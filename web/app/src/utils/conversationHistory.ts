'use client';

export interface ConversationHistoryEntry {
  id: string;
  nonce: string;
  title: string;
  preview: string;
  updatedAt: string;
  messageCount: number;
}

const STORAGE_PREFIX = 'niuniu-wiki-conversation-history';
export const CONVERSATION_HISTORY_EVENT = 'niuniu-wiki:history-changed';

const storageKey = (scope: string) => `${STORAGE_PREFIX}:${scope}`;

const canUseStorage = () => typeof window !== 'undefined';

export const getConversationHistoryScope = (name?: string, baseUrl?: string) =>
  encodeURIComponent(
    `${baseUrl || (canUseStorage() ? window.location.pathname : '/')}:${name || ''}`,
  );

export const readConversationHistory = (
  scope: string,
): ConversationHistoryEntry[] => {
  if (!canUseStorage()) return [];
  try {
    const value = JSON.parse(localStorage.getItem(storageKey(scope)) || '[]');
    if (!Array.isArray(value)) return [];
    return value
      .filter(
        item =>
          item && typeof item.id === 'string' && typeof item.title === 'string',
      )
      .slice(0, 24);
  } catch {
    return [];
  }
};

export const rememberConversation = (
  scope: string,
  entry: Partial<ConversationHistoryEntry> & { id: string },
) => {
  if (!canUseStorage() || !entry.id) return;
  const current = readConversationHistory(scope);
  const existing = current.find(item => item.id === entry.id);
  const title = (entry.title || existing?.title || '新的问答').trim();
  const next: ConversationHistoryEntry = {
    id: entry.id,
    nonce: entry.nonce ?? existing?.nonce ?? '',
    title: title.slice(0, 80),
    preview: (entry.preview || existing?.preview || title).trim().slice(0, 120),
    updatedAt: entry.updatedAt || new Date().toISOString(),
    messageCount: Math.max(
      entry.messageCount ?? existing?.messageCount ?? 1,
      1,
    ),
  };
  const histories = [next, ...current.filter(item => item.id !== entry.id)]
    .sort(
      (a, b) =>
        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
    )
    .slice(0, 24);
  localStorage.setItem(storageKey(scope), JSON.stringify(histories));
  window.dispatchEvent(
    new CustomEvent(CONVERSATION_HISTORY_EVENT, { detail: { scope } }),
  );
};
