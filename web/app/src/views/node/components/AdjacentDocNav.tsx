'use client';

import { IconMianbaoxie } from '@niuniu-wiki/icons';
import { Box, Stack } from '@mui/material';
import Link from 'next/link';
import type { ITreeItem } from '@/assets/type';

interface AdjacentDocNavProps {
  prev?: ITreeItem;
  next?: ITreeItem;
  basePath: string;
  hasCommentSection?: boolean;
}

const AdjacentDocNav = ({
  prev,
  next,
  basePath,
  hasCommentSection = false,
}: AdjacentDocNavProps) => {
  if (!prev && !next) return null;

  return (
    <Stack
      direction='row'
      justifyContent='space-between'
      alignItems='center'
      sx={{
        mt: { xs: 5, md: 7 },
        mb: hasCommentSection ? 0 : 2,
        gap: { xs: 1, md: 2 },
      }}
    >
      {prev ? (
        <Box
          component={Link}
          href={`${basePath}/node/${prev.id}`}
          sx={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            minHeight: 76,
            p: { xs: 1.5, md: 2 },
            color: 'text.secondary',
            bgcolor: 'background.paper3',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: '16px',
            textDecoration: 'none',
            maxWidth: '48%',
            '&:hover': {
              color: 'primary.main',
              transform: 'translateY(-2px)',
              boxShadow: '0 10px 28px rgba(0,0,0,.06)',
            },
            transition: 'all 0.3s cubic-bezier(.22,1,.36,1)',
          }}
        >
          <IconMianbaoxie
            sx={{ flexShrink: 0, fontSize: 14, transform: 'rotate(180deg)' }}
          />
          <Box
            sx={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              textAlign: 'left',
            }}
          >
            上一篇：{prev.name}
          </Box>
        </Box>
      ) : (
        <Box sx={{ flex: 1 }} />
      )}
      {next ? (
        <Box
          component={Link}
          href={`${basePath}/node/${next.id}`}
          sx={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 1,
            minHeight: 76,
            p: { xs: 1.5, md: 2 },
            color: 'text.secondary',
            bgcolor: 'background.paper3',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: '16px',
            textDecoration: 'none',
            maxWidth: '48%',
            '&:hover': {
              color: 'primary.main',
              transform: 'translateY(-2px)',
              boxShadow: '0 10px 28px rgba(0,0,0,.06)',
            },
            transition: 'all 0.3s cubic-bezier(.22,1,.36,1)',
          }}
        >
          <Box
            sx={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              textAlign: 'right',
            }}
          >
            下一篇：{next.name}
          </Box>
          <IconMianbaoxie sx={{ flexShrink: 0, fontSize: 14 }} />
        </Box>
      ) : (
        <Box sx={{ flex: 1 }} />
      )}
    </Stack>
  );
};

export default AdjacentDocNav;
