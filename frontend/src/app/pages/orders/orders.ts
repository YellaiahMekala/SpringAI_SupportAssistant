import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiError, userMessage } from '../../api/api-error';
import { OrderDetail, OrderSummary } from '../../api/api.models';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-orders',
  imports: [DatePipe],
  templateUrl: './orders.html',
  styleUrl: './orders.css',
})
export class Orders implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly listError = signal<ApiError | null>(null);

  protected readonly selected = signal<OrderDetail | null>(null);
  protected readonly detailError = signal<ApiError | null>(null);
  protected readonly lookup = signal('');

  protected readonly listErrorText = computed(() => {
    const error = this.listError();
    return error ? userMessage(error) : '';
  });
  protected readonly detailErrorText = computed(() => {
    const error = this.detailError();
    return error ? userMessage(error) : '';
  });

  async ngOnInit(): Promise<void> {
    try {
      this.orders.set(await this.api.orders());
    } catch (caught) {
      this.listError.set(caught instanceof ApiError ? caught : new ApiError(0, null, null));
    } finally {
      this.loading.set(false);
    }
  }

  protected onLookup(event: Event): void {
    this.lookup.set((event.target as HTMLInputElement).value.trim());
  }

  protected async open(orderRef: string): Promise<void> {
    this.detailError.set(null);
    try {
      this.selected.set(await this.api.order(orderRef));
    } catch (caught) {
      this.selected.set(null);
      this.detailError.set(caught instanceof ApiError ? caught : new ApiError(0, null, null));
    }
  }

  /** Support agents can look up any order by its reference. */
  protected async find(event: Event): Promise<void> {
    event.preventDefault();
    if (this.lookup()) {
      await this.open(this.lookup());
    }
  }
}
