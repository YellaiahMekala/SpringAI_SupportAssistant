import { HttpErrorResponse } from '@angular/common/http';

export type ErrorKind =
  | 'sensitive-data'
  | 'too-long'
  | 'budget-used-up'
  | 'model-unavailable'
  | 'model-timeout'
  | 'model-error'
  | 'not-found'
  | 'session-expired'
  | 'forbidden'
  | 'invalid-input'
  | 'invalid-document'
  | 'payload-too-large'
  | 'unknown';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    readonly requestId: string | null,
    readonly categories: string[] = [],
  ) {
    super(`API error ${status}${code ? ' ' + code : ''}`);
  }

  get kind(): ErrorKind {
    switch (this.code) {
      case 'SENSITIVE_DATA':
        return 'sensitive-data';
      case 'INPUT_TOO_LONG':
        return 'too-long';
      case 'DAILY_BUDGET_EXHAUSTED':
        return 'budget-used-up';
      case 'MODEL_UNAVAILABLE':
        return 'model-unavailable';
      case 'MODEL_TIMEOUT':
        return 'model-timeout';
      case 'INVALID_DOCUMENT':
        return 'invalid-document';
      case 'MODEL_ERROR':
        return 'model-error';
      case 'UNAUTHENTICATED':
        return 'session-expired';
      case 'FORBIDDEN':
        return 'forbidden';
    }
    switch (this.status) {
      case 413:
        return 'payload-too-large';
      case 400:
        return 'invalid-input';
      case 401:
        return 'session-expired';
      case 403:
        return 'forbidden';
      case 404:
        return 'not-found';
      case 429:
        return 'budget-used-up';
      case 503:
        return 'model-unavailable';
      case 504:
        return 'model-timeout';
      case 502:
        return 'model-error';
      default:
        return 'unknown';
    }
  }

  /** Builds an ApiError from the problem+json body the backend sends (status, code, requestId, categories). */
  static fromProblem(status: number, body: unknown): ApiError {
    const problem = (typeof body === 'object' && body !== null ? body : {}) as Record<string, unknown>;
    const code = typeof problem['code'] === 'string' ? problem['code'] : null;
    const requestId = typeof problem['requestId'] === 'string' ? problem['requestId'] : null;
    const categories = Array.isArray(problem['categories'])
      ? (problem['categories'] as unknown[]).filter((c): c is string => typeof c === 'string')
      : [];
    return new ApiError(status, code, requestId, categories);
  }

  static fromHttp(error: HttpErrorResponse): ApiError {
    return ApiError.fromProblem(error.status, error.error);
  }
}

export type ErrorContext = 'chat' | 'document';

export function userMessage(error: ApiError, context: ErrorContext = 'chat'): string {
  switch (error.kind) {
    case 'sensitive-data':
      return context === 'document'
        ? 'This document looks like it contains a password, one-time code, card security code or access key, ' +
            'so it was not stored. Remove that text and upload it again.'
        : 'Your message looks like it contains a password, one-time code, card security code or access key. ' +
            'For your safety it was not sent. Please remove it and try again.';
    case 'too-long':
      return 'Your message is too long to process. Please shorten it and try again.';
    case 'budget-used-up':
      return 'The daily usage limit for your account has been reached. It resets at 00:00 UTC.';
    case 'model-unavailable':
      return 'The assistant is not available right now. Please try again in a few minutes.';
    case 'model-timeout':
      return 'The assistant took too long to answer. Please try again.';
    case 'model-error':
      return 'The assistant could not process your request. Please try again.';
    case 'invalid-document':
      return 'That file was not accepted. Use a plain-text .md or .txt file of up to 1 MiB.';
    case 'payload-too-large':
      return 'That file is too large. The limit is 1 MiB.';
    case 'not-found':
      return 'That order was not found. Check the order reference and try again.';
    case 'session-expired':
      return 'Your session has ended. Please sign in again.';
    case 'forbidden':
      return 'Your account does not have access to this.';
    case 'invalid-input':
      return 'Please check what you entered and try again.';
    default:
      return 'Something went wrong. Please try again.';
  }
}
