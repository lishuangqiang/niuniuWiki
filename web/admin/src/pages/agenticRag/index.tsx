import Card from '@/components/Card';
import { agenticRagApi, AgenticRun } from '@/services/agenticRagService';
import { useAppSelector } from '@/store';
import AccountTreeRounded from '@mui/icons-material/AccountTreeRounded';
import RouteRounded from '@mui/icons-material/RouteRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import {
  alpha,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  Stack,
  Typography,
} from '@mui/material';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

const modeLabel: Record<string, string> = {
  NONE: '无需检索',
  SINGLE: '单次检索',
  PARALLEL: '并行多查询',
  MULTI_HOP: '链式多跳',
  CLARIFY: '用户澄清',
};

const statusColor: Record<string, any> = {
  COMPLETED: 'success',
  RUNNING: 'primary',
  PLANNING: 'primary',
  GENERATING: 'primary',
  PAUSED: 'warning',
  CANCELLED: 'default',
  FAILED: 'error',
};

const AgenticRag = () => {
  const { kb_id } = useAppSelector(state => state.config);
  const [loading, setLoading] = useState(true);
  const [runs, setRuns] = useState<AgenticRun[]>([]);
  const [detail, setDetail] = useState<any>(null);

  const refresh = useCallback(async (silent = false) => {
    if (!kb_id) return;
    if (!silent) setLoading(true);
    try {
      setRuns(await agenticRagApi.runs(kb_id));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [kb_id]);

  useEffect(() => {
    refresh();
    const timer = window.setInterval(() => refresh(true), 4000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const metrics = useMemo(() => {
    const completed = runs.filter(run => run.status === 'COMPLETED');
    const multi = runs.filter(run => ['PARALLEL', 'MULTI_HOP'].includes(run.mode));
    const sufficient = completed.filter(run => run.evidence_sufficient);
    const averageRetrievals = completed.length
      ? completed.reduce((sum, run) => sum + Number(run.usage?.retrievals || 0), 0) / completed.length
      : 0;
    return {
      total: runs.length,
      adaptive: multi.length,
      sufficientRate: completed.length ? Math.round((sufficient.length / completed.length) * 100) : 0,
      averageRetrievals: averageRetrievals.toFixed(1),
    };
  }, [runs]);

  const openRun = async (runId: string) => {
    if (kb_id) setDetail(await agenticRagApi.detail(kb_id, runId));
  };

  if (loading) {
    return <Stack alignItems='center' justifyContent='center' sx={{ height: '65vh' }}><CircularProgress /></Stack>;
  }

  return (
    <Stack gap={2.25}>
      <Card sx={{ p: 3, position: 'relative', overflow: 'hidden' }}>
        <Box sx={{ position: 'absolute', inset: 0, pointerEvents: 'none', background: 'radial-gradient(circle at 12% 15%, rgba(0,122,255,.12), transparent 34%), radial-gradient(circle at 88% 12%, rgba(52,199,89,.10), transparent 30%)' }} />
        <Stack direction='row' alignItems='flex-start' justifyContent='space-between' sx={{ position: 'relative' }}>
          <Box>
            <Stack direction='row' alignItems='center' gap={1}>
              <RouteRounded sx={{ color: 'primary.main' }} />
              <Typography sx={{ fontSize: 23, fontWeight: 750, letterSpacing: '-0.035em' }}>Adaptive Agentic RAG</Typography>
            </Stack>
            <Typography sx={{ mt: 1, maxWidth: 760, color: 'text.tertiary', lineHeight: 1.75 }}>
              根据问题复杂度动态选择无需检索、单次、并行或多跳策略，并在次数、Token、时间预算内持续检查证据充分度。
            </Typography>
          </Box>
          <Button variant='outlined' onClick={() => refresh()}>刷新运行</Button>
        </Stack>
      </Card>

      <Stack direction='row' gap={1.5} flexWrap='wrap'>
        {[
          ['运行总数', metrics.total, '已持久化执行轨迹'],
          ['复杂问题', metrics.adaptive, '并行与多跳规划'],
          ['证据充分率', `${metrics.sufficientRate}%`, '完成运行中的充分证据'],
          ['平均检索次数', metrics.averageRetrievals, '用于观察资源自适应效果'],
        ].map(([label, value, helper]) => (
          <Card key={label} sx={{ flex: 1, minWidth: 180, p: 2.25 }}>
            <Typography sx={{ fontSize: 12, color: 'text.tertiary' }}>{label}</Typography>
            <Typography sx={{ mt: .65, fontSize: 27, fontWeight: 750, letterSpacing: '-0.04em' }}>{value}</Typography>
            <Typography sx={{ mt: .4, fontSize: 11, color: 'text.disabled' }}>{helper}</Typography>
          </Card>
        ))}
      </Stack>

      <Card>
        <Stack direction='row' alignItems='center' gap={1} sx={{ p: 2.25 }}>
          <AccountTreeRounded sx={{ fontSize: 20, color: 'text.tertiary' }} />
          <Typography sx={{ fontWeight: 700 }}>Agent 运行</Typography>
          <Chip size='small' label={runs.length} />
        </Stack>
        <Divider />
        <Stack>
          {runs.map(run => (
            <Box key={run.id} onClick={() => openRun(run.id)} sx={{ px: 2.25, py: 1.8, cursor: 'pointer', borderBottom: '1px solid', borderColor: 'divider', '&:hover': { bgcolor: 'action.hover' } }}>
              <Stack direction='row' alignItems='center' justifyContent='space-between' gap={2}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography noWrap sx={{ fontSize: 14, fontWeight: 650 }}>{run.question}</Typography>
                  <Stack direction='row' alignItems='center' gap={1} sx={{ mt: .75 }}>
                    <Chip size='small' variant='outlined' label={modeLabel[run.mode] || run.mode} sx={{ height: 21, fontSize: 10 }} />
                    <Typography sx={{ fontSize: 11, color: 'text.disabled' }}>
                      {run.usage?.iterations || 0} 轮 · {run.usage?.retrievals || 0} 次检索 · {run.usage?.evidence_count || 0} 条证据
                    </Typography>
                  </Stack>
                </Box>
                <Stack alignItems='flex-end' gap={.7} sx={{ flexShrink: 0 }}>
                  <Chip size='small' color={statusColor[run.status] || 'default'} label={run.status} />
                  <Typography sx={{ fontSize: 10.5, color: 'text.disabled' }}>{dayjs(run.created_at).format('MM-DD HH:mm:ss')}</Typography>
                </Stack>
              </Stack>
            </Box>
          ))}
          {!runs.length && <Stack alignItems='center' sx={{ py: 10, color: 'text.disabled' }}>用户发起问答后，Agent 运行会显示在这里</Stack>}
        </Stack>
      </Card>

      <Drawer anchor='right' open={Boolean(detail)} onClose={() => setDetail(null)} PaperProps={{ sx: { width: 660, p: 3 } }}>
        {detail && (
          <>
            <Stack direction='row' justifyContent='space-between' gap={2}>
              <Box>
                <Typography sx={{ fontSize: 20, fontWeight: 750 }}>{detail.question}</Typography>
                <Stack direction='row' gap={1} sx={{ mt: 1 }}>
                  <Chip size='small' label={modeLabel[detail.mode] || detail.mode} />
                  <Chip size='small' color={statusColor[detail.status] || 'default'} label={detail.status} />
                </Stack>
              </Box>
              <Button onClick={() => setDetail(null)}>关闭</Button>
            </Stack>
            <Divider sx={{ my: 2.5 }} />
            <Typography sx={{ mb: 1.5, fontWeight: 700 }}>执行轨迹</Typography>
            <Stack sx={{ borderLeft: '1px solid', borderColor: 'divider', pl: 2 }} gap={1.5}>
              {detail.steps?.map((step: any) => (
                <Box key={step.sequence_no}>
                  <Stack direction='row' alignItems='center' gap={1}>
                    <Typography sx={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase' }}>{step.stage}</Typography>
                    <Chip size='small' label={step.status} sx={{ height: 18, fontSize: 9 }} />
                  </Stack>
                  <Typography sx={{ mt: .45, fontSize: 12.5, color: 'text.secondary', lineHeight: 1.6 }}>{step.message}</Typography>
                </Box>
              ))}
            </Stack>
            <Divider sx={{ my: 2.5 }} />
            <Stack direction='row' alignItems='center' gap={1} sx={{ mb: 1.25 }}>
              <SearchRounded sx={{ fontSize: 19, color: 'text.tertiary' }} />
              <Typography sx={{ fontWeight: 700 }}>合并证据</Typography>
              <Chip size='small' label={detail.evidence?.length || 0} />
            </Stack>
            <Stack gap={1}>
              {detail.evidence?.map((item: any) => (
                <Box key={item.evidence_key} sx={{ p: 1.5, borderRadius: 2.5, bgcolor: alpha('#007AFF', .04), border: '1px solid', borderColor: alpha('#007AFF', .08) }}>
                  <Stack direction='row' alignItems='center' justifyContent='space-between' gap={2}>
                    <Typography sx={{ fontSize: 13, fontWeight: 650 }}>{item.title || '未命名文档'}</Typography>
                    <Typography sx={{ fontSize: 10.5, color: 'text.disabled' }}>第 {item.hop} 跳</Typography>
                  </Stack>
                  <Typography sx={{ mt: .5, fontSize: 11.5, color: 'text.tertiary' }}>{item.query}</Typography>
                </Box>
              ))}
            </Stack>
          </>
        )}
      </Drawer>
    </Stack>
  );
};

export default AgenticRag;
