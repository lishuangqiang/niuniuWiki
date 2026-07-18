'use client';

import { ChunkResultItem } from '@/assets/type';
import { useBasePath } from '@/hooks';
import { getShareV1NodeDetail } from '@/request/ShareNode';
import { V1ShareNodeDetailResp } from '@/request/types';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import ArrowOutwardRounded from '@mui/icons-material/ArrowOutwardRounded';
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined';
import MenuBookRounded from '@mui/icons-material/MenuBookRounded';
import { Editor, useTiptap } from '@ctzhian/tiptap';
import {
  alpha,
  Box,
  Button,
  CircularProgress,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';

interface ReferenceWorkspaceProps {
  references: ChunkResultItem[];
  activeReferenceId?: string;
  onSelect: (reference: ChunkResultItem) => void;
  onMobileBack: () => void;
}

const ReferenceWorkspace = ({
  references,
  activeReferenceId,
  onSelect,
  onMobileBack,
}: ReferenceWorkspaceProps) => {
  const basePath = useBasePath();
  const [detail, setDetail] = useState<V1ShareNodeDetailResp>();
  const [loading, setLoading] = useState(false);
  const activeReference = useMemo(
    () => references.find(item => item.node_id === activeReferenceId),
    [activeReferenceId, references],
  );
  const editorRef = useTiptap({
    content: '',
    editable: false,
    contentType: 'html',
    immediatelyRender: false,
    baseUrl: basePath,
  });

  useEffect(() => {
    if (!activeReference?.node_id) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    getShareV1NodeDetail(
      {
        id: activeReference.node_id,
        format: 'html',
        ...(activeReference.node_release_id
          ? { release_id: activeReference.node_release_id }
          : {}),
      } as any,
      { isAlert: false },
    )
      .then(result => {
        if (!cancelled) setDetail(result as V1ShareNodeDetailResp);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeReference?.node_id, activeReference?.node_release_id]);

  useEffect(() => {
    if (!detail || !editorRef.editor) return;
    editorRef.setContent(
      detail.content || '',
      detail.meta?.content_type === 'md' ? 'markdown' : 'html',
    );
  }, [detail, editorRef.editor]);

  return (
    <Box
      component='aside'
      aria-label='参考文档工作区'
      sx={{
        minWidth: 0,
        minHeight: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        borderRadius: 0,
        border: 0,
        bgcolor: 'transparent',
        boxShadow: 'none',
      }}
    >
      {references.length > 0 && (
        <Stack
          direction='row'
          alignItems='center'
          justifyContent='space-between'
          sx={{ height: 56, flexShrink: 0, px: { xs: 2, md: 2.5 } }}
        >
          <Stack direction='row' alignItems='center' gap={1}>
            <IconButton
              aria-label='返回对话'
              onClick={onMobileBack}
              sx={{ display: { xs: 'inline-flex', lg: 'none' } }}
            >
              <ArrowBackRounded />
            </IconButton>
            <MenuBookRounded sx={{ color: '#777', fontSize: 18 }} />
            <Typography sx={{ fontSize: 12.5, fontWeight: 650 }}>
              {references.length} 篇参考文档
            </Typography>
          </Stack>
          {activeReference && (
            <Button
              component={Link}
              href={`${basePath}/node/${activeReference.node_id}${
                activeReference.node_release_id
                  ? `?release_id=${encodeURIComponent(activeReference.node_release_id)}`
                  : ''
              }`}
              target='_blank'
              rel='noopener noreferrer'
              endIcon={<ArrowOutwardRounded sx={{ fontSize: 15 }} />}
              sx={{ color: '#777', fontSize: 11.5 }}
            >
              新窗口打开
            </Button>
          )}
        </Stack>
      )}

      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            md: references.length > 0 ? '220px minmax(0, 1fr)' : '1fr',
          },
        }}
      >
        {references.length > 0 && (
          <Stack
            component='nav'
            aria-label='引用文档列表'
            gap={1}
            sx={{
              minHeight: 0,
              overflowY: 'auto',
              px: 0.5,
              py: 1,
              border: 0,
              bgcolor: 'transparent',
            }}
          >
            {references.map((reference, index) => {
              const active = reference.node_id === activeReferenceId;
              return (
                <Box
                  component='button'
                  type='button'
                  key={`${reference.node_id}-${index}`}
                  aria-label={`查看文档：${reference.name}`}
                  onClick={() => onSelect(reference)}
                  sx={{
                    width: '100%',
                    p: 1.35,
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 1,
                    borderRadius: '12px',
                    border: `1px solid ${active ? '#dedede' : 'transparent'}`,
                    bgcolor: active ? '#fff' : 'transparent',
                    boxShadow: active ? '0 8px 22px rgba(0,0,0,.055)' : 'none',
                    color: '#15171c',
                    font: 'inherit',
                    textAlign: 'left',
                    cursor: 'pointer',
                    transition: 'background .18s ease, box-shadow .18s ease',
                    '&:hover': { bgcolor: '#fff' },
                  }}
                >
                  <Box
                    sx={{
                      width: 28,
                      height: 28,
                      flexShrink: 0,
                      display: 'grid',
                      placeItems: 'center',
                      borderRadius: '8px',
                      bgcolor: active ? '#eeeeee' : '#e7e7e7',
                      color: '#666',
                      fontSize: 11,
                      fontWeight: 800,
                    }}
                  >
                    {index + 1}
                  </Box>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography
                      sx={{
                        display: '-webkit-box',
                        overflow: 'hidden',
                        WebkitBoxOrient: 'vertical',
                        WebkitLineClamp: 2,
                        fontSize: 12.5,
                        lineHeight: 1.45,
                        fontWeight: 650,
                      }}
                    >
                      {reference.name || `参考文档 ${index + 1}`}
                    </Typography>
                    <Typography
                      noWrap
                      sx={{ mt: 0.45, color: '#999da5', fontSize: 10.5 }}
                    >
                      {reference.node_release_id
                        ? `历史证据 · v${reference.knowledge_version || '—'}`
                        : '点击查看完整内容'}
                    </Typography>
                  </Box>
                </Box>
              );
            })}
          </Stack>
        )}

        <Box sx={{ minWidth: 0, minHeight: 0, position: 'relative' }}>
          {loading && (
            <Box
              sx={{
                position: 'absolute',
                inset: 0,
                zIndex: 2,
                display: 'grid',
                placeItems: 'center',
                bgcolor: alpha('#f7f8fa', 0.78),
                backdropFilter: 'blur(5px)',
              }}
            >
              <CircularProgress size={26} />
            </Box>
          )}

          {!activeReference && (
            <Stack
              alignItems='center'
              justifyContent='center'
              textAlign='center'
              sx={{ height: '100%', px: 4 }}
            >
              <Stack direction='row' alignItems='center' gap={1.7}>
                {[0, 1].map(index => (
                  <Box
                    key={index}
                    sx={{
                      position: 'relative',
                      width: 34,
                      height: 54,
                      border: '6px solid #050505',
                      borderRadius: '50%',
                      transform: `rotate(${index === 0 ? -4 : 4}deg)`,
                      '&::after': {
                        content: '""',
                        position: 'absolute',
                        width: 10,
                        height: 16,
                        top: 13,
                        left: index === 0 ? 8 : 2,
                        borderRadius: '50%',
                        bgcolor: '#050505',
                      },
                    }}
                  />
                ))}
              </Stack>
              <Typography
                sx={{ mt: 2.5, maxWidth: 420, color: '#969696', fontSize: 13 }}
              >
                回答引用的知识库文档会显示在这里。
              </Typography>
            </Stack>
          )}

          {activeReference && detail && (
            <Box
              sx={{
                height: { xs: '100%', md: 'calc(100% - 24px)' },
                overflowY: 'auto',
                m: { xs: 0, md: 1.5 },
                px: { xs: 2.5, md: 4.5 },
                py: { xs: 3, md: 4 },
                bgcolor: '#fff',
                borderRadius: { xs: 0, md: '18px' },
                boxShadow: { xs: 'none', md: '0 16px 44px rgba(0,0,0,.06)' },
              }}
            >
              <Stack direction='row' alignItems='center' gap={0.8}>
                <DescriptionOutlined sx={{ color: '#777', fontSize: 20 }} />
                <Typography sx={{ color: '#777c86', fontSize: 11.5 }}>
                  {activeReference.node_release_id
                    ? `引用时版本 · v${activeReference.knowledge_version || '—'}`
                    : '引用文档'}
                </Typography>
              </Stack>
              <Typography
                component='h2'
                sx={{
                  mt: 1.4,
                  fontSize: { xs: 23, md: 28 },
                  lineHeight: 1.25,
                  fontWeight: 750,
                  letterSpacing: '-0.025em',
                  color: '#111318',
                }}
              >
                {detail.name || activeReference.name}
              </Typography>
              {(detail.meta?.summary || activeReference.summary) && (
                <Typography
                  sx={{
                    mt: 1.2,
                    color: '#777c86',
                    fontSize: 13,
                    lineHeight: 1.7,
                  }}
                >
                  {detail.meta?.summary || activeReference.summary}
                </Typography>
              )}
              <Box
                sx={{
                  my: 3,
                  height: '1px',
                  bgcolor: 'rgba(17,28,48,.08)',
                }}
              />
              <Box
                sx={{
                  pb: 6,
                  color: '#25272d',
                  '& .tiptap.ProseMirror': {
                    p: 0,
                    fontSize: 14,
                    lineHeight: 1.85,
                  },
                }}
              >
                {editorRef.editor && <Editor editor={editorRef.editor} />}
              </Box>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
};

export default ReferenceWorkspace;
