import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiError } from './api-error';
import { DocumentInfo, IngestionResult, Me, OrderDetail, OrderSummary, UsageToday, Visibility } from './api.models';

/** JSON API client; failures surface as {@link ApiError}. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  me(): Promise<Me> {
    return this.get<Me>('/api/v1/me');
  }

  orders(): Promise<OrderSummary[]> {
    return this.get<OrderSummary[]>('/api/v1/orders');
  }

  order(orderRef: string): Promise<OrderDetail> {
    return this.get<OrderDetail>(`/api/v1/orders/${encodeURIComponent(orderRef)}`);
  }

  usageToday(): Promise<UsageToday> {
    return this.get<UsageToday>('/api/v1/usage/today');
  }

  /** Support agents only: the stored documents. */
  documents(): Promise<DocumentInfo[]> {
    return this.get<DocumentInfo[]>('/api/v1/documents');
  }

  /** Support agents only: uploads a .md or .txt file. The file travels as multipart form data. */
  upload(file: File, title: string, visibility: Visibility): Promise<IngestionResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    if (title.trim()) {
      form.append('title', title.trim());
    }
    form.append('visibility', visibility);
    return this.send(() => this.http.post<IngestionResult>('/api/v1/documents', form));
  }

  private get<T>(url: string): Promise<T> {
    return this.send(() => this.http.get<T>(url));
  }

  private async send<T>(call: () => Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(call());
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw ApiError.fromHttp(error);
      }
      throw error;
    }
  }
}
