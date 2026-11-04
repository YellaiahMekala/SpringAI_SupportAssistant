import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiError, userMessage } from '../../api/api-error';
import { Citation, OrderSummary, UsageSummary } from '../../api/api.models';
import { ApiService } from '../../api/api.service';
import { StreamService } from '../../api/stream.service';
import { AuthService } from '../../auth/auth.service';

export type ChatState = 'idle' | 'working' | 'done' | 'failed' | 'cancelled' | 'interrupted';

export const MAX_QUESTION_LENGTH = 4000;

@Component({
  selector: 'app-chat',
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat implements OnInit {
  private readonly api = inject(ApiService);
  private readonly stream = inject(StreamService);
  protected readonly auth = inject(AuthService);

  protected readonly maxLength = MAX_QUESTION_LENGTH;

  protected readonly question = signal('');
  protected readonly orderRef = signal('');
  protected readonly orders = signal<OrderSummary[]>([]);

  protected readonly state = signal<ChatState>('idle');
  protected readonly answer = signal('');
  protected readonly usage = signal<UsageSummary | null>(null);
  protected readonly citations = signal<Citation[]>([]);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly canSend = computed(
    () => this.question().trim().length > 0 && this.question().length <= MAX_QUESTION_LENGTH && this.state() !== 'working',
  );
  protected readonly errorText = computed(() => {
    const error = this.error();
    return error ? userMessage(error) : '';
  });
  protected readonly sessionEnded = computed(() => this.error()?.kind === 'session-expired');

  private controller: AbortController | null = null;

  async ngOnInit(): Promise<void> {
    try {
      this.orders.set(await this.api.orders());
    } catch {
      // The order list only fills the suggestions; the screen works without it.
    }
  }

  protected onQuestion(event: Event): void {
    this.question.set((event.target as HTMLTextAreaElement).value);
  }

  protected onOrderRef(event: Event): void {
    this.orderRef.set((event.target as HTMLInputElement).value.trim());
  }

  protected async send(event?: Event): Promise<void> {
    event?.preventDefault();
    if (!this.canSend()) {
      return;
    }

    this.state.set('working');
    this.answer.set('');
    this.usage.set(null);
    this.citations.set([]);
    this.error.set(null);
    this.controller = new AbortController();

    let finished = false;
    try {
      for await (const item of this.stream.ask(this.question(), this.orderRef() || null, this.controller.signal)) {
        if (item.type === 'token') {
          this.answer.update((text) => text + item.text);
        } else {
          this.usage.set(item.usage);
          if (typeof item.usage.answer === 'string') {
            this.answer.set(item.usage.answer);
          }
          this.citations.set(item.usage.citations ?? []);
          finished = true;
        }
      }
      this.state.set(finished ? 'done' : 'interrupted');
    } catch (caught) {
      if (this.controller.signal.aborted) {
        this.state.set('cancelled');
      } else if (caught instanceof ApiError) {
        this.error.set(caught);
        this.state.set('failed');
      } else {
        // The connection dropped or the browser could not reach the server.
        this.state.set(this.answer() ? 'interrupted' : 'failed');
        if (!this.answer()) {
          this.error.set(new ApiError(0, null, null));
        }
      }
    } finally {
      this.controller = null;
    }
  }

  protected cancel(): void {
    this.controller?.abort();
  }

  protected signInAgain(): void {
    this.auth.signIn();
  }
}
