import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { formatAssistantMarkdown } from './assistant-markdown';

interface AssistantReply { conversationId?: string; answer: string; toolsUsed: string[]; model: string; executionMode?: string; stopReason?: string; evidence: string[]; steps?: { number: number; tool: string; outcome: string }[]; }
interface ChatMessage { role: 'user' | 'assistant'; text: string; toolsUsed?: string[]; evidence?: string[]; steps?: { number: number; tool: string; outcome: string }[]; }
interface Conversation { id: string; messages: Array<ChatMessage>; }

@Component({ selector: 'app-assistant-page', imports: [], templateUrl: './assistant-page.html', styleUrl: './assistant-page.scss' })
export class AssistantPage implements OnInit {
  private readonly http = inject(HttpClient);
  @ViewChild('composer') private composer?: ElementRef<HTMLElement>;
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly loadingConversation = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly suggestions = [
    'What is my current total credit utilization?',
    'How much did I spend by category last month?',
    'What are my top merchants this month?',
    'What recurring activity have you found?',
  ];
  private conversationId: string | null = null;

  ngOnInit(): void {
    const storedId = localStorage.getItem('finance-tracker-assistant-conversation');
    if (storedId) {
      this.http.get<Conversation>(`http://localhost:8080/api/assistant/conversations/${storedId}`).subscribe({
        next: conversation => this.restoreConversation(conversation), error: () => this.createConversation(),
      });
    } else this.createConversation();
  }

  protected updateDraft(event: Event): void { this.draft.set((event.target as HTMLTextAreaElement).value); }
  protected submitWithEnter(event: Event): void {
    const keyEvent = event as KeyboardEvent;
    if (keyEvent.shiftKey || keyEvent.isComposing) return;
    event.preventDefault();
    this.ask();
  }
  protected askSuggestion(question: string): void { this.draft.set(question); this.ask(); }
  protected ask(): void {
    const question = this.draft().trim();
    if (!question || this.sending() || !this.conversationId) return;
    this.messages.update(messages => [...messages, { role: 'user', text: question }]);
    this.scrollToComposer();
    this.draft.set(''); this.sending.set(true); this.error.set(null);
    const endpoint = `http://localhost:8080/api/assistant/conversations/${this.conversationId}/messages`;
    this.http.post<AssistantReply>(endpoint, { message: question }).subscribe({
      next: reply => { this.messages.update(messages => [...messages, { role: 'assistant', text: reply.answer, toolsUsed: reply.toolsUsed, evidence: reply.evidence, steps: reply.steps }]); this.sending.set(false); this.scrollToComposer(); },
      error: response => { this.error.set(this.assistantError(response.status, response.error?.detail)); this.sending.set(false); },
    });
  }
  protected newConversation(): void { if (!this.sending()) this.createConversation(); }
  protected scrollToTop(): void { window.scrollTo({ top: 0, behavior: 'smooth' }); }
  protected deleteConversation(): void {
    if (!this.conversationId || this.sending()) return;
    if (!window.confirm('Delete this conversation and its local history? This cannot be undone.')) return;
    this.http.delete(`http://localhost:8080/api/assistant/conversations/${this.conversationId}`).subscribe({ next: () => this.createConversation() });
  }
  private createConversation(): void {
    this.loadingConversation.set(true);
    this.messages.set([]);
    this.conversationId = null;
    this.http.post<Conversation>('http://localhost:8080/api/assistant/conversations', {}).subscribe({
      next: conversation => this.restoreConversation(conversation),
      error: response => { this.error.set(this.assistantError(response.status, response.error?.detail)); this.loadingConversation.set(false); },
    });
  }
  private restoreConversation(conversation: Conversation): void {
    this.conversationId = conversation.id;
    localStorage.setItem('finance-tracker-assistant-conversation', conversation.id);
    this.messages.set(conversation.messages);
    this.loadingConversation.set(false); this.error.set(null);
    if (conversation.messages.length) this.scrollToComposer();
  }
  /** Keep the active question box in view after restoring or extending a conversation. */
  private scrollToComposer(): void {
    window.setTimeout(() => this.composer?.nativeElement.scrollIntoView({ behavior: 'auto', block: 'end' }));
  }
  private assistantError(status: number, detail?: string): string {
    if (status === 504) return 'Your local model is taking longer than expected. It may still be loading—wait a moment and try again.';
    if (status === 502) return 'Your local model returned an unusable response. Try again, or reload the model in LM Studio.';
    if (status === 503 || status === 0) return 'LM Studio is not ready. Start its local server and load a model, then try again.';
    return detail ?? 'The local assistant could not respond. Please try again.';
  }
  protected toolLabel(tool: string): string {
    const labels: Record<string, string> = { get_monthly_summary: 'Monthly summary', get_category_spending: 'Category spending', get_merchant_spending: 'Top merchants', get_credit_utilization: 'Credit utilization', get_credit_paydown_plan: 'Credit paydown plan', get_recurring_activity: 'Recurring activity', get_account_overview: 'Account overview', get_account_history: 'Account history', compare_periods: 'Compare periods', compare_category_spending: 'Compare category spending', search_transactions: 'Search transactions' };
    return labels[tool] ?? tool.replace(/^get_/, '').replaceAll('_', ' ');
  }
  protected formatAssistantAnswer(text: string): string { return formatAssistantMarkdown(text); }
}
