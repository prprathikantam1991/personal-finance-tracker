import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ImportsApiService, ImportReview, ReviewTransaction } from '../core/imports-api.service';

@Component({ selector: 'app-import-review', imports: [RouterLink], templateUrl: './import-review-page.html', styleUrl: './import-review-page.scss' })
export class ImportReviewPage implements OnInit {
  private readonly route = inject(ActivatedRoute); private readonly api = inject(ImportsApiService);
  protected readonly review = signal<ImportReview | null>(null); protected readonly error = signal<string | null>(null); protected readonly busy = signal(false);
  private importId = '';
  ngOnInit() { this.importId = this.route.snapshot.paramMap.get('importId') ?? ''; this.load(); }
  protected load() { this.api.review(this.importId).subscribe({ next: value => this.review.set(value), error: () => this.error.set('Unable to load this import.') }); }
  protected save(row: ReviewTransaction, date: string, description: string, amount: string, balance: string) { this.busy.set(true); this.api.updateTransaction(this.importId, { ...row, date, description, amount: Number(amount), balance: balance === '' ? null : Number(balance) }).subscribe({ next: updated => { this.review.update(value => value && ({ ...value, transactions: value.transactions.map(item => item.id === updated.id ? updated : item) })); this.busy.set(false); }, error: () => { this.error.set('Could not save that row.'); this.busy.set(false); } }); }
  protected remove(row: ReviewTransaction) { this.api.deleteTransaction(this.importId, row.id).subscribe({ next: () => this.review.update(value => value && ({ ...value, transactions: value.transactions.filter(item => item.id !== row.id) })), error: () => this.error.set('Could not delete that row.') }); }
  protected confirm() { this.busy.set(true); this.api.confirm(this.importId).subscribe({ next: value => { this.review.set(value); this.busy.set(false); }, error: () => { this.error.set('Could not confirm this import.'); this.busy.set(false); } }); }
}
