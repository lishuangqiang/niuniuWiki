import httpRequest, { ContentType } from '@/request/httpClient';

export interface AgenticRun {
  id: string;
  conversation_id: string;
  question: string;
  mode: 'NONE' | 'SINGLE' | 'PARALLEL' | 'MULTI_HOP' | 'CLARIFY';
  status: string;
  usage?: {
    retrievals?: number;
    iterations?: number;
    tokens?: number;
    elapsed_ms?: number;
    evidence_count?: number;
    stop_reason?: string;
  };
  evidence_sufficient: boolean;
  evidence_confidence: number;
  stop_reason?: string;
  error_message?: string;
  created_at: string;
}

export const agenticRagApi = {
  runs: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/agentic-rag/runs',
      method: 'GET',
      query: { kb_id: kbId, limit: 100 },
    }) as Promise<AgenticRun[]>,
  detail: (kbId: string, runId: string) =>
    httpRequest<any>({
      path: '/api/v1/agentic-rag/run',
      method: 'GET',
      query: { kb_id: kbId, run_id: runId },
    }),
  cancel: (kbId: string, runId: string) =>
    httpRequest<any>({
      path: '/api/v1/agentic-rag/cancel',
      method: 'POST',
      type: ContentType.Json,
      body: { kb_id: kbId, run_id: runId },
    }),
};
