export function normalizeTags(tags?: string[]): string[] {
  const words = (tags ?? []).join(' ').normalize('NFC').match(/[\p{L}\p{N}_][\p{L}\p{M}\p{N}_-]*/gu) ?? [];
  return [...new Set(words.map((word) => `#${word.toLowerCase()}`))];
}
