import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiError, userMessage } from '../../api/api-error';
import { UsageToday } from '../../api/api.models';
import { ApiService } from '../../api/api.service';

@Component({
  selector: 'app-usage',
  templateUrl: './usage.html',
  styleUrl: './usage.css',
})
export class Usage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly loading = signal(true);
  protected readonly usage = signal<UsageToday | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly errorText = computed(() => {
    const error = this.error();
    return error ? userMessage(error) : '';
  });

  /** How much of the day's allowance is used, 0 to 100, for the bar. */
  protected readonly percent = computed(() => {
    const usage = this.usage();
    if (!usage || usage.dailyLimit <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((usage.tokensUsed / usage.dailyLimit) * 100));
  });

  /** Switches the bar to a warning colour when most of the allowance has gone. */
  protected readonly nearLimit = computed(() => this.percent() >= 80);

  ngOnInit(): Promise<void> {
    return this.refresh();
  }

  protected async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.usage.set(await this.api.usageToday());
    } catch (caught) {
      this.error.set(caught instanceof ApiError ? caught : new ApiError(0, null, null));
    } finally {
      this.loading.set(false);
    }
  }
}
