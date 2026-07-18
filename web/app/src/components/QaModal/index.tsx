'use client';

import { ChunkResultItem } from '@/assets/type';
import Logo from '@/assets/images/niuniu-avatar.jpg';
import { useBasePath } from '@/hooks';
import { useStore } from '@/provider';
import { getImagePath } from '@/utils/getImagePath';
import AddRounded from '@mui/icons-material/AddRounded';
import FolderOpenRounded from '@mui/icons-material/FolderOpenRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import KeyboardTabRounded from '@mui/icons-material/KeyboardTabRounded';
import {
  Box,
  Button,
  IconButton,
  Modal,
  Stack,
  Typography,
} from '@mui/material';
import { useSearchParams } from 'next/navigation';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import AiQaContent from './AiQaContent';
import ReferenceWorkspace from './ReferenceWorkspace';
import SearchDocContent from './SearchDocContent';

interface SearchSuggestion {
  id: string;
  title: string;
  description?: string;
  type?: 'recent' | 'suggestion' | 'trending';
}

interface QaModalProps {
  placeholder?: string;
  initialValue?: string;
  onSearch?: (value?: string, type?: 'search' | 'chat') => void;
  onSearchSuggestions?: (query: string) => Promise<SearchSuggestion[]>;
  defaultSuggestions?: SearchSuggestion[];
}

