import { ApiError } from '../app/api/api-error';
import { OrderDetail, OrderSummary, StreamEvent, UsageSummary, UsageToday } from '../app/api/api.models';

export const USAGE_DONE: UsageSummary = {
  requestId: 'req-1',
  inputTokens: 12,
  outputTokens: 30,
  estimated: true,
  costStatus: 'LOCAL_MODEL',
};

/** Builds a fake StreamService whose answer is a fixed script: events to emit, or an error to throw. */
export function fakeStream(script: Array<StreamEvent | Error>) {
  return {
    async *ask(_question: string, _orderRef: string | null, signal?: AbortSignal): AsyncGenerator<StreamEvent> {
      for (const step of script) {
        if (signal?.aborted) {
          throw new DOMException('aborted', 'AbortError');
        }
        if (step instanceof Error) {
          throw step;
        }
        yield step;
      }
    },
  };
}

/** A stream that waits until released (to show the "working" state) or until cancelled. */
export function gatedStream() {
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => (release = resolve));
  return {
    release: () => release(),
    service: {
      async *ask(_q: string, _o: string | null, signal?: AbortSignal): AsyncGenerator<StreamEvent> {
        await new Promise<void>((resolve, reject) => {
          signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          gate.then(resolve);
        });
        yield { type: 'token', text: 'Done.' };
        yield { type: 'done', usage: USAGE_DONE };
      },
    },
  };
}

export const ORDERS: OrderSummary[] = [
  { orderRef: 'ORD-1002', status: 'SHIPPED', promisedDate: '2026-11-08' },
  { orderRef: 'ORD-1001', status: 'DELAYED', promisedDate: '2026-11-10' },
];

export const ORDER_DETAIL: OrderDetail = {
  orderRef: 'ORD-1001',
  status: 'DELAYED',
  promisedDate: '2026-11-10',
  events: [
    { eventType: 'PLACED', occurredAt: '2026-11-02T10:00:00Z' },
    { eventType: 'DELAYED', occurredAt: '2026-11-07T14:15:00Z' },
  ],
};

export const USAGE_TODAY: UsageToday = {
  day: '2026-11-02',
  tokensUsed: 600,
  dailyLimit: 1000,
  remainingTokens: 400,
  note: 'Token counts are estimates unless the provider reported them. The day resets at 00:00 UTC.',
};

export function apiError(status: number, code: string | null, requestId: string | null = 'req-9'): ApiError {
  return new ApiError(status, code, requestId);
}
