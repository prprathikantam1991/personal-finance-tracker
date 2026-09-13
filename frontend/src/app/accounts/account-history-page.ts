import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Account } from '../core/account';

interface Snapshot { cycleStartDate: string | null; cycleEndDate: string | null; beginningBalance: number | null; endingBalance: number | null; totalCredits: number | null; totalDebits: number | null; interestEarned: number | null; annualInterestRate: number | null; annualPercentageYield: number | null; creditLimit: number | null; availableCredit: number | null; feesCharged: number | null; interestCharged: number | null; importedAt: string; }
interface AccountHistory { account: Account; snapshots: Snapshot[]; }

@Component({ selector: 'app-account-history-page', imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink], templateUrl: './account-history-page.html', styleUrl: './account-history-page.scss' })
export class AccountHistoryPage implements OnInit {
  private readonly http = inject(HttpClient); private readonly route = inject(ActivatedRoute);
  protected readonly Math = Math;
  protected readonly history = signal<AccountHistory | null>(null); protected readonly selectedSnapshot = signal<Snapshot | null>(null); protected readonly error = signal(false);
  ngOnInit(): void { const id = this.route.snapshot.paramMap.get('accountId'); if (!id) { this.error.set(true); return; } this.http.get<AccountHistory>(`http://localhost:8080/api/accounts/${id}/history`).subscribe({ next: value => { this.history.set(value); this.selectedSnapshot.set(value.snapshots.at(-1) ?? null); }, error: () => this.error.set(true) }); }
  protected balanceHeight(value: number | null): number {
    const values = this.history()?.snapshots.map(item => item.endingBalance ?? 0) ?? [];
    const minimum = Math.min(...values);
    const maximum = Math.max(...values);
    if (maximum === minimum) return 55;
    // This is a relative scale so smaller month-to-month changes remain visible.
    return Math.round(18 + ((value ?? 0) - minimum) / (maximum - minimum) * 82);
  }
  protected snapshotDate(snapshot: Snapshot): string { return snapshot.cycleEndDate ?? snapshot.importedAt; }
  protected selectSnapshot(snapshot: Snapshot): void { this.selectedSnapshot.set(snapshot); }
  protected balanceBarClass(): string {
    switch (this.history()?.account.accountType) {
      case 'CREDIT_CARD': return 'credit-bar';
      case 'SAVINGS': return 'savings-bar';
      case 'CHECKING': return 'checking-bar';
      default: return 'other-bar';
    }
  }
  protected balanceChange(snapshot: Snapshot): number | null {
    const snapshots = this.history()?.snapshots ?? [];
    const index = snapshots.indexOf(snapshot);
    const previousBalance = index < 1 ? null : snapshots[index - 1].endingBalance;
    if (snapshot.endingBalance === null || previousBalance === null) return null;
    return snapshot.endingBalance - previousBalance;
  }
}
