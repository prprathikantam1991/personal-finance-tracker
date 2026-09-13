import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { HttpParams } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface Summary { income: number; expenses: number; indiaRemittance: number; netCashFlow: number; reviewCount: number; categorySpending: { category: string; amount: number }[]; }
interface MonthlyTrend { month: string; income: number; expenses: number; indiaRemittance: number; netCashFlow: number; }
interface MerchantSpending { merchant: string; category: string; amount: number; transactionCount: number; previousAmount: number; }
@Component({ selector: 'app-dashboard-page', imports: [CurrencyPipe, RouterLink], templateUrl: './dashboard-page.html', styleUrl: './dashboard-page.scss' })
export class DashboardPage implements OnInit {
  private readonly http = inject(HttpClient); protected readonly summary = signal<Summary | null>(null); protected readonly previousSummary = signal<Summary | null>(null); protected readonly trends = signal<MonthlyTrend[]>([]); protected readonly merchants = signal<MerchantSpending[]>([]); protected readonly selectedTrend = signal<MonthlyTrend | null>(null); protected readonly trendMonths = signal(6); protected readonly error = signal(false); protected readonly from = signal(''); protected readonly to = signal(''); protected readonly month = signal('');
  protected merchantChange(item: MerchantSpending): number { return item.amount - item.previousAmount; }
  protected change(current: number, previous: number): number { return current - previous; }
  ngOnInit(): void { const previous = new Date(); previous.setMonth(previous.getMonth() - 1); const month = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`; this.month.set(month); this.applyMonth(month); }
  protected updateFrom(event: Event): void { this.from.set((event.target as HTMLInputElement).value); this.month.set(''); this.load(); }
  protected updateTo(event: Event): void { this.to.set((event.target as HTMLInputElement).value); this.month.set(''); this.load(); }
  protected clearFilters(): void { this.month.set(''); this.from.set(''); this.to.set(''); this.selectedTrend.set(null); this.load(false); }
  protected updateMonth(event: Event): void { const value = (event.target as HTMLInputElement).value; this.month.set(value); if (value) this.applyMonth(value); }
  private applyMonth(value: string): void { const [year, month] = value.split('-').map(Number); this.from.set(`${value}-01`); this.to.set(new Date(year, month, 0).toISOString().slice(0, 10)); this.load(); }
  protected trendHeight(value: number): number { const largest = Math.max(...this.trends().flatMap(trend => [trend.income, trend.expenses, trend.indiaRemittance, trend.netCashFlow]), 1); return Math.max(5, Math.round((Math.abs(value) / largest) * 100)); }
  protected monthLabel(value: string): string { const [year, month] = value.split('-').map(Number); return new Intl.DateTimeFormat('en-US', { month: 'short', year: '2-digit' }).format(new Date(year, month - 1, 1)); }
  protected selectTrend(trend: MonthlyTrend): void {
    this.selectedTrend.set(trend);
    this.month.set(trend.month);
    const [year, month] = trend.month.split('-').map(Number);
    this.from.set(`${trend.month}-01`);
    this.to.set(new Date(year, month, 0).toISOString().slice(0, 10));
    this.loadSummary();
  }
  protected updateTrendMonths(event: Event): void { this.trendMonths.set(Number((event.target as HTMLSelectElement).value)); this.load(this.selectedTrend() !== null); }
  private load(selectLatest = true): void { this.loadSummary(); let trendParams = new HttpParams().set('months', this.trendMonths()); if (this.to()) trendParams = trendParams.set('to', this.to()); this.http.get<MonthlyTrend[]>('http://localhost:8080/api/dashboard/monthly-trends', { params: trendParams }).subscribe({ next: value => { this.trends.set(value); this.selectedTrend.set(selectLatest ? value.at(-1) ?? null : null); }, error: () => { this.trends.set([]); this.selectedTrend.set(null); } }); }
  private loadSummary(): void { let params = new HttpParams(); if (this.from()) params = params.set('from', this.from()); if (this.to()) params = params.set('to', this.to()); this.http.get<Summary>('http://localhost:8080/api/dashboard/summary', { params }).subscribe({ next: value => { this.summary.set(value); this.error.set(false); }, error: () => this.error.set(true) }); this.http.get<MerchantSpending[]>('http://localhost:8080/api/dashboard/merchant-spending', { params }).subscribe({ next: value => this.merchants.set(value), error: () => this.merchants.set([]) }); const previous = this.previousParams(); if (!previous) { this.previousSummary.set(null); return; } this.http.get<Summary>('http://localhost:8080/api/dashboard/summary', { params: previous }).subscribe({ next: value => this.previousSummary.set(value), error: () => this.previousSummary.set(null) }); }
  private previousParams(): HttpParams | null { if (!this.from() || !this.to()) return null; if (this.month()) { const [year, month] = this.month().split('-').map(Number); const start = new Date(year, month - 2, 1); const end = new Date(year, month - 1, 0); return new HttpParams().set('from', this.isoDate(start)).set('to', this.isoDate(end)); } const start = new Date(`${this.from()}T12:00:00`); const end = new Date(`${this.to()}T12:00:00`); const days = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1; const previousEnd = new Date(start); previousEnd.setDate(previousEnd.getDate() - 1); const previousStart = new Date(previousEnd); previousStart.setDate(previousStart.getDate() - days + 1); return new HttpParams().set('from', this.isoDate(previousStart)).set('to', this.isoDate(previousEnd)); }
  private isoDate(value: Date): string { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`; }
}
