import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { ApiError } from './api-error';
import { StreamEvent } from './api.models';
import { StreamService, toStreamEvent } from './stream.service';

function streamResponse(chunks: Array<string | Uint8Array>, status = 200): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk);
      }
      controller.close();
    },
  });
  return new Response(body, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

async function collect(generator: AsyncGenerator<StreamEvent>): Promise<StreamEvent[]> {
  const events: StreamEvent[] = [];
  for await (const event of generator) {
    events.push(event);
  }
  return events;
}

describe('StreamService', () => {
  let service: StreamService;
  let token: string | null;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    token = 'access-token-1';
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { accessToken: () => token } }],
    });
    service = TestBed.inject(StreamService);
    fetchSpy = spyOn(window, 'fetch');
  });

  it('posts the question in the body with the bearer token, never in the URL', async () => {
    fetchSpy.and.resolveTo(streamResponse(['event:done\ndata:{"requestId":"r","inputTokens":1,"outputTokens":2,"estimated":true,"costStatus":"LOCAL_MODEL"}\n\n']));

    await collect(service.ask('Where is my order?', 'ORD-1001'));

    const [url, init] = fetchSpy.calls.mostRecent().args as [string, RequestInit];
    expect(url).toBe('/api/v1/support/ask/stream');
    expect(url).not.toContain('Where');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer access-token-1');
    expect(JSON.parse(init.body as string)).toEqual({ question: 'Where is my order?', orderRef: 'ORD-1001' });
  });

  it('sends no Authorization header when there is no token, and null when no order is chosen', async () => {
    token = null;
    fetchSpy.and.resolveTo(streamResponse([]));

    await collect(service.ask('hi', ''));

    const init = fetchSpy.calls.mostRecent().args[1] as RequestInit;
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined();
    expect(JSON.parse(init.body as string).orderRef).toBeNull();
  });

  it('yields token events then the done event, even when events are split across reads', async () => {
    fetchSpy.and.resolveTo(streamResponse([
      'event:token\nda', 'ta:"Hel"\n\nevent:token\ndata:" lo"\n',
      '\nevent:done\ndata:{"requestId":"r1","inputTokens":3,"outputTokens":4,"estimated":true,"costStatus":"LOCAL_MODEL"}\n\n',
    ]));

    const events = await collect(service.ask('hi', null));

    expect(events.map((e) => e.type)).toEqual(['token', 'token', 'done']);
    expect(events.filter((e) => e.type === 'token').map((e) => (e as { text: string }).text).join('')).toBe('Hel lo');
    expect((events[2] as { usage: { requestId: string } }).usage.requestId).toBe('r1');
  });

  it('keeps a multi-byte character intact when its bytes arrive in different reads', async () => {
    const bytes = new TextEncoder().encode('event:token\ndata:"café"\n\n');
    const split = bytes.length - 4; // inside the two bytes of the accented letter
    fetchSpy.and.resolveTo(streamResponse([bytes.slice(0, split), bytes.slice(split)]));

    const events = await collect(service.ask('hi', null));

    expect(events).toEqual([{ type: 'token', text: 'café' }]);
  });

  it('throws an ApiError built from the problem document when the server rejects the request', async () => {
    fetchSpy.and.resolveTo(new Response(
      JSON.stringify({ code: 'SENSITIVE_DATA', requestId: 'req-7', categories: ['ONE_TIME_CODE'], detail: 'x' }),
      { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
    ));

    const error = await collect(service.ask('my otp is 123456', null)).catch((e) => e);

    expect(error instanceof ApiError).toBeTrue();
    expect(error.kind).toBe('sensitive-data');
    expect(error.requestId).toBe('req-7');
  });

  it('still throws a usable error when the error body is not JSON', async () => {
    fetchSpy.and.resolveTo(new Response('<html>Bad Gateway</html>', { status: 502 }));

    const error = await collect(service.ask('hi', null)).catch((e) => e);

    expect(error instanceof ApiError).toBeTrue();
    expect(error.kind).toBe('model-error');
  });

  it('stops reading when the caller aborts', async () => {
    const controller = new AbortController();
    fetchSpy.and.callFake((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));

    const pending = collect(service.ask('hi', null, controller.signal)).catch((e) => e);
    controller.abort();

    expect((await pending).name).toBe('AbortError');
  });
});

describe('toStreamEvent', () => {
  it('turns a token message into text and a done message into usage', () => {
    expect(toStreamEvent({ event: 'token', data: '"hi"' })).toEqual({ type: 'token', text: 'hi' });
    expect(toStreamEvent({ event: 'done', data: '{"requestId":"r"}' })?.type).toBe('done');
  });

  it('skips unknown events, unreadable data and non-text tokens instead of failing', () => {
    expect(toStreamEvent({ event: 'other', data: '"x"' })).toBeNull();
    expect(toStreamEvent({ event: 'token', data: 'not json' })).toBeNull();
    expect(toStreamEvent({ event: 'token', data: '42' })).toBeNull();
    expect(toStreamEvent({ event: 'done', data: '{broken' })).toBeNull();
  });

  it('keeps spaces and line breaks exactly', () => {
    expect(toStreamEvent({ event: 'token', data: '" world"' })).toEqual({ type: 'token', text: ' world' });
    expect(toStreamEvent({ event: 'token', data: '"a\\nb"' })).toEqual({ type: 'token', text: 'a\nb' });
  });
});
