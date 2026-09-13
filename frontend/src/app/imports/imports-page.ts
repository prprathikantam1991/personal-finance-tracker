import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ImportsApiService, ImportHistoryItem, ImportResult, IncomingStatementItem } from '../core/imports-api.service';
import { Router } from '@angular/router';

@Component({ selector: 'app-imports-page', imports: [CurrencyPipe, DatePipe, DecimalPipe], templateUrl: './imports-page.html', styleUrl: './imports-page.scss' })
export class ImportsPage implements OnInit {
  private readonly importsApi = inject(ImportsApiService);
  private readonly router = inject(Router);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly importing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly history = signal<ImportHistoryItem[]>([]);
  protected readonly historyError = signal<string | null>(null);
  protected readonly incoming = signal<IncomingStatementItem[]>([]);

  ngOnInit(): void { this.loadHistory(); this.loadIncoming(); }

  protected selectFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.selectedFile.set(file);
    this.error.set(null);
    this.result.set(null);
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (!file) {
      this.error.set('Choose a CSV or PDF statement before uploading.');
      return;
    }
    this.importing.set(true);
    this.error.set(null);
    this.importsApi.upload(file).subscribe({
      next: (result) => { this.result.set(result); this.importing.set(false); this.loadHistory(); this.loadIncoming(); this.router.navigate(['/imports', result.importId, 'review']); },
      error: (response) => { this.error.set(response.error?.message ?? 'Upload failed. Make sure the backend is running and try again.'); this.importing.set(false); },
    });
  }

  protected reset(): void { this.selectedFile.set(null); this.result.set(null); this.error.set(null); }

  protected reviewImport(importId: string): void { this.router.navigate(['/imports', importId, 'review']); }

  private loadHistory(): void {
    this.importsApi.history().subscribe({
      next: history => this.history.set(history),
      error: () => this.historyError.set('Import history is temporarily unavailable.'),
    });
  }

  private loadIncoming(): void {
    this.importsApi.incoming().subscribe({ next: incoming => this.incoming.set(incoming) });
  }
}
