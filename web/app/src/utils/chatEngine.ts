export interface AgentRunEvent {
  run_id: string;
  stage: string;
  status: string;
  message: string;
  iteration: number;
  mode: string;
  queries: string[];
  metrics: Record<string, string | number | boolean>;
}

export const handleThinkingContent = (content: string) => {
  const thinkRegex = /<think>([\s\S]*?)(?:<\/think>|$)/g;
  const thinkMatches: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = thinkRegex.exec(content)) !== null) {
    thinkMatches.push(match[1]);
  }

  let answerContent = content.replace(/<think>[\s\S]*?<\/think>/g, '');
  answerContent = answerContent.replace(/<think>[\s\S]*$/, '');
  return { thinkingContent: thinkMatches.join(''), answerContent };
};

export const upsertReference = <T extends { node_id: string }>(
  references: T[],
  reference: T,
) => [
  ...references.filter(item => item.node_id !== reference.node_id),
  reference,
];

export const appendAgentEvent = (
  events: AgentRunEvent[],
  event: AgentRunEvent,
) => {
  const duplicate = events.some(
    item =>
      item.stage === event.stage &&
      item.status === event.status &&
      item.message === event.message,
  );
  return duplicate ? events : [...events, event].slice(-16);
};

export const thinkingStageForAgentEvent = (event: AgentRunEvent) => {
  if (event.stage === 'reflect') return 2 as const;
  if (event.stage === 'generate') return 3 as const;
  return 1 as const;
};
