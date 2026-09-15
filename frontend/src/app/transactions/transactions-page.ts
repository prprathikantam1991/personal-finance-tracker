import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { AccountsApiService } from '../core/accounts-api.service';
import { Account } from '../core/account';
import { FinanceTransaction } from '../core/transaction';
import { TransactionsApiService } from '../core/transactions-api.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-transactions-page',
  imports: [CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './transactions-page.html',
  styleUrl: './transactions-page.scss',
})
export class TransactionsPage implements OnInit {
  private readonly accountsApi = inject(AccountsApiService);
  private readonly transactionsApi = inject(TransactionsApiService);
  private readonly route = inject(ActivatedRoute);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly transactions = signal<FinanceTransaction[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly selectedAccountId = signal('');
  protected readonly fromDate = signal('');
  protected readonly toDate = signal('');
  protected readonly search = signal('');
  protected readonly selectedCategory = signal('');
  protected readonly selectedConfidence = signal('');
  protected readonly categoryMessage = signal('');
  protected readonly editingCategoryId = signal<string | null>(null);
  protected readonly categoryOptions = ['Auto & Transport', 'Fitness', 'Food & Drinks', 'Gas & Fuel', 'Groceries', 'India Remittance', 'Income', 'Investments', 'Rent', 'Restaurants', 'Shopping', 'Transfer', 'Travel', 'Uncategorized', 'Utilities'];

  ngOnInit(): void {
    this.selectedCategory.set(this.route.snapshot.queryParamMap.get('category') ?? '');
    this.search.set((this.route.snapshot.queryParamMap.get('merchant') ?? '').toLowerCase());
    this.fromDate.set(this.route.snapshot.queryParamMap.get('from') ?? '');
    this.toDate.set(this.route.snapshot.queryParamMap.get('to') ?? '');
    this.accountsApi.list().subscribe({ next: (accounts) => this.accounts.set(accounts) });
    this.loadTransactions();
  }

  protected loadTransactions(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.transactionsApi.list(this.selectedAccountId(), this.fromDate(), this.toDate()).subscribe({
      next: (transactions) => { this.transactions.set(transactions); this.loading.set(false); },
      error: () => { this.loadError.set(true); this.loading.set(false); },
    });
  }

  protected updateAccount(event: Event): void { this.selectedAccountId.set((event.target as HTMLSelectElement).value); this.loadTransactions(); }
  protected updateFrom(event: Event): void { this.fromDate.set((event.target as HTMLInputElement).value); this.loadTransactions(); }
  protected updateTo(event: Event): void { this.toDate.set((event.target as HTMLInputElement).value); this.loadTransactions(); }
  protected updateSearch(event: Event): void { this.search.set((event.target as HTMLInputElement).value.toLowerCase()); }
  protected updateCategory(event: Event): void { this.selectedCategory.set((event.target as HTMLSelectElement).value); }
  protected updateConfidence(event: Event): void { this.selectedConfidence.set((event.target as HTMLSelectElement).value); }
  protected clearFilters(): void { this.selectedAccountId.set(''); this.fromDate.set(''); this.toDate.set(''); this.search.set(''); this.selectedCategory.set(''); this.selectedConfidence.set(''); this.loadTransactions(); }
  protected categories(): string[] { return [...new Set(this.transactions().map(transaction => transaction.category))].sort(); }
  protected saveCategory(transaction: FinanceTransaction, category: string, rememberForFuture: boolean): void {
    this.categoryMessage.set('');
    this.transactionsApi.updateCategory(transaction.id, category, rememberForFuture).subscribe({
      next: updated => { this.transactions.update(items => items.map(item => item.id === updated.id ? updated : item)); this.editingCategoryId.set(null); this.categoryMessage.set(`Saved ${category}. This merchant will be categorized this way in future imports.`); },
      error: () => this.categoryMessage.set('Could not save that category. Please try again.'),
    });
  }
  protected editCategory(transactionId: string): void { this.editingCategoryId.set(transactionId); this.categoryMessage.set(''); }
  protected cancelCategoryEdit(): void { this.editingCategoryId.set(null); }
  protected visibleTransactions(): FinanceTransaction[] {
    const search = this.search();
    return this.transactions().filter((transaction) => (!search || `${transaction.merchantName} ${transaction.description} ${transaction.accountName ?? ''}`.toLowerCase().includes(search)) && (!this.selectedCategory() || transaction.category === this.selectedCategory()) && (!this.selectedConfidence() || transaction.categorizationConfidence === this.selectedConfidence()));
  }
}
