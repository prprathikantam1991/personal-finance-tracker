import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ImportsApiService, ImportHistoryItem, ImportResult, IncomingStatementItem, StatementCoverage } from '../core/imports-api.service';
import { Router } from '@angular/router';

@Component({ selector: 'app-imports-page', imports: [DatePipe, DecimalPipe], templateUrl: './imports-page.html', styleUrl: './imports-page.scss' })
export class ImportsPage implements OnInit {
  private readonly importsApi = inject(ImportsApiService);
  private readonly router = inject(Router);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly importing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly history = signal<ImportHistoryItem[]>([]);
  protected readonly historyStatus = signal<'ALL' | 'REVIEW_REQUIRED' | 'CONFIRMED' | 'NEEDS_ATTENTION'>('ALL');
  protected readonly incoming = signal<IncomingStatementItem[]>([]);
  protected readonly importRows = computed<ImportListRow[]>(() => [
    ...this.history().map(item => ({ kind: 'IMPORTED' as const, item })),
    ...this.incoming().map(item => ({ kind: 'INCOMING' as const, item })),
  ].sort((left, right) => this.rowDate(right).localeCompare(this.rowDate(left))));
  protected readonly filteredHistory = computed(() => this.historyStatus() === 'ALL'
    ? this.importRows()
    : this.importRows().filter(row => row.kind === 'INCOMING'
      ? this.historyStatus() === 'NEEDS_ATTENTION'
      : row.item.status === this.historyStatus()));
  protected readonly historyError = signal<string | null>(null);
  protected readonly uploadDialogOpen = signal(false);
  protected readonly coverage = signal<StatementCoverage | null>(null);
  protected readonly coverageError = signal<string | null>(null);
  protected readonly coverageYear = signal(new Date().getFullYear());
  protected readonly coverageYears = Array.from({ length: 4 }, (_, index) => new Date().getFullYear() - index);

  ngOnInit(): void { this.loadHistory(); this.loadIncoming(); this.loadCoverage(); }

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
      next: (result) => { this.result.set(result); this.importing.set(false); this.uploadDialogOpen.set(false); this.loadHistory(); this.loadIncoming(); this.loadCoverage(); this.router.navigate(['/imports', result.importId, 'review']); },
      error: (response) => { this.error.set(response.error?.message ?? 'Upload failed. Make sure the backend is running and try again.'); this.importing.set(false); },
    });
  }

  protected reset(): void { this.selectedFile.set(null); this.result.set(null); this.error.set(null); }
  protected openUploadDialog(): void { this.reset(); this.uploadDialogOpen.set(true); }
  protected closeUploadDialog(): void { if (!this.importing()) this.uploadDialogOpen.set(false); }

  protected reviewImport(importId: string): void { this.router.navigate(['/imports', importId, 'review']); }
  protected selectCoverageYear(event: Event): void {
    this.coverageYear.set(Number((event.target as HTMLSelectElement).value));
    this.loadCoverage();
  }
  protected selectHistoryStatus(event: Event): void {
    this.historyStatus.set((event.target as HTMLSelectElement).value as 'ALL' | 'REVIEW_REQUIRED' | 'CONFIRMED' | 'NEEDS_ATTENTION');
  }

  private loadHistory(): void {
    this.importsApi.history().subscribe({
      next: history => this.history.set(history),
      error: () => this.historyError.set('Import history is temporarily unavailable.'),
    });
  }
  private loadIncoming(): void {
    this.importsApi.incoming().subscribe({ next: incoming => this.incoming.set(incoming) });
  }
  protected rowDate(row: ImportListRow): string { return row.kind === 'IMPORTED' ? row.item.importedAt : row.item.modifiedAt; }

  private loadCoverage(): void {
    this.coverageError.set(null);
    this.importsApi.coverage(this.coverageYear()).subscribe({
      next: coverage => this.coverage.set(coverage),
      error: () => this.coverageError.set('Statement coverage is temporarily unavailable.'),
    });
  }
}

type ImportListRow =
  | { kind: 'IMPORTED'; item: ImportHistoryItem }
  | { kind: 'INCOMING'; item: IncomingStatementItem };
