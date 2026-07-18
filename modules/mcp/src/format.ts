export function detectFormat(source: string): "js" | "json" {
  const first = source.trimStart()[0];
  return first === "{" || first === "[" ? "json" : "js";
}

export interface SourceSummary {
  bookSourceName: string;
  bookSourceUrl: string;
  bookSourceGroup: string;
  enabled: boolean;
  isJsSource: boolean;
}

export function summarizeSources(raw: unknown, search?: string): SourceSummary[] {
  const arr = (Array.isArray(raw) ? raw : []) as Array<Record<string, unknown>>;
  const summaries = arr.map((s) => ({
    bookSourceName: String(s.bookSourceName ?? ""),
    bookSourceUrl: String(s.bookSourceUrl ?? ""),
    bookSourceGroup: String(s.bookSourceGroup ?? ""),
    enabled: Boolean(s.enabled),
    isJsSource: typeof s.mainJs === "string" && s.mainJs.length > 0,
  }));
  if (!search) {
    return summaries;
  }
  const q = search.toLowerCase();
  return summaries.filter(
    (s) =>
      s.bookSourceName.toLowerCase().includes(q) ||
      s.bookSourceUrl.toLowerCase().includes(q),
  );
}

export const TRUNCATE_LIMIT = 100_000;

export function truncateText(text: string, limit = TRUNCATE_LIMIT): string {
  if (text.length <= limit) {
    return text;
  }
  return text.slice(0, limit) + `\n…[已截断,原文 ${text.length} 字符]`;
}
