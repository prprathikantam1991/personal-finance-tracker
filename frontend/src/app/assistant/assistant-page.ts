import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface AssistantReply { answer: string; toolsUsed: string[]; model: string; }
interface ChatMessage { role: 'user' | 'assistant'; text: string; toolsUsed?: string[]; }

@Component({ selector: 'app-assistant-page', imports: [], templateUrl: './assistant-page.html', styleUrl: './assistant-page.scss' })
export class AssistantPage {
  private readonly http = inject(HttpClient);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
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
    this.http.post<AssistantReply>('http://localhost:8080/api/assistant/chat', { message: question, conversation }).subscribe({
      next: reply => { this.messages.update(messages => [...messages, { role: 'assistant', text: reply.answer, toolsUsed: reply.toolsUsed }]); this.sending.set(false); },
      error: response => { this.error.set(response.error?.detail ?? 'The local assistant could not respond. Confirm LM Studio and its model are running.'); this.sending.set(false); },
    });
  }
  protected toolLabel(tool: string): string { return tool.replace(/^get_/, '').replaceAll('_', ' '); }
}
