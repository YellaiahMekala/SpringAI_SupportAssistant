import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, ErrorKind, userMessage } from './api-error';

describe('ApiError', () => {
  const kinds: Array<[number, string | null, ErrorKind]> = [
    [422, 'SENSITIVE_DATA', 'sensitive-data'],
    [422, 'INPUT_TOO_LONG', 'too-long'],
    [429, 'DAILY_BUDGET_EXHAUSTED', 'budget-used-up'],
    [503, 'MODEL_UNAVAILABLE', 'model-unavailable'],
    [504, 'MODEL_TIMEOUT', 'model-timeout'],
    [502, 'MODEL_ERROR', 'model-error'],
    [401, 'UNAUTHENTICATED', 'session-expired'],
    [403, 'FORBIDDEN', 'forbidden'],
    [404, null, 'not-found'],
    [400, null, 'invalid-input'],
    [500, 'INTERNAL_ERROR', 'unknown'],
    [0, null, 'unknown'],
  ];

  kinds.forEach(([status, code, kind]) => {
    it(`classifies ${status} ${code ?? '(no code)'} as ${kind}`, () => {
      expect(new ApiError(status, code, null).kind).toBe(kind);
    });
  });

  it('falls back to the status when the code is missing or unknown', () => {
    expect(new ApiError(429, 'SOMETHING_NEW', null).kind).toBe('budget-used-up');
    expect(new ApiError(401, null, null).kind).toBe('session-expired');
  });

  it('reads status, code, request id and categories from a problem document', () => {
    const error = ApiError.fromProblem(422, {
      code: 'SENSITIVE_DATA',
      requestId: 'abc-123',
      categories: ['ONE_TIME_CODE', 7],
      detail: 'server detail that must not be kept',
    });

    expect(error.status).toBe(422);
    expect(error.code).toBe('SENSITIVE_DATA');
    expect(error.requestId).toBe('abc-123');
    expect(error.categories).toEqual(['ONE_TIME_CODE']);
  });

  it('survives a body that is not a problem document', () => {
    for (const body of [null, undefined, 'text', 42, [], { code: 5 }]) {
      const error = ApiError.fromProblem(500, body);
      expect(error.code).toBeNull();
      expect(error.requestId).toBeNull();
    }
  });

  it('builds from an Angular HttpErrorResponse', () => {
    const error = ApiError.fromHttp(new HttpErrorResponse({ status: 404, error: { requestId: 'r1' } }));

    expect(error.kind).toBe('not-found');
    expect(error.requestId).toBe('r1');
  });
});

describe('userMessage', () => {
  it('gives every kind a non-empty message that tells the person what to do', () => {
    const codes = ['SENSITIVE_DATA', 'INPUT_TOO_LONG', 'DAILY_BUDGET_EXHAUSTED', 'MODEL_UNAVAILABLE', 'MODEL_TIMEOUT',
      'MODEL_ERROR', 'UNAUTHENTICATED', 'FORBIDDEN', null];
    for (const code of codes) {
      expect(userMessage(new ApiError(500, code, null)).length).toBeGreaterThan(20);
    }
  });

  it('never repeats server detail or a request id inside the message', () => {
    const error = new ApiError(502, 'MODEL_ERROR', 'secret-request-id');

    expect(userMessage(error)).not.toContain('secret-request-id');
  });

  it('says the message was not sent when sensitive data was found', () => {
    expect(userMessage(new ApiError(422, 'SENSITIVE_DATA', null))).toContain('was not sent');
  });
});

describe('document error wording', () => {
  it('words an invalid document and an oversize upload for the document screen', () => {
    const invalid = new ApiError(400, 'INVALID_DOCUMENT', null);
    expect(invalid.kind).toBe('invalid-document');
    expect(userMessage(invalid, 'document')).toContain('.md or .txt');

    const large = new ApiError(413, null, null);
    expect(large.kind).toBe('payload-too-large');
    expect(userMessage(large, 'document')).toContain('1 MiB');
  });
});
