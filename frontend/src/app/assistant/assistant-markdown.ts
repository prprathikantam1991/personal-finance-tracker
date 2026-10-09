/**
 * Renders only a small, app-owned Markdown subset. Input is escaped before
 * formatting so an assistant response can never introduce executable HTML.
 */
export function formatAssistantMarkdown(value: string): string {
  const lines = (value ?? '').replace(/\r/g, '').split('\n');
  const blocks: string[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index].trim();
    if (!line) { index++; continue; }

    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length + 1;
      blocks.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      index++;
      continue;
    }

    if (isTableStart(lines, index)) {
      const tableLines: string[] = [];
      while (index < lines.length && lines[index].trim().startsWith('|')) tableLines.push(lines[index++]);
      blocks.push(table(tableLines));
      continue;
    }

    if (isBullet(line)) {
      const items: string[] = [];
      while (index < lines.length && isBullet(lines[index].trim())) items.push(lines[index++].trim().replace(/^\\?[-*•]\s+/, ''));
      blocks.push(`<ul>${items.map(item => `<li>${inline(item)}</li>`).join('')}</ul>`);
      continue;
    }

    const paragraph: string[] = [];
    while (index < lines.length && lines[index].trim() && !isBullet(lines[index].trim())
      && !isTableStart(lines, index) && !/^(#{1,3})\s+/.test(lines[index].trim())) paragraph.push(lines[index++]);
    blocks.push(`<p>${paragraph.map(inline).join('<br>')}</p>`);
  }

  return blocks.join('');
}

function isBullet(value: string): boolean { return /^\\?[-*•]\s+/.test(value); }
function isTableStart(lines: string[], index: number): boolean {
  return lines[index].trim().startsWith('|') && index + 1 < lines.length && /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(lines[index + 1].trim());
}
function table(lines: string[]): string {
  const rows = lines.filter((_, index) => index !== 1).map(cells);
  const [header, ...body] = rows;
  return `<div class="assistant-table-wrap"><table class="assistant-table"><thead><tr>${header.map(cell => `<th>${inline(cell)}</th>`).join('')}</tr></thead><tbody>${body.map(row => `<tr>${row.map(cell => `<td>${inline(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
}
function cells(line: string): string[] { return line.trim().replace(/^\||\|$/g, '').split('|').map(cell => cell.trim()); }
function inline(value: string): string {
  return escapeHtml(value.replace(/\\([*_`])/g, '$1'))
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}
function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
