import { ChunkResultItem } from '@/assets/type';

const CITATION_PATTERN = /\[\s*文档\s*(\d+)\s*\]/g;
const REFERENCE_LINE_PATTERN = /^>\s*\[\d+\]\.\s*\[.*\]\(.*\)\s*$/;

/**
 * 兼容历史消息：移除旧的候选文档列表，只保留回答实际声明的引用并连续编号。
 *
 * @author 程序员牛肉
 * @since 2026-06-17
 */
export const reconcileCitedReferences = (
  answer: string,
  candidates: ChunkResultItem[],
): { answer: string; references: ChunkResultItem[] } => {
  const content = stripLegacyReferenceBlock(answer || '');
  const renumbering = new Map<number, number>();
  const rewritten = content.replace(
    CITATION_PATTERN,
    (_matched, rawNumber: string) => {
      const originalNumber = Number(rawNumber);
      if (originalNumber < 1 || originalNumber > candidates.length) return '';
      if (!renumbering.has(originalNumber)) {
        renumbering.set(originalNumber, renumbering.size + 1);
      }
      return `[文档 ${renumbering.get(originalNumber)}]`;
    },
  );
  const references = Array.from(renumbering.keys()).map(
    number => candidates[number - 1],
  );
  if (references.length === 0) {
    return { answer: rewritten, references: [] };
  }
  const referenceBlock = references
    .map(
      (reference, index) =>
        `> [${index + 1}]. [${reference.name || `参考文档 ${index + 1}`}](${reference.url || ''})`,
    )
    .join('\n');
  return {
    answer: `${rewritten.trimEnd()}\n\n${referenceBlock}\n`,
    references,
  };
};

const stripLegacyReferenceBlock = (answer: string): string => {
  const lines = answer.split('\n');
  let cursor = lines.length - 1;
  while (cursor >= 0 && lines[cursor].trim() === '') cursor -= 1;
  let foundReference = false;
  while (cursor >= 0 && REFERENCE_LINE_PATTERN.test(lines[cursor].trim())) {
    foundReference = true;
    cursor -= 1;
  }
  if (!foundReference) return answer;
  while (cursor >= 0 && lines[cursor].trim() === '') cursor -= 1;
  return lines.slice(0, cursor + 1).join('\n');
};
