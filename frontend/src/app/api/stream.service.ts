import { Injectable, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { ApiError } from './api-error';
import { StreamEvent, UsageSummary } from './api.models';
import { SseMessage, SseParser } from './sse-parser';

/** POST + SSE stream for support answers (Authorization header + body). */
@Injectable({ providedIn: 'root' })
export class StreamService {
  private readonly auth = inject(AuthService);

  async *ask(question: string, orderRef: string | null, signal?: AbortSignal): AsyncGenerator<StreamEvent> {
    const token = this.auth.accessToken();
    const response = await fetch('/api/v1/support/ask/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question, orderRef: orderRef || null }),
      signal,
    });

    if (!response.ok) {
      throw ApiError.fromProblem(response.status, await readJsonQuietly(response));
    }
    if (!response.body) {
      throw new ApiError(response.status, null, null);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const parser = new SseParser();
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        for (const message of parser.push(decoder.decode(value, { stream: true }))) {
          const event = toStreamEvent(message);
          if (event) {
            yield event;
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
}

async function readJsonQuietly(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export function toStreamEvent(message: SseMessage): StreamEvent | null {
  if (message.event === 'token') {
    try {
      const text = JSON.parse(message.data);
      return typeof text === 'string' ? { type: 'token', text } : null;
    } catch {
      return null;
    }
  }
  if (message.event === 'done') {
    try {
      return { type: 'done', usage: JSON.parse(message.data) as UsageSummary };
    } catch {
      return null;
    }
  }
  return null;
}
