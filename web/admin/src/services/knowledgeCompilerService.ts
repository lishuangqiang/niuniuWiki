import httpRequest, { ContentType } from '@/request/httpClient';

export interface CompilerRun {
  id: string;
  trigger_type: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'ROLLED_BACK';
  force_full: boolean;
  version_id?: string;
  version_no?: number;
  stats?: Record<string, any>;
  error_message?: string;
  created_at: string;
  completed_at?: string;
}

export interface CompilerOverview {
  active_version_id?: string;
  active_version_no?: number;
  artifact_count: number;
  source_count: number;
  conflict_count: number;
  issue_count: number;
  published_at?: string;
  active_index_id?: string;
  active_index_status?: string;
  index_document_count?: number;
  validation_status?: string;
  last_reconciled_at?: string;
  latest_run?: CompilerRun;
}

export interface ReleaseDiagnostics {
  validations: Array<{
    check_code: string;
    severity: 'ERROR' | 'WARNING' | 'INFO';
    status: 'PASSED' | 'FAILED';
    message: string;
    metrics?: Record<string, any>;
  }>;
  changes: Array<{
    node_id: string;
    change_type: string;
    before_snapshot?: Record<string, any>;
    after_snapshot?: Record<string, any>;
  }>;
  activations: any[];
}

export const knowledgeCompilerApi = {
  overview: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/overview',
      method: 'GET',
      query: { kb_id: kbId },
    }) as Promise<CompilerOverview>,
  compile: (kbId: string, forceFull = true, nodeIds: string[] = []) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/compile',
      method: 'POST',
      type: ContentType.Json,
      body: { kb_id: kbId, force_full: forceFull, node_ids: nodeIds },
    }) as Promise<{ run_id: string }>,
  runs: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/runs',
      method: 'GET',
      query: { kb_id: kbId, page: 1, per_page: 20 },
    }) as Promise<{ total: number; data: CompilerRun[] }>,
  versions: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/versions',
      method: 'GET',
      query: { kb_id: kbId },
    }) as Promise<any[]>,
  artifacts: (kbId: string, search = '') =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/artifacts',
      method: 'GET',
      query: { kb_id: kbId, search, page: 1, per_page: 100 },
    }) as Promise<{ total: number; data: any[] }>,
  artifact: (kbId: string, id: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/artifact/detail',
      method: 'GET',
      query: { kb_id: kbId, id },
    }),
  issues: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/issues',
      method: 'GET',
      query: { kb_id: kbId },
    }) as Promise<{ conflicts: any[]; lint_issues: any[] }>,
  diagnostics: (kbId: string, versionId?: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/release-diagnostics',
      method: 'GET',
      query: { kb_id: kbId, ...(versionId ? { version_id: versionId } : {}) },
    }) as Promise<ReleaseDiagnostics>,
  reconciliationRuns: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/reconciliation-runs',
      method: 'GET',
      query: { kb_id: kbId },
    }) as Promise<any[]>,
  reconcile: (kbId: string) =>
    httpRequest<any>({
      path: '/api/v1/knowledge-compiler/reconcile',
      method: 'POST',
      type: ContentType.Json,
      body: { kb_id: kbId },
    }),
  rollback: (kbId: string, versionId: string) =>
    httpRequest<void>({
      path: '/api/v1/knowledge-compiler/rollback',
      method: 'POST',
      type: ContentType.Json,
      body: { kb_id: kbId, version_id: versionId },
    }),
};
