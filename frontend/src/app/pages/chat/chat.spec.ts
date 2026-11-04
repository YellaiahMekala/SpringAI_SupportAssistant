import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiService } from '../../api/api.service';
import { StreamService } from '../../api/stream.service';
import { AuthService } from '../../auth/auth.service';
import { expectNoA11yViolations } from '../../../testing/a11y';
import { ORDERS, USAGE_DONE, apiError, fakeStream, gatedStream } from '../../../testing/fakes';
import { Chat } from './chat';

describe('Chat screen', () => {
  let signIn: jasmine.Spy;

  async function setup(stream: unknown): Promise<{ fixture: ComponentFixture<Chat>; el: HTMLElement }> {
    signIn = jasmine.createSpy('signIn');
    await TestBed.configureTestingModule({
      imports: [Chat],
      providers: [
        { provide: ApiService, useValue: { orders: () => Promise.resolve(ORDERS) } },
        { provide: StreamService, useValue: stream },
        { provide: AuthService, useValue: { signIn } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Chat);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(fixture: ComponentFixture<Chat>, el: HTMLElement, text: string): void {
    const area = el.querySelector('#question') as HTMLTextAreaElement;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function submit(fixture: ComponentFixture<Chat>, el: HTMLElement): Promise<void> {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const sendButton = (el: HTMLElement) => el.querySelector('button[type="submit"]') as HTMLButtonElement;
  const text = (el: HTMLElement) => el.textContent ?? '';

  it('keeps Send disabled until there is a question, and shows the character count', async () => {
    const { fixture, el } = await setup(fakeStream([]));
    expect(sendButton(el).disabled).toBeTrue();

    type(fixture, el, '   ');
    expect(sendButton(el).disabled).toBeTrue();

    type(fixture, el, 'Where is my order?');
    expect(sendButton(el).disabled).toBeFalse();
    expect(text(el)).toContain('18 / 4000');
  });

  it('offers the order references the caller can see as suggestions', async () => {
    const { el } = await setup(fakeStream([]));

    const options = Array.from(el.querySelectorAll('datalist option')).map((o) => o.getAttribute('value'));
    expect(options).toEqual(['ORD-1002', 'ORD-1001']);
  });

  it('streams the answer piece by piece and reports usage as an estimate', async () => {
    const { fixture, el } = await setup(fakeStream([
      { type: 'token', text: 'Your order ' },
      { type: 'token', text: 'is delayed.' },
      { type: 'done', usage: USAGE_DONE },
    ]));
    type(fixture, el, 'Why is it late?');

    await submit(fixture, el);

    expect(el.querySelector('.answer')?.textContent).toBe('Your order is delayed.');
    expect(text(el)).toContain('42 tokens used');
    expect(text(el)).toContain('estimated');
    expect(el.querySelector('.alert')).toBeNull();
  });

  it('shows a working state with a Cancel button, and blocks a second send, while waiting', async () => {
    const gated = gatedStream();
    const { fixture, el } = await setup(gated.service);
    type(fixture, el, 'hello');

    await submit(fixture, el);

    expect(text(el)).toContain('The assistant is answering');
    expect(el.querySelector('.result')?.getAttribute('aria-busy')).toBe('true');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.trim() === 'Cancel')).toBeTrue();
    expect(sendButton(el).disabled).toBeTrue();

    gated.release();
    // The stream resumes through several chained promises; yield to the event loop so they all finish.
    await new Promise<void>((resolve) => setTimeout(resolve, 20));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('.answer')?.textContent).toBe('Done.');
    expect(text(el)).not.toContain('The assistant is answering');
  });

  it('can be cancelled while waiting', async () => {
    const gated = gatedStream();
    const { fixture, el } = await setup(gated.service);
    type(fixture, el, 'hello');
    await submit(fixture, el);

    const cancel = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Cancel')!;
    cancel.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(el)).toContain('Cancelled');
    expect(el.querySelector('.answer')).toBeNull();
    expect(sendButton(el).disabled).toBeFalse();
  });

  // Each failure the backend can report gets its own wording; none shows server detail.
  const failures: Array<[string, number, string | null, string]> = [
    ['sensitive data', 422, 'SENSITIVE_DATA', 'was not sent'],
    ['a message that is too long', 422, 'INPUT_TOO_LONG', 'too long'],
    ['a used-up daily budget', 429, 'DAILY_BUDGET_EXHAUSTED', 'daily usage limit'],
    ['the model being unavailable', 503, 'MODEL_UNAVAILABLE', 'not available right now'],
    ['a model timeout', 504, 'MODEL_TIMEOUT', 'took too long'],
    ['a model error', 502, 'MODEL_ERROR', 'could not process'],
    ['an order that is not found', 404, null, 'order was not found'],
    ['invalid input', 400, null, 'check what you entered'],
    ['an unexpected failure', 500, 'INTERNAL_ERROR', 'Something went wrong'],
  ];

  failures.forEach(([name, status, code, expected]) => {
    it(`explains ${name} in plain words, shows the reference, and never echoes what was typed`, async () => {
      const { fixture, el } = await setup(fakeStream([apiError(status, code, 'req-42')]));
      type(fixture, el, 'my otp is 482913');

      await submit(fixture, el);

      const alert = el.querySelector('.alert-danger') as HTMLElement;
      expect(alert.getAttribute('role')).toBe('alert');
      expect(alert.textContent).toContain(expected);
      expect(alert.textContent).toContain('Reference: req-42');
      expect(alert.textContent).not.toContain('482913');
      expect(el.querySelector('.answer')).toBeNull();
    });
  });

  it('offers to sign in again when the session has ended', async () => {
    const { fixture, el } = await setup(fakeStream([apiError(401, 'UNAUTHENTICATED')]));
    type(fixture, el, 'hello');
    await submit(fixture, el);

    expect(text(el)).toContain('Your session has ended');
    const button = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Sign in again')!;
    button.click();

    expect(signIn).toHaveBeenCalledTimes(1);
  });

  it('warns that the answer may be incomplete when the stream ends without a final event', async () => {
    const { fixture, el } = await setup(fakeStream([{ type: 'token', text: 'Part of an ans' }]));
    type(fixture, el, 'hello');

    await submit(fixture, el);

    expect(text(el)).toContain('cut off');
    expect(el.querySelector('.answer')?.textContent).toBe('Part of an ans');
  });

  it('shows a distinct, calm message when the assistant abstains', async () => {
    const { fixture, el } = await setup(fakeStream([
      { type: 'done', usage: { ...USAGE_DONE, status: 'ABSTAIN' } },
    ]));
    type(fixture, el, 'What is the meaning of life?');

    await submit(fixture, el);

    expect(text(el)).toContain('not enough reliable information');
    expect(el.querySelector('.alert-danger')).toBeNull();
  });

  it('asks for more detail when the assistant needs clarification', async () => {
    const { fixture, el } = await setup(fakeStream([
      { type: 'done', usage: { ...USAGE_DONE, status: 'CLARIFY' } },
    ]));
    type(fixture, el, 'it is broken');

    await submit(fixture, el);

    expect(text(el)).toContain('More detail is needed');
  });

  describe('grounded answers', () => {
    const citation = {
      id: 'S1', documentId: 1, title: 'Returns policy', version: 2, section: 'Refunds', page: null,
      chunkIndex: 0, score: 0.9, excerpt: 'Refunds take 5 days.',
    };

    it('replaces the streamed text with the checked answer and lists the sources', async () => {
      const { fixture, el } = await setup(fakeStream([
        { type: 'token', text: 'Refunds take 5 days [S9].' },
        { type: 'done', usage: { ...USAGE_DONE, status: 'ANSWER', answer: 'Refunds take 5 days [S1].', citations: [citation] } },
      ]));
      type(fixture, el, 'How long do refunds take?');
      await submit(fixture, el);

      expect(el.querySelector('.answer')?.textContent).toBe('Refunds take 5 days [S1].');
      const sources = el.querySelectorAll('.sources li');
      expect(sources.length).toBe(1);
      expect(sources[0].textContent).toContain('Returns policy');
      expect(sources[0].textContent).toContain('Refunds take 5 days.');
    });

    it('shows no sources when the server sends none', async () => {
      const { fixture, el } = await setup(fakeStream([
        { type: 'token', text: 'Hi' },
        { type: 'done', usage: USAGE_DONE },
      ]));
      type(fixture, el, 'Hello');
      await submit(fixture, el);

      expect(el.querySelector('.sources')).toBeNull();
      expect(el.querySelector('.answer')?.textContent).toBe('Hi');
    });

    it('has no accessibility violations with sources shown', async () => {
      const { fixture, el } = await setup(fakeStream([
        { type: 'done', usage: { ...USAGE_DONE, answer: 'Refunds take 5 days [S1].', citations: [citation] } },
      ]));
      type(fixture, el, 'Refunds?');
      await submit(fixture, el);
      await expectNoA11yViolations(el);
    });
  });

  describe('accessibility (automated axe check, WCAG 2 A and AA)', () => {
    it('has no violations when idle', async () => {
      const { el } = await setup(fakeStream([]));
      await expectNoA11yViolations(el);
    });

    it('has no violations while working', async () => {
      const { fixture, el } = await setup(gatedStream().service);
      type(fixture, el, 'hello');
      await submit(fixture, el);
      await expectNoA11yViolations(el);
    });

    it('has no violations after an answer', async () => {
      const { fixture, el } = await setup(fakeStream([
        { type: 'token', text: 'Hello.' }, { type: 'done', usage: USAGE_DONE },
      ]));
      type(fixture, el, 'hello');
      await submit(fixture, el);
      await expectNoA11yViolations(el);
    });

    it('has no violations after an error', async () => {
      const { fixture, el } = await setup(fakeStream([apiError(422, 'SENSITIVE_DATA')]));
      type(fixture, el, 'hello');
      await submit(fixture, el);
      await expectNoA11yViolations(el);
    });
  });
});
