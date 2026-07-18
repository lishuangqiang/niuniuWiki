'use client';

import { ConstsCopySetting, V1ShareNodeDetailResp } from '@/request/types';
import { Box, IconButton, Stack, Tooltip } from '@mui/material';
import { IconFuzhi, IconWenjian, IconWenjianjia } from '@niuniu-wiki/icons';
import dayjs from 'dayjs';
import type { UseTiptapReturn } from '@ctzhian/tiptap';
import { copyText } from '@/utils';

interface DocMetaInfoProps {
  info: V1ShareNodeDetailResp;
  characterCount?: number;
  kbDetailCopySetting?: string;
  onCopyDocMd: () => void;
}

const DocMetaInfo = ({
  info,
  characterCount,
  kbDetailCopySetting,
  onCopyDocMd,
}: DocMetaInfoProps) => (
  <>
    <Stack
      direction='row'
      alignItems='flex-start'
      gap={1}
      sx={{
        fontSize: { xs: 30, md: 42 },
        lineHeight: { xs: 1.15, md: 1.08 },
        fontWeight: 720,
        letterSpacing: '-0.045em',
        color: 'text.primary',
        mb: 2,
      }}
    >
      {info?.meta?.emoji ? (
        <Box sx={{ flexShrink: 0 }}>{info?.meta?.emoji}</Box>
      ) : info?.type === 1 ? (
        <IconWenjianjia sx={{ flexShrink: 0, mt: 0.5 }} />
      ) : (
        <IconWenjian sx={{ flexShrink: 0, mt: 0.5 }} />
      )}
      {info?.name}
    </Stack>
    <Stack
      direction='row'
      justifyContent='space-between'
      alignItems='center'
      sx={{ mb: { xs: 4, md: 5.5 }, gap: 2 }}
    >
      <Stack
        direction='row'
        alignItems='center'
        gap={1}
        flexWrap='wrap'
        sx={{
          fontSize: { xs: 12, md: 13 },
          color: 'text.tertiary',
          lineHeight: 1.6,
        }}
      >
        {info?.created_at && (
          <Box>
            {info?.creator_account === 'admin'
              ? '管理员'
              : info?.creator_account}{' '}
            {dayjs(info?.created_at).fromNow()}创建
          </Box>
        )}
        {info?.updated_at && info.updated_at.slice(0, 1) !== '0' && (
          <>
            <Box>·</Box>
            <Box>
              {info?.publisher_account === 'admin'
                ? '管理员'
                : info?.publisher_account}{' '}
              {dayjs(info.updated_at).fromNow()}更新
            </Box>
          </>
        )}
        {!!characterCount && characterCount > 0 && (
          <>
            <Box>·</Box>
            <Box>{characterCount} 字</Box>
          </>
        )}
        {(info.pv ?? 0) > 0 && (
          <>
            <Box>·</Box>
            <Box>浏览量 {info.pv}</Box>
          </>
        )}
      </Stack>
      {info?.type === 2 &&
        kbDetailCopySetting !== ConstsCopySetting.CopySettingDisabled && (
          <Tooltip title='复制 MarkDown 格式' arrow placement='top'>
            <IconButton
              size='small'
              onClick={onCopyDocMd}
              sx={theme => ({
                width: 38,
                height: 38,
                flexShrink: 0,
                bgcolor: theme.palette.action.hover,
                border: '1px solid',
                borderColor: 'divider',
              })}
            >
              <IconFuzhi sx={{ fontSize: 16 }} />
            </IconButton>
          </Tooltip>
        )}
    </Stack>
  </>
);

export default DocMetaInfo;
