import Card from '@/components/Card';
import { knowledgeCompilerApi, CompilerOverview, ReleaseDiagnostics } from '@/services/knowledgeCompilerService';
import { useAppSelector } from '@/store';
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import ErrorOutlineRounded from '@mui/icons-material/ErrorOutlineRounded';
import HubRounded from '@mui/icons-material/HubRounded';
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded';
import PublishedWithChangesRounded from '@mui/icons-material/PublishedWithChangesRounded';
import SourceRounded from '@mui/icons-material/SourceRounded';
import SyncRounded from '@mui/icons-material/SyncRounded';
import VerifiedUserRounded from '@mui/icons-material/VerifiedUserRounded';
import {
  Alert,
  alpha,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  InputBase,
  LinearProgress,
  Stack,
  Typography,
} from '@mui/material';
import { message } from '@ctzhian/ui';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

const statusMeta: Record<string, { label: string; color: any }> = {
  QUEUED: { label: '等待编译', color: 'default' },
  RUNNING: { label: '编译中', color: 'primary' },
  SUCCEEDED: { label: '已发布', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  ROLLED_BACK: { label: '已回滚', color: 'warning' },
};

const stages = [
  { title: '原始证据', subtitle: '不可变文档版本', icon: SourceRounded },
  { title: '语义编译', subtitle: '事实 · 实体 · 知识页', icon: AutoAwesomeRounded },
  { title: '依赖图谱', subtitle: '来源与影响范围', icon: HubRounded },
  { title: '质量门禁', subtitle: '冲突与规则检查', icon: ErrorOutlineRounded },
  { title: '原子发布', subtitle: '版本切换与回滚', icon: PublishedWithChangesRounded },
];

const metric = (label: string, value: number | string, detail: string) => (
  <Card sx={{ flex: 1, minWidth: 160, p: 2.25 }}>
    <Typography sx={{ fontSize: 12, color: 'text.tertiary' }}>{label}</Typography>
    <Typography sx={{ mt: 0.7, fontSize: 28, fontWeight: 750, letterSpacing: '-0.04em' }}>
      {value}
    </Typography>
    <Typography sx={{ mt: 0.4, fontSize: 11.5, color: 'text.disabled' }}>{detail}</Typography>
  </Card>
);

const Compiler = () => {
  const { kb_id } = useAppSelector(state => state.config);
  const [loading, setLoading] = useState(true);
  const [compiling, setCompiling] = useState(false);
  const [overview, setOverview] = useState<CompilerOverview | null>(null);
  const [runs, setRuns] = useState<any[]>([]);
  const [versions, setVersions] = useState<any[]>([]);
  const [artifacts, setArtifacts] = useState<any[]>([]);
  const [issues, setIssues] = useState<{ conflicts: any[]; lint_issues: any[] }>({
    conflicts: [],
    lint_issues: [],
  });
  const [search, setSearch] = useState('');
  const [detail, setDetail] = useState<any>(null);
  const [diagnostics, setDiagnostics] = useState<ReleaseDiagnostics>({
    validations: [], changes: [], activations: [],
  });
  const [reconciliationRuns, setReconciliationRuns] = useState<any[]>([]);
  const [reconciling, setReconciling] = useState(false);

  const refresh = useCallback(async (silent = false) => {
    if (!kb_id) return;
    if (!silent) setLoading(true);
    try {
      const [overviewData, runData, versionData, artifactData, issueData, diagnosticData, reconcileData] = await Promise.all([
        knowledgeCompilerApi.overview(kb_id),
        knowledgeCompilerApi.runs(kb_id),
        knowledgeCompilerApi.versions(kb_id),
        knowledgeCompilerApi.artifacts(kb_id),
        knowledgeCompilerApi.issues(kb_id),
        knowledgeCompilerApi.diagnostics(kb_id),
        knowledgeCompilerApi.reconciliationRuns(kb_id),
      ]);
      setOverview(overviewData);
      setRuns(runData.data || []);
      setVersions(versionData || []);
      setArtifacts(artifactData.data || []);
      setIssues(issueData || { conflicts: [], lint_issues: [] });
      setDiagnostics(diagnosticData || { validations: [], changes: [], activations: [] });
      setReconciliationRuns(reconcileData || []);
      setCompiling(['QUEUED', 'RUNNING'].includes(overviewData.latest_run?.status || ''));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [kb_id]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!compiling) return;
    const timer = window.setInterval(() => refresh(true), 2500);
    return () => window.clearInterval(timer);
  }, [compiling, refresh]);

  const visibleArtifacts = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return artifacts;
    return artifacts.filter(item =>
      `${item.title} ${item.summary} ${item.type}`.toLowerCase().includes(keyword),
    );
  }, [artifacts, search]);

  const startCompile = async () => {
    if (!kb_id || compiling) return;
    setCompiling(true);
    try {
      await knowledgeCompilerApi.compile(kb_id, true);
      message.success('全量知识编译已进入队列');
      await refresh(true);
    } catch {
      setCompiling(false);
    }
  };

  const rollback = async (versionId: string) => {
    if (!kb_id || !window.confirm('确认把线上知识指针切换到这个版本吗？原始文档不会被修改。')) return;
    await knowledgeCompilerApi.rollback(kb_id, versionId);
    message.success('知识版本已回滚');
    refresh(true);
  };

  const reconcile = async () => {
    if (!kb_id || reconciling) return;
    setReconciling(true);
    try {
      const result = await knowledgeCompilerApi.reconcile(kb_id);
      const drift = Number(result?.missing_count || 0) + Number(result?.stale_count || 0);
      message.success(drift ? `发现 ${drift} 个漂移项，已进入修复队列` : '源数据、事件账本与活动索引一致');
      await refresh(true);
    } finally {
      setReconciling(false);
    }
  };

  const openArtifact = async (id: string) => {
    if (!kb_id) return;
    setDetail(await knowledgeCompilerApi.artifact(kb_id, id));
  };

  if (loading) {
    return <Stack alignItems='center' justifyContent='center' sx={{ height: '65vh' }}><CircularProgress /></Stack>;
  }

  const latest = overview?.latest_run;
  const latestStatus = statusMeta[latest?.status || ''] || statusMeta.QUEUED;

  return (
    <Stack gap={2.25}>
      <Card sx={{ p: 3, position: 'relative', overflow: 'hidden' }}>
        <Box sx={{ position: 'absolute', inset: 0, pointerEvents: 'none', background: 'radial-gradient(circle at 12% 20%, rgba(0,122,255,.12), transparent 34%), radial-gradient(circle at 88% 10%, rgba(175,82,222,.10), transparent 32%)' }} />
        <Stack direction='row' alignItems='flex-start' justifyContent='space-between' sx={{ position: 'relative' }}>
          <Box>
            <Stack direction='row' alignItems='center' gap={1}>
              <AutoAwesomeRounded sx={{ color: 'primary.main' }} />
              <Typography sx={{ fontSize: 23, fontWeight: 750, letterSpacing: '-0.035em' }}>
                AI 知识编译器
              </Typography>
            </Stack>
            <Typography sx={{ mt: 1, maxWidth: 720, color: 'text.tertiary', lineHeight: 1.75 }}>
              把已发布文档编译成 Agent 可直接消费的知识页面，并自动维护来源依赖、事实冲突、质量门禁和可回滚版本。
            </Typography>
          </Box>
          <Stack direction='row' gap={1}>
            <Button variant='outlined' startIcon={<SyncRounded />} disabled={reconciling} onClick={reconcile}>
              {reconciling ? '正在对账' : '源数据对账'}
            </Button>
            <Button variant='contained' startIcon={<AutoAwesomeRounded />} disabled={compiling} onClick={startCompile}>
              {compiling ? '正在编译' : '全量编译'}
            </Button>
          </Stack>
        </Stack>
        {compiling && <LinearProgress sx={{ position: 'absolute', left: 0, right: 0, bottom: 0 }} />}
      </Card>

      <Stack direction='row' gap={1.5} flexWrap='wrap'>
        {metric('线上知识版本', overview?.active_version_no ? `v${overview.active_version_no}` : '—', overview?.published_at ? dayjs(overview.published_at).fromNow() : '尚未发布')}
        {metric('知识页面', overview?.artifact_count || 0, '供问答与 Agent 复用')}
        {metric('证据来源', overview?.source_count || 0, '已建立来源依赖')}
        {metric('开放问题', (overview?.conflict_count || 0) + (overview?.issue_count || 0), `${overview?.conflict_count || 0} 个冲突 · ${overview?.issue_count || 0} 个检查项`)}
        {metric('活动影子索引', overview?.active_index_status || '—', `${overview?.index_document_count || 0} 个可检索知识单元`)}
      </Stack>

      <Card sx={{ p: 2.5 }}>
        <Stack direction='row' alignItems='center' justifyContent='space-between' sx={{ mb: 2.25 }}>
          <Box>
            <Typography sx={{ fontWeight: 700 }}>编译流水线</Typography>
            <Typography sx={{ mt: 0.4, fontSize: 12, color: 'text.tertiary' }}>当前运行采用失败不切换、成功原子发布策略</Typography>
          </Box>
          {latest && <Chip size='small' color={latestStatus.color} label={`${latestStatus.label}${latest.version_no ? ` · v${latest.version_no}` : ''}`} />}
        </Stack>
        <Stack direction='row' alignItems='stretch' gap={1}>
          {stages.map((stage, index) => {
            const Icon = stage.icon;
            return <Stack key={stage.title} direction='row' alignItems='center' sx={{ flex: 1 }}>
              <Box sx={{ flex: 1, p: 1.6, borderRadius: 3, bgcolor: alpha('#007AFF', index === 1 ? .09 : .045), border: '1px solid', borderColor: alpha('#007AFF', .1) }}>
                <Icon sx={{ fontSize: 20, color: index === 1 ? 'primary.main' : 'text.tertiary' }} />
                <Typography sx={{ mt: 1, fontSize: 13, fontWeight: 650 }}>{stage.title}</Typography>
                <Typography sx={{ mt: .35, fontSize: 10.5, color: 'text.disabled' }}>{stage.subtitle}</Typography>
              </Box>
              {index < stages.length - 1 && <Typography sx={{ px: .7, color: 'text.disabled' }}>→</Typography>}
            </Stack>;
          })}
        </Stack>
      </Card>

      {latest?.status === 'FAILED' && <Alert severity='error'>{latest.error_message || '最近一次知识编译失败'}</Alert>}

      <Stack direction={{ xs: 'column', lg: 'row' }} gap={2.25}>
        <Card sx={{ flex: 1, p: 2.25 }}>
          <Stack direction='row' alignItems='center' justifyContent='space-between'>
            <Stack direction='row' alignItems='center' gap={1}>
              <VerifiedUserRounded sx={{ color: diagnostics.validations.some(item => item.status === 'FAILED') ? 'error.main' : 'success.main' }} />
              <Box>
                <Typography sx={{ fontWeight: 700 }}>版本发布门禁</Typography>
                <Typography sx={{ mt: .25, fontSize: 11.5, color: 'text.tertiary' }}>影子索引校验通过后才允许原子切换</Typography>
              </Box>
            </Stack>
            <Chip size='small' color={overview?.validation_status === 'PASSED' ? 'success' : 'warning'} label={overview?.validation_status === 'PASSED' ? '全部通过' : overview?.validation_status || '待验证'} />
          </Stack>
          <Stack direction='row' gap={1} flexWrap='wrap' sx={{ mt: 1.8 }}>
            {diagnostics.validations.map(item => <Chip
              key={item.check_code}
              size='small'
              color={item.status === 'FAILED' ? 'error' : item.severity === 'WARNING' ? 'warning' : 'success'}
              variant='outlined'
              label={`${item.status === 'FAILED' ? '阻断' : '通过'} · ${item.message}`}
            />)}
            {!diagnostics.validations.length && <Typography sx={{ fontSize: 12, color: 'text.disabled' }}>尚无发布校验记录</Typography>}
          </Stack>
        </Card>
        <Card sx={{ width: { xs: '100%', lg: 390 }, p: 2.25 }}>
          <Typography sx={{ fontWeight: 700 }}>时序变更集</Typography>
          <Typography sx={{ mt: .25, fontSize: 11.5, color: 'text.tertiary' }}>新增不覆盖旧知识，版本间保留 supersedes 链</Typography>
          <Stack direction='row' gap={.8} flexWrap='wrap' sx={{ mt: 1.6 }}>
            {Object.entries(diagnostics.changes.reduce<Record<string, number>>((acc, item) => {
              acc[item.change_type] = (acc[item.change_type] || 0) + 1;
              return acc;
            }, {})).map(([type, count]) => <Chip key={type} size='small' label={`${type} · ${count}`} />)}
            {!diagnostics.changes.length && <Chip size='small' label='本版本无源变更' />}
          </Stack>
          <Typography sx={{ mt: 1.4, fontSize: 11, color: 'text.disabled' }}>
            最近对账：{overview?.last_reconciled_at ? dayjs(overview.last_reconciled_at).fromNow() : '尚未执行'}
            {reconciliationRuns[0] ? ` · ${reconciliationRuns[0].status}` : ''}
          </Typography>
        </Card>
      </Stack>

      <Stack direction='row' gap={2.25} alignItems='flex-start'>
        <Card sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction='row' alignItems='center' justifyContent='space-between' sx={{ p: 2.25 }}>
            <Stack direction='row' alignItems='center' gap={1}>
              <Inventory2Rounded sx={{ fontSize: 19, color: 'text.tertiary' }} />
              <Typography sx={{ fontWeight: 700 }}>编译产物</Typography>
              <Chip size='small' label={visibleArtifacts.length} />
            </Stack>
            <InputBase value={search} onChange={event => setSearch(event.target.value)} placeholder='搜索知识页面' sx={{ width: 210, height: 34, px: 1.4, borderRadius: 2, bgcolor: 'action.hover', fontSize: 13 }} />
          </Stack>
          <Divider />
          <Stack sx={{ maxHeight: 480, overflowY: 'auto' }}>
            {visibleArtifacts.map(item => <Box key={item.id} onClick={() => openArtifact(item.id)} sx={{ px: 2.25, py: 1.65, cursor: 'pointer', borderBottom: '1px solid', borderColor: 'divider', '&:hover': { bgcolor: 'action.hover' } }}>
              <Stack direction='row' alignItems='center' justifyContent='space-between' gap={2}>
                <Box sx={{ minWidth: 0 }}>
                  <Stack direction='row' gap={1} alignItems='center'>
                    <Typography noWrap sx={{ fontSize: 14, fontWeight: 650 }}>{item.title}</Typography>
                    <Chip size='small' variant='outlined' label={item.type} sx={{ height: 20, fontSize: 10 }} />
                  </Stack>
                  <Typography noWrap sx={{ mt: .6, fontSize: 12, color: 'text.tertiary' }}>{item.summary || '暂无摘要'}</Typography>
                </Box>
                <Typography sx={{ flexShrink: 0, fontSize: 11, color: 'text.disabled' }}>{item.source_node_ids?.length || 0} 个来源</Typography>
              </Stack>
            </Box>)}
            {!visibleArtifacts.length && <Stack alignItems='center' sx={{ py: 10, color: 'text.disabled' }}>尚无编译产物</Stack>}
          </Stack>
        </Card>

        <Card sx={{ width: 330, flexShrink: 0 }}>
          <Box sx={{ p: 2.25 }}>
            <Typography sx={{ fontWeight: 700 }}>知识版本</Typography>
            <Typography sx={{ mt: .4, fontSize: 12, color: 'text.tertiary' }}>每次成功编译都会生成不可变版本</Typography>
          </Box>
          <Divider />
          <Stack sx={{ maxHeight: 480, overflowY: 'auto' }}>
            {versions.map(version => <Box key={version.id} sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
              <Stack direction='row' justifyContent='space-between' alignItems='center'>
                <Stack direction='row' gap={1} alignItems='center'>
                  <Typography sx={{ fontWeight: 700 }}>v{version.version_no}</Typography>
                  {version.active && <Chip size='small' color='success' icon={<CheckCircleRounded />} label='线上' />}
                  <Chip size='small' variant='outlined' color={version.validation_status === 'PASSED' ? 'success' : 'warning'} label={version.validation_status || 'PENDING'} />
                </Stack>
                {!version.active && version.status === 'PUBLISHED' && <Button size='small' onClick={() => rollback(version.id)}>回滚到此版本</Button>}
              </Stack>
              <Typography sx={{ mt: .8, fontSize: 11, color: 'text.disabled' }}>{dayjs(version.created_at).format('YYYY-MM-DD HH:mm')} · {version.stats?.artifact_count || 0} 个知识页 · {version.index_status || '无索引'}</Typography>
            </Box>)}
            {!versions.length && <Typography sx={{ p: 3, color: 'text.disabled', textAlign: 'center' }}>尚无版本</Typography>}
          </Stack>
        </Card>
      </Stack>

      {(issues.conflicts.length > 0 || issues.lint_issues.length > 0) && <Card sx={{ p: 2.25 }}>
        <Typography sx={{ fontWeight: 700 }}>质量中心</Typography>
        <Typography sx={{ mt: .4, mb: 1.5, fontSize: 12, color: 'text.tertiary' }}>冲突不会被静默覆盖；问题会随版本固化，方便定位来源</Typography>
        <Stack direction='row' gap={1} flexWrap='wrap'>
          {issues.conflicts.slice(0, 8).map(issue => <Chip key={issue.id} color='warning' variant='outlined' label={`冲突 · ${issue.conflict_key}`} />)}
          {issues.lint_issues.slice(0, 8).map(issue => <Chip key={issue.id} color={issue.severity === 'ERROR' ? 'error' : 'default'} variant='outlined' label={`${issue.rule_code} · ${issue.artifact_title || '全局'}`} />)}
        </Stack>
      </Card>}

      <Drawer anchor='right' open={Boolean(detail)} onClose={() => setDetail(null)} PaperProps={{ sx: { width: 620, p: 3 } }}>
        {detail && <>
          <Stack direction='row' alignItems='center' justifyContent='space-between'>
            <Box>
              <Typography sx={{ fontSize: 21, fontWeight: 750 }}>{detail.title}</Typography>
              <Typography sx={{ mt: .5, fontSize: 12, color: 'text.tertiary' }}>v{detail.version_no} · {detail.type} · 可信度 {Math.round(Number(detail.confidence || 0) * 100)}%</Typography>
            </Box>
            <Button onClick={() => setDetail(null)}>关闭</Button>
          </Stack>
          <Divider sx={{ my: 2.5 }} />
          <Typography sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover', color: 'text.secondary', lineHeight: 1.7 }}>{detail.summary}</Typography>
          <Typography component='pre' sx={{ mt: 2.5, whiteSpace: 'pre-wrap', font: 'inherit', lineHeight: 1.85 }}>{detail.content}</Typography>
          {detail.timeline?.length > 0 && <>
            <Divider sx={{ my: 2.5 }} />
            <Typography sx={{ mb: 1, fontWeight: 700 }}>时序版本链</Typography>
            {detail.timeline.map((version: any) => <Stack key={version.id} direction='row' alignItems='center' justifyContent='space-between' sx={{ py: .75 }}>
              <Stack direction='row' gap={1} alignItems='center'>
                <Chip size='small' color={version.status === 'EFFECTIVE' ? 'success' : 'default'} label={`v${version.knowledge_version} · ${version.status}`} />
                {version.supersedes_id && <Typography sx={{ fontSize: 11, color: 'text.disabled' }}>取代上一条知识</Typography>}
              </Stack>
              <Typography sx={{ fontSize: 11, color: 'text.disabled' }}>{dayjs(version.recorded_at).format('MM-DD HH:mm')}</Typography>
            </Stack>)}
          </>}
          <Divider sx={{ my: 2.5 }} />
          <Typography sx={{ mb: 1, fontWeight: 700 }}>来源依赖</Typography>
          {detail.dependencies?.map((source: any) => <Stack key={source.source_release_id} direction='row' gap={1} sx={{ py: .7 }}><SourceRounded sx={{ fontSize: 18, color: 'text.disabled' }} /><Typography sx={{ fontSize: 13 }}>{source.source_name || source.source_node_id}</Typography></Stack>)}
        </>}
      </Drawer>
    </Stack>
  );
};

export default Compiler;
