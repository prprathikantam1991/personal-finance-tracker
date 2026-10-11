import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FinanceAuthService } from './core/finance-auth.service';
import { formatAssistantMarkdown } from './assistant/assistant-markdown';

interface Reminder { id: string; accountId: string; accountName: string; title: string; detail: string; date: string; severity: 'OVERDUE' | 'URGENT' | 'UPCOMING' | 'PLANNED'; }
interface AssistantReply { answer: string; toolsUsed: string[]; evidence: string[]; steps?: { number: number; tool: string; outcome: string }[]; }
interface ChatMessage { role: 'user' | 'assistant'; text: string; toolsUsed?: string[]; evidence?: string[]; steps?: { number: number; tool: string; outcome: string }[]; }
interface Conversation { id: string; messages: ChatMessage[]; }

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  protected readonly auth = inject(FinanceAuthService);
  protected readonly canShowProtectedContent = computed(() =>
    this.auth.statusLoaded() && (!this.auth.authenticationEnabled() || this.auth.authenticated()),
  );
  protected readonly notificationOpen = signal(false);
  protected readonly profileOpen = signal(false);
  protected readonly intentionallySignedOut = signal(sessionStorage.getItem('finance-tracker-intentionally-signed-out') === 'true');
  protected readonly profilePictureFailed = signal(false);
  protected readonly profileImageUrl = computed(() => {
    const picture = this.auth.profile()?.picture?.trim();
    return picture && !this.profilePictureFailed() ? picture : null;
  });
  protected readonly reminders = signal<Reminder[]>([]);
  protected readonly assistantOpen = signal(false);
  protected readonly assistantMessages = signal<ChatMessage[]>([]);
  protected readonly assistantDraft = signal('');
  protected readonly assistantSending = signal(false);
  protected readonly assistantLoading = signal(false);
  protected readonly assistantError = signal<string | null>(null);
  private assistantConversationId: string | null = null;

  ngOnInit(): void {
    this.auth.loadStatus();
    this.http.get<Reminder[]>('http://localhost:8080/api/reminders').subscribe({ next: reminders => this.reminders.set(reminders), error: () => this.reminders.set([]) });
  }

  protected toggleAssistant(): void {
    this.assistantOpen.update(open => !open);
    if (this.assistantOpen()) this.loadAssistantConversation();
  }

  protected isAssistantPage(): boolean { return this.router.url.startsWith('/assistant'); }

  protected toggleNotifications(): void { this.notificationOpen.update(open => !open); }
  protected closeNotifications(): void { this.notificationOpen.set(false); }
  protected toggleProfile(): void { this.profileOpen.update(open => !open); }
  protected closeProfile(): void { this.profileOpen.set(false); }
  protected useAvatarFallback(): void { this.profilePictureFailed.set(true); }
  @HostListener('document:click', ['$event'])
  protected closeHeaderPopoversWhenClickingOutside(event: MouseEvent): void {
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (this.profileOpen() && !target.closest('.profile-control')) this.closeProfile();
    if (this.notificationOpen() && !target.closest('.notification-control')) this.closeNotifications();
  }
  protected signOut(): void {
    this.closeNotifications();
    this.closeProfile();
    sessionStorage.setItem('finance-tracker-intentionally-signed-out', 'true');
    this.intentionallySignedOut.set(true);
    this.auth.signOut();
  }
  protected signIn(): void {
    sessionStorage.removeItem('finance-tracker-intentionally-signed-out');
    this.intentionallySignedOut.set(false);
    this.auth.signIn();
  }

  protected updateAssistantDraft(event: Event): void { this.assistantDraft.set((event.target as HTMLTextAreaElement).value); }
  protected submitAssistantWithEnter(event: Event): void {
    const keyEvent = event as KeyboardEvent;
    if (keyEvent.shiftKey || keyEvent.isComposing) return;
    keyEvent.preventDefault();
    this.askAssistant();
  }
  protected askAssistant(): void {
    const question = this.assistantDraft().trim();
    if (!question || this.assistantSending() || !this.assistantConversationId) return;
    this.assistantMessages.update(messages => [...messages, { role: 'user', text: question }]);
    this.assistantDraft.set('');
    this.assistantSending.set(true);
    this.assistantError.set(null);
    this.http.post<AssistantReply>(`http://localhost:8080/api/assistant/conversations/${this.assistantConversationId}/messages`, { message: question }).subscribe({
      next: reply => {
        this.assistantMessages.update(messages => [...messages, { role: 'assistant', text: reply.answer, toolsUsed: reply.toolsUsed, evidence: reply.evidence, steps: reply.steps }]);
        this.assistantSending.set(false);
      },
      error: response => {
        this.assistantError.set(this.assistantErrorMessage(response.status, response.error?.detail));
        this.assistantSending.set(false);
      },
    });
  }
  protected maximizeAssistant(): void {
    this.assistantOpen.set(false);
    this.router.navigate(['/assistant']);
  }
  protected userInitials(): string {
    const name = this.auth.profile()?.name?.trim();
    return name ? name.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() : 'U';
  }
  protected formatAssistantAnswer(text: string): string { return formatAssistantMarkdown(text); }
  protected toolLabel(tool: string): string {
    const labels: Record<string, string> = { get_monthly_summary: 'Monthly summary', get_category_spending: 'Category spending', get_merchant_spending: 'Top merchants', get_credit_utilization: 'Credit utilization', get_credit_paydown_plan: 'Credit paydown plan', get_recurring_activity: 'Recurring activity', get_account_overview: 'Account overview', compare_periods: 'Compare periods', compare_category_spending: 'Category comparison', search_transactions: 'Transaction search' };
    return labels[tool] ?? tool.replace(/^get_/, '').replaceAll('_', ' ');
  }

  private loadAssistantConversation(): void {
    if (this.assistantConversationId || this.assistantLoading()) return;
    this.assistantLoading.set(true);
    const storedId = localStorage.getItem('finance-tracker-assistant-conversation');
    if (storedId) {
      this.http.get<Conversation>(`http://localhost:8080/api/assistant/conversations/${storedId}`).subscribe({
        next: conversation => this.restoreAssistantConversation(conversation),
        error: () => this.createAssistantConversation(),
      });
    } else this.createAssistantConversation();
  }

  private createAssistantConversation(): void {
    this.http.post<Conversation>('http://localhost:8080/api/assistant/conversations', {}).subscribe({
      next: conversation => this.restoreAssistantConversation(conversation),
      error: response => {
        this.assistantError.set(this.assistantErrorMessage(response.status, response.error?.detail));
        this.assistantLoading.set(false);
      },
    });
  }

  private restoreAssistantConversation(conversation: Conversation): void {
    this.assistantConversationId = conversation.id;
    localStorage.setItem('finance-tracker-assistant-conversation', conversation.id);
    this.assistantMessages.set(conversation.messages);
    this.assistantLoading.set(false);
  }

  private assistantErrorMessage(status: number, detail?: string): string {
    if (status === 504) return 'The selected AI model is taking longer than expected. Try again in a moment.';
    if (status === 503 && detail) return detail;
    if (status === 503 || status === 0) return 'The selected AI provider is unavailable. Check its connection and try again.';
    return detail || 'The Assistant could not respond. Please try again.';
  }
}
