'use client';
import { V1NodeDetailResp } from '@/request/types';
import { Ellipsis } from '@ctzhian/ui';
import { Box, Button, Skeleton, Stack } from '@mui/material';
import { IconBaocun, IconMuluzhankai } from '@niuniu-wiki/icons';
import dayjs from 'dayjs';
import { useEffect, useRef } from 'react';
import { useWrapContext } from '..';

interface HeaderProps {
  detail: V1NodeDetailResp;
  handleSave: () => void;
}

const Header = ({ detail, handleSave }: HeaderProps) => {
  const firstLoad = useRef(true);

  const { catalogOpen, nodeDetail, setCatalogOpen, saveLoading } =
    useWrapContext();

  useEffect(() => {
    firstLoad.current = false;
  }, [nodeDetail?.updated_at]);

  return (
    <Box sx={{ px: { xs: 1.5, md: 2.5 }, py: 1.25 }}>
      <Stack
        direction={'row'}
        alignItems={'center'}
        gap={1}
        justifyContent={'space-between'}
        sx={{ height: '42px' }}
      >
        {!catalogOpen && (
          <Stack
            alignItems='center'
            justifyContent='space-between'
            onClick={() => setCatalogOpen(true)}
            sx={{
              cursor: 'pointer',
              color: 'text.tertiary',
              ':hover': {
                color: 'text.primary',
              },
            }}
          >
            <IconMuluzhankai
              sx={{
                fontSize: 24,
              }}
            />
          </Stack>
        )}
        <Stack sx={{ width: 0, flex: 1 }}>
          {detail?.name ? (
            <Ellipsis sx={{ fontSize: 14, fontWeight: 650 }}>
              <Box
                component='span'
                sx={{ cursor: 'pointer' }}
                // onClick={() => setRenameOpen(true)}
              >
                {detail.name}
              </Box>
            </Ellipsis>
          ) : // <Skeleton variant='text' width={300} height={24} />
          null}
          {nodeDetail?.updated_at && (
            <Stack
              direction={'row'}
              alignItems={'center'}
              gap={0.5}
              sx={{ fontSize: 12, color: 'text.tertiary' }}
            >
              <IconBaocun sx={{ fontSize: 12 }} />
              {nodeDetail?.updated_at ? (
                dayjs(nodeDetail.updated_at).format('YYYY-MM-DD HH:mm:ss')
              ) : (
                <Skeleton variant='text' width={100} height={24} />
              )}
            </Stack>
          )}
        </Stack>

        <Stack direction={'row'} gap={2}>
          <Button
            size='small'
            variant='contained'
            disabled={!detail.name || saveLoading}
            startIcon={<IconBaocun />}
            onClick={handleSave}
            sx={{ px: 2.25, minWidth: 96 }}
          >
            保存
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
};

export default Header;
