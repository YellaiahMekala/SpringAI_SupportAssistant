import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiError, userMessage } from '../../api/api-error';
import { DocumentInfo, IngestionResult, Visibility } from '../../api/api.models';
import { ApiService } from '../../api/api.service';

export const MAX_FILE_BYTES = 1_048_576;
const ALLOWED_EXTENSIONS = ['.md', '.txt'];

@Component({
  selector: 'app-documents',
  templateUrl: './documents.html',
  styleUrl: './documents.css',
})
export class Documents implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly loading = signal(true);
  protected readonly documents = signal<DocumentInfo[]>([]);
  protected readonly listError = signal<ApiError | null>(null);

  protected readonly file = signal<File | null>(null);
  protected readonly title = signal('');
  protected readonly visibility = signal<Visibility>('PUBLIC');
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<ApiError | null>(null);
  protected readonly localError = signal('');
  protected readonly result = signal<IngestionResult | null>(null);

  protected readonly listErrorText = computed(() => {
    const error = this.listError();
    return error ? userMessage(error, 'document') : '';
  });
  protected readonly uploadErrorText = computed(() => {
    const error = this.uploadError();
    return error ? userMessage(error, 'document') : '';
  });
  protected readonly canUpload = computed(() => this.file() !== null && !this.uploading());

  ngOnInit(): Promise<void> {
    return this.refresh();
  }

  protected async refresh(): Promise<void> {
    this.loading.set(true);
    this.listError.set(null);
    try {
      this.documents.set(await this.api.documents());
    } catch (caught) {
      this.listError.set(caught instanceof ApiError ? caught : new ApiError(0, null, null));
    } finally {
      this.loading.set(false);
    }
  }

  protected onFile(event: Event): void {
    const chosen = (event.target as HTMLInputElement).files?.item(0) ?? null;
    this.result.set(null);
    this.uploadError.set(null);
    this.localError.set('');
    if (chosen && !this.acceptable(chosen)) {
      this.file.set(null);
      this.localError.set('Choose a plain-text .md or .txt file of up to 1 MiB.');
      return;
    }
    this.file.set(chosen);
  }

  protected onTitle(event: Event): void {
    this.title.set((event.target as HTMLInputElement).value);
  }

  protected onVisibility(event: Event): void {
    this.visibility.set((event.target as HTMLSelectElement).value as Visibility);
  }

  protected async upload(event: Event): Promise<void> {
    event.preventDefault();
    const chosen = this.file();
    if (!chosen || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.uploadError.set(null);
    this.result.set(null);
    try {
      this.result.set(await this.api.upload(chosen, this.title(), this.visibility()));
      this.file.set(null);
      this.title.set('');
      await this.refresh();
    } catch (caught) {
      this.uploadError.set(caught instanceof ApiError ? caught : new ApiError(0, null, null));
    } finally {
      this.uploading.set(false);
    }
  }

  /** A short summary of what the upload did, in words. */
  protected resultText(result: IngestionResult): string {
    switch (result.status) {
      case 'CREATED':
        return `Added "${result.title}" (version ${result.version}, ${result.chunks} passages).`;
      case 'DUPLICATE':
        return `"${result.title}" was already stored with this exact content, so nothing changed.`;
      default:
        return `"${result.title}" was already stored; its passages were embedded again for the current model.`;
    }
  }

  protected needsReindex(document: DocumentInfo): boolean {
    return document.embeddedForProvider < document.chunks;
  }

  private acceptable(file: File): boolean {
    const name = file.name.toLowerCase();
    return ALLOWED_EXTENSIONS.some((extension) => name.endsWith(extension)) && file.size > 0 && file.size <= MAX_FILE_BYTES;
  }
}
