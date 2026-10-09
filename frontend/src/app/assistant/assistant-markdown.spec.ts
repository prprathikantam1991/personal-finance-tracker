import { formatAssistantMarkdown } from './assistant-markdown';

describe('formatAssistantMarkdown', () => {
  it('renders supported formatting after escaping model content', () => {
    const formatted = formatAssistantMarkdown('## Summary\n\n- **Groceries**: $23.84\n- Travel: $41.12\n\n<img src=x onerror=alert(1)>');

    expect(formatted).toContain('<h3>Summary</h3>');
    expect(formatted).toContain('<strong>Groceries</strong>');
    expect(formatted).toContain('<li>Travel: $41.12</li>');
    expect(formatted).not.toContain('<img');
    expect(formatted).toContain('&lt;img');
  });

  it('renders simple Markdown tables', () => {
    const formatted = formatAssistantMarkdown('| Month | Spending |\n| :--- | ---: |\n| July | $49.31 |');

    expect(formatted).toContain('<table class="assistant-table">');
    expect(formatted).toContain('<th>Month</th>');
    expect(formatted).toContain('<td>$49.31</td>');
  });
});
