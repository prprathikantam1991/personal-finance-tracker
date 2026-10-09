import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface Reminder { id: string; accountId: string; accountName: string; title: string; detail: string; date: string; severity: 'OVERDUE' | 'URGENT' | 'UPCOMING' | 'PLANNED'; }

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  protected readonly notificationOpen = signal(false);
  protected readonly reminders = signal<Reminder[]>([]);

  ngOnInit(): void {
    this.http.get<Reminder[]>('http://localhost:8080/api/reminders').subscribe({ next: reminders => this.reminders.set(reminders), error: () => this.reminders.set([]) });
  }

  protected ask(input: HTMLInputElement): void {
    const value = input.value.trim();
    if (!value) return;
    input.value = '';
    this.router.navigate(['/assistant'], { queryParams: { ask: value } });
  }

  protected isAssistantPage(): boolean { return this.router.url.startsWith('/assistant'); }

  protected toggleNotifications(): void { this.notificationOpen.update(open => !open); }
  protected closeNotifications(): void { this.notificationOpen.set(false); }
}