const QaModal: React.FC<QaModalProps> = () => {
  const { qaModalOpen, setQaModalOpen, kbDetail } = useStore();
  const [searchMode, setSearchMode] = useState<'chat' | 'search'>('chat');
  const [references, setReferences] = useState<ChunkResultItem[]>([]);
  const [activeReferenceId, setActiveReferenceId] = useState<string>();
  const [mobileReferenceOpen, setMobileReferenceOpen] = useState(false);
  const [newConversationToken, setNewConversationToken] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const aiQaInputRef = useRef<HTMLInputElement>(null);
  const searchParams = useSearchParams();
  const basePath = useBasePath();
  const brandLogo = getImagePath(
    kbDetail?.settings?.icon || Logo.src,
    basePath,
  );

  const onClose = () => {
    setQaModalOpen?.(false);
    setMobileReferenceOpen(false);
  };

  const placeholder = useMemo(
    () =>
      kbDetail?.settings?.web_app_custom_style?.header_search_placeholder ||
      '搜索...',
    [kbDetail],
  );

  const hotSearch = useMemo(() => {
    const bannerConfig = kbDetail?.settings?.web_app_landing_configs?.find(
      item => item.type === 'banner',
    );
    return bannerConfig?.banner_config?.hot_search || [];
  }, [kbDetail]);

  const handleReferencesChange = useCallback((next: ChunkResultItem[]) => {
    setReferences(next);
    setActiveReferenceId(current => {
      if (current && next.some(item => item.node_id === current))
        return current;
      return next[0]?.node_id;
    });
  }, []);

  const handleReferenceSelect = useCallback(
    (reference: ChunkResultItem, source?: ChunkResultItem[]) => {
      if (source) setReferences(source);
      setActiveReferenceId(reference.node_id);
      setMobileReferenceOpen(true);
    },
    [],
  );

  useEffect(() => {
    if (!qaModalOpen) return;
    setTimeout(() => {
      if (searchMode === 'chat') {
        aiQaInputRef.current?.querySelector('textarea')?.focus();
      } else {
        inputRef.current?.querySelector('input')?.focus();
      }
    }, 100);
  }, [qaModalOpen, searchMode]);

  useEffect(() => {
    if (!qaModalOpen) {
      setTimeout(() => setSearchMode('chat'), 300);
    }
  }, [qaModalOpen]);

  useEffect(() => {
    const cid = searchParams.get('cid');
    const ask = searchParams.get('ask');
    if (cid || ask) setQaModalOpen?.(true);
  }, []);

  return (
    <Modal
      open={qaModalOpen as boolean}
      onClose={onClose}
      sx={{
        display: 'flex',
        p: 0,
        '& .MuiBackdrop-root': {
          backgroundColor: '#f2f2f2',
          backdropFilter: 'none',
        },
      }}
    >
      <Box
        sx={{
          width: '100vw',
          height: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          outline: 'none',
          bgcolor: '#f2f2f2',
        }}
        onClick={event => event.stopPropagation()}
      >
        <Stack
          component='header'
          direction='row'
          alignItems='center'
          justifyContent='space-between'
          sx={{
            height: 62,
            flexShrink: 0,
            px: { xs: 1.5, sm: 2.5 },
            bgcolor: 'rgba(255,255,255,.92)',
            borderBottom: '1px solid rgba(17,28,48,.07)',
            backdropFilter: 'blur(18px)',
          }}
        >
          <Stack direction='row' alignItems='center' gap={0.7}>
            <Typography sx={{ mr: 2, fontSize: 14, fontWeight: 750 }}>
              牛牛问答
            </Typography>
            <IconButton
              aria-label='返回首页'
              onClick={onClose}
              sx={{ width: 34, height: 34 }}
            >
              <HomeRounded sx={{ fontSize: 18 }} />
            </IconButton>
            <IconButton
              aria-label='查看历史消息'
              onClick={onClose}
              sx={{ width: 34, height: 34, color: '#a3a6ad' }}
            >
              <HistoryRounded sx={{ fontSize: 19 }} />
            </IconButton>
            <IconButton
              aria-label={searchMode === 'chat' ? '搜索文档' : '返回智能问答'}
              onClick={() =>
                setSearchMode(value => (value === 'chat' ? 'search' : 'chat'))
              }
              sx={{
                width: 34,
                height: 34,
                bgcolor: searchMode === 'search' ? '#f0f0f0' : 'transparent',
              }}
            >
              <FolderOpenRounded sx={{ fontSize: 19 }} />
            </IconButton>
          </Stack>
          <Stack direction='row' alignItems='center' gap={1}>
            <Button
              variant='outlined'
              onClick={() =>
                void navigator.clipboard?.writeText(window.location.href)
              }
              sx={{
                minWidth: 104,
                height: 34,
                color: '#777',
                borderColor: '#e5e5e5',
                fontSize: 12,
                boxShadow: 'none',
              }}
            >
              分享
            </Button>
            <Button
              variant='contained'
              onClick={() => window.print()}
              disableElevation
              sx={{
                minWidth: 104,
                height: 34,
                bgcolor: '#8d8d8d',
                color: '#fff',
                fontSize: 12,
                '&:hover': { bgcolor: '#767676' },
              }}
            >
              导出
            </Button>
            <Box
              component='img'
              src={brandLogo}
              alt='牛牛 Wiki'
              sx={{
                width: 34,
                height: 34,
                objectFit: 'cover',
                borderRadius: '50%',
              }}
            />
          </Stack>
        </Stack>

        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'grid',
            gridTemplateColumns: {
              xs: '1fr',
              lg:
                searchMode === 'chat'
                  ? 'minmax(440px, 45%) minmax(0, 55%)'
                  : 'minmax(0, 920px)',
            },
            justifyContent: searchMode === 'search' ? 'center' : 'stretch',
            gap: { xs: 0, lg: 2 },
            p: { xs: 0, lg: 2 },
          }}
        >
          <Box
            sx={{
              minWidth: 0,
              minHeight: 0,
              display: {
                xs:
                  searchMode === 'chat' && mobileReferenceOpen
                    ? 'none'
                    : 'flex',
                lg: 'flex',
              },
              flexDirection: 'column',
              overflow: 'hidden',
              borderRadius: { xs: 0, lg: '22px' },
              border: 0,
              bgcolor: '#fff',
              boxShadow: { xs: 'none', lg: '0 18px 55px rgba(0,0,0,.06)' },
            }}
          >
            <Stack
              direction='row'
              alignItems='center'
              justifyContent='space-between'
              gap={1}
              sx={{
                flexShrink: 0,
                height: 88,
                px: { xs: 2, sm: 3 },
                borderBottom: '1px solid #ededed',
              }}
            >
              <Stack direction='row' alignItems='center' gap={1.2}>
                <Box
                  component='img'
                  src={brandLogo}
                  alt='牛牛 Wiki'
                  sx={{
                    width: 38,
                    height: 38,
                    objectFit: 'cover',
                    borderRadius: '50%',
                  }}
                />
                <IconButton
                  aria-label='新会话'
                  onClick={() => {
                    setNewConversationToken(value => value + 1);
                    setReferences([]);
                    setActiveReferenceId(undefined);
                  }}
                  sx={{
                    width: 42,
                    height: 42,
                    border: '1px dashed #cdd0d5',
                    color: '#445064',
                  }}
                >
                  <AddRounded sx={{ fontSize: 22 }} />
                </IconButton>
              </Stack>
              <IconButton
                aria-label='收起问答'
                onClick={onClose}
                sx={{ color: '#41506a' }}
              >
                <KeyboardTabRounded sx={{ fontSize: 21 }} />
              </IconButton>
            </Stack>

            <Box
              sx={{
                px: { xs: 2, sm: 3 },
                pt: 2,
                pb: 1.5,
                flex: 1,
                minHeight: 0,
                display: searchMode === 'chat' ? 'flex' : 'none',
                flexDirection: 'column',
              }}
            >
              <AiQaContent
                hotSearch={hotSearch}
                placeholder={placeholder}
                inputRef={aiQaInputRef}
                activeReferenceId={activeReferenceId}
                onReferencesChange={handleReferencesChange}
                onReferenceSelect={handleReferenceSelect}
                newConversationToken={newConversationToken}
              />
            </Box>
            <Box
              sx={{
                px: { xs: 2, sm: 3 },
                py: 2,
                flex: 1,
                minHeight: 0,
                display: searchMode === 'search' ? 'flex' : 'none',
                flexDirection: 'column',
              }}
            >
              <SearchDocContent inputRef={inputRef} placeholder={placeholder} />
            </Box>

            <Typography
              sx={{
                flexShrink: 0,
                pb: 1.5,
                px: 2,
                color: '#b0b3ba',
                fontSize: 10.5,
                textAlign: 'center',
              }}
            >
              {!kbDetail?.settings?.conversation_setting
                ?.copyright_hide_enabled &&
                (kbDetail?.settings?.conversation_setting?.copyright_info ||
                  '本回答由 牛牛 Wiki AI 生成，请结合原文判断。')}
            </Typography>
          </Box>

          {searchMode === 'chat' && (
            <Box
              sx={{
                minWidth: 0,
                minHeight: 0,
                display: {
                  xs: mobileReferenceOpen ? 'block' : 'none',
                  lg: 'block',
                },
              }}
            >
              <ReferenceWorkspace
                references={references}
                activeReferenceId={activeReferenceId}
                onSelect={reference => handleReferenceSelect(reference)}
                onMobileBack={() => setMobileReferenceOpen(false)}
              />
            </Box>
          )}
        </Box>
      </Box>
    </Modal>
  );
};

export default QaModal;
