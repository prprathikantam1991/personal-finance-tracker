import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

interface Reminder { id: string; type: 'STATEMENT' | 'PAYMENT_DUE' | 'PROMOTIONAL_APR'; accountId: string; accountName: string; title: string; detail: string; date: string; severity: 'OVERDUE' | 'URGENT' | 'UPCOMING' | 'PLANNED'; }
interface RecurringTransaction { merchant: string; category: string; averageAmount: number; occurrenceCount: number; nextExpectedDate: string; }

@Component({ selector: 'app-notifications-page', imports: [CurrencyPipe, DatePipe, RouterLink], templateUrl: './notifications-page.html', styleUrl: './notifications-page.scss' })
export class NotificationsPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly reminders = signal<Reminder[]>([]);
  protected readonly recurring = signal<RecurringTransaction[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  ngOnInit(): void { this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(false);
    this.http.get<Reminder[]>('http://localhost:8080/api/reminders').subscribe({ next: items => { this.reminders.set(items); this.loading.set(false); }, error: () => { this.error.set(true); this.loading.set(false); } });
    this.http.get<RecurringTransaction[]>('http://localhost:8080/api/recurring-transactions').subscribe({ next: items => this.recurring.set(items), error: () => this.recurring.set([]) });
  }
  protected typeLabel(type: Reminder['type']): string { return type === 'PAYMENT_DUE' ? 'Card payment' : type === 'PROMOTIONAL_APR' ? 'APR reminder' : 'Statement'; }
}
