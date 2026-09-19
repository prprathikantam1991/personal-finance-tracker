import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface AssistantReply { answer: string; toolsUsed: string[]; model: string; executionMode?: string; stopReason?: string; evidence: string[]; steps?: { number: number; tool: string; outcome: string }[]; }
interface ChatMessage { role: 'user' | 'assistant'; text: string; toolsUsed?: string[]; evidence?: string[]; steps?: { number: number; tool: string; outcome: string }[]; }

@Component({ selector: 'app-assistant-page', imports: [], templateUrl: './assistant-page.html', styleUrl: './assistant-page.scss' })
export class AssistantPage {
  private readonly http = inject(HttpClient);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly agentMode = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly suggestions = [
    'What is my current total credit utilization?',
    'How much did I spend by category last month?',
    'What are my top merchants this month?',
    'What recurring activity have you found?',
  ];

  protected updateDraft(event: Event): void { this.draft.set((event.target as HTMLTextAreaElement).value); }
  protected askSuggestion(question: string): void { this.draft.set(question); this.ask(); }
  protected ask(): void {
    const question = this.draft().trim();
    if (!question || this.sending()) return;
    this.messages.update(messages => [...messages, { role: 'user', text: question }]);
    this.draft.set(''); this.sending.set(true); this.error.set(null);
    const conversation = this.messages().slice(0, -1).slice(-12).map(message => ({ role: message.role, text: message.text }));
    const endpoint = this.agentMode() ? 'http://localhost:8080/api/assistant/agent-runs' : 'http://localhost:8080/api/assistant/chat';
    this.http.post<AssistantReply>(endpoint, { message: question, conversation }).subscribe({
      next: reply => { this.messages.update(messages => [...messages, { role: 'assistant', text: reply.answer, toolsUsed: reply.toolsUsed, evidence: reply.evidence, steps: reply.steps }]); this.sending.set(false); },
      error: response => { this.error.set(this.assistantError(response.status, response.error?.detail)); this.sending.set(false); },
    });
  }
  protected setAgentMode(enabled: boolean): void { this.agentMode.set(enabled); }
  private assistantError(status: number, detail?: string): string {
    if (status === 504) return 'Your local model is taking longer than expected. It may still be loading—wait a moment and try again.';
    if (status === 502) return 'Your local model returned an unusable response. Try again, or reload the model in LM Studio.';
    if (status === 503 || status === 0) return 'LM Studio is not ready. Start its local server and load a model, then try again.';
    return detail ?? 'The local assistant could not respond. Please try again.';
  }
  protected toolLabel(tool: string): string {
    const labels: Record<string, string> = { get_monthly_summary: 'Monthly summary', get_category_spending: 'Category spending', get_merchant_spending: 'Top merchants', get_credit_utilization: 'Credit utilization', get_recurring_activity: 'Recurring activity', get_account_overview: 'Account overview', get_account_history: 'Account history', compare_periods: 'Compare periods', compare_category_spending: 'Compare category spending', search_transactions: 'Search transactions' };
    return labels[tool] ?? tool.replace(/^get_/, '').replaceAll('_', ' ');
  }
}
