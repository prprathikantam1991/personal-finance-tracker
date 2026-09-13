import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AccountsApiService } from '../core/accounts-api.service';
import { Account, AccountType } from '../core/account';

@Component({
  selector: 'app-accounts-page',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe, RouterLink],
  templateUrl: './accounts-page.html',
  styleUrl: './accounts-page.scss',
})
export class AccountsPage implements OnInit {
  private readonly accountsApi = inject(AccountsApiService);
  protected readonly Math = Math;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly selectedType = signal<AccountType | 'ALL'>('ALL');
  protected readonly filters: { label: string; value: AccountType | 'ALL' }[] = [
    { label: 'All', value: 'ALL' },
    { label: 'Cards', value: 'CREDIT_CARD' },
    { label: 'Savings', value: 'SAVINGS' },
    { label: 'Checking', value: 'CHECKING' },
    { label: 'Other', value: 'OTHER' },
  ];
  protected readonly filteredAccounts = computed(() => {
    const type = this.selectedType();
    return type === 'ALL' ? this.accounts() : this.accounts().filter((account) => account.accountType === type);
  });
  protected readonly accountGroups = computed(() => this.filters.slice(1)
    .map(filter => ({ label: filter.label, accounts: this.filteredAccounts().filter(account => account.accountType === filter.value) }))
    .filter(group => group.accounts.length));
  protected readonly cashBalance = computed(() => this.accounts().filter(account => account.accountType !== 'CREDIT_CARD').reduce((sum, account) => sum + (account.statementBalance ?? 0), 0));
  protected readonly cardDebt = computed(() => this.accounts().filter(account => account.accountType === 'CREDIT_CARD').reduce((sum, account) => sum + Math.max(account.statementBalance ?? 0, 0), 0));
  protected readonly netPosition = computed(() => this.cashBalance() - this.cardDebt());
  protected readonly creditCards = computed(() => this.accounts().filter(account => account.accountType === 'CREDIT_CARD' && account.creditLimit && account.creditLimit > 0));
  protected readonly totalCreditLimit = computed(() => this.creditCards().reduce((sum, account) => sum + (account.creditLimit ?? 0), 0));
  protected readonly totalCreditUsed = computed(() => this.creditCards().reduce((sum, account) => sum + Math.max(account.statementBalance ?? 0, 0), 0));
  protected readonly totalCreditAvailable = computed(() => Math.max(0, this.totalCreditLimit() - this.totalCreditUsed()));
  protected readonly overallUtilization = computed(() => this.totalCreditLimit() ? (this.totalCreditUsed() / this.totalCreditLimit()) * 100 : null);
  protected readonly previousTotalCreditLimit = computed(() => this.creditCards().filter(account => account.previousCreditLimit && account.previousStatementBalance !== null && account.previousStatementBalance !== undefined).reduce((sum, account) => sum + (account.previousCreditLimit ?? 0), 0));
  protected readonly previousTotalCreditUsed = computed(() => this.creditCards().filter(account => account.previousCreditLimit && account.previousStatementBalance !== null && account.previousStatementBalance !== undefined).reduce((sum, account) => sum + Math.max(account.previousStatementBalance ?? 0, 0), 0));
  protected readonly previousOverallUtilization = computed(() => this.previousTotalCreditLimit() ? (this.previousTotalCreditUsed() / this.previousTotalCreditLimit()) * 100 : null);
  protected utilizationChange(account: Account): number | null { return account.creditUtilizationPercent === null || account.creditUtilizationPercent === undefined || account.previousCreditUtilizationPercent === null || account.previousCreditUtilizationPercent === undefined ? null : account.creditUtilizationPercent - account.previousCreditUtilizationPercent; }
  protected absolute(value: number): number { return Math.abs(value); }

  ngOnInit(): void { this.loadAccounts(); }

  protected loadAccounts(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.accountsApi.list().subscribe({
      next: (accounts) => { this.accounts.set(accounts); this.loading.set(false); },
      error: () => { this.loadError.set(true); this.loading.set(false); },
    });
  }

  protected accountTypeLabel(type: AccountType): string {
    return type.replace('_', ' ').toLowerCase();
  }

  protected selectType(type: AccountType | 'ALL'): void { this.selectedType.set(type); }

}
