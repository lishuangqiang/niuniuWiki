'use client';

import Logo from '@/assets/images/niuniu-avatar.jpg';
import { useBasePath } from '@/hooks';
import { useStore } from '@/provider';
import { postShareProV1AuthLogout } from '@/request/pro/ShareAuth';
import { getImagePath } from '@/utils/getImagePath';
import { Modal } from '@ctzhian/ui';
import ErrorIcon from '@mui/icons-material/Error';
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import HelpOutlineRounded from '@mui/icons-material/HelpOutlineRounded';
import { alpha, Box, Button, IconButton, Stack, Tooltip } from '@mui/material';
import { IconDengchu } from '@niuniu-wiki/icons';
import {
  Header as CustomHeader,
  WelcomeHeader as WelcomeHeaderComponent,
} from '@niuniu-wiki/ui';
import { useMemo, useState } from 'react';
import QaModal from '../QaModal';
import ThemeSwitch from './themeSwitch';
interface HeaderProps {
  isDocPage?: boolean;
  isWelcomePage?: boolean;
}

const LogoutButton = () => {
  const [open, setOpen] = useState(false);
  const handleLogout = () => {
    return postShareProV1AuthLogout().then(() => {
      // 使用当前页面的协议（http 或 https）
      const protocol = window.location.protocol;
      const host = window.location.host;
      window.location.href = `${protocol}//${host}/auth/login`;
    });
  };
  return (
    <>
      <Modal
        title={
          <Stack direction='row' alignItems='center' gap={1}>
            <ErrorIcon sx={{ fontSize: 24, color: 'warning.main' }} />
            <Box sx={{ mt: '2px' }}>提示</Box>
          </Stack>
        }
        open={open}
        okText='确定'
        cancelText='取消'
        onCancel={() => setOpen(false)}
        onOk={handleLogout}
        closable={false}
      >
        <Box sx={{ pl: 4 }}>确定要退出登录吗？</Box>
      </Modal>
      <Tooltip title='退出登录' arrow>
        <IconButton size='small' onClick={() => setOpen(true)}>
          <IconDengchu
            sx={theme => ({
              cursor: 'pointer',
              color: alpha(theme.palette.text.primary, 0.65),
              fontSize: 24,
              '&:hover': { color: theme.palette.primary.main },
            })}
          />
        </IconButton>
      </Tooltip>
    </>
  );
};

const Header = ({ isDocPage = false, isWelcomePage = false }: HeaderProps) => {
  const {
    mobile = false,
    kbDetail,
    catalogWidth,
    setQaModalOpen,
    authInfo,
  } = useStore();
  const basePath = useBasePath();
  const brandTitle = kbDetail?.settings?.title || '牛牛 Wiki';
  const configuredIcon = kbDetail?.settings?.icon || '';
  const brandLogo = configuredIcon || Logo.src;
  const docWidth = useMemo(() => {
    if (isWelcomePage) return 'full';
    return kbDetail?.settings?.theme_and_style?.doc_width || 'full';
  }, [kbDetail, isWelcomePage]);

  const handleSearch = (value?: string, type: 'chat' | 'search' = 'chat') => {
    if (value?.trim()) {
      if (type === 'chat') {
        sessionStorage.setItem('chat_search_query', value.trim());
        setQaModalOpen?.(true);
      } else {
        sessionStorage.setItem('chat_search_query', value.trim());
      }
    }
  };

  return (
    <CustomHeader
      isDocPage={isDocPage}
      mobile={mobile}
      docWidth={docWidth}
      catalogWidth={catalogWidth}
      logo={getImagePath(brandLogo, basePath)}
      title={brandTitle}
      placeholder={
        kbDetail?.settings?.web_app_custom_style?.header_search_placeholder
      }
      showSearch
      homePath={basePath || '/'}
      btns={
        kbDetail?.settings?.btns?.map((item: any) => ({
          ...item,
          url: getImagePath(item.url, basePath),
          icon: getImagePath(item.icon, basePath),
        })) || []
      }
      onSearch={handleSearch}
      onQaClick={() => setQaModalOpen?.(true)}
    >
      <Stack sx={{ ml: 2 }} direction='row' alignItems='center' gap={1}>
        <ThemeSwitch />
        {!!authInfo && <LogoutButton />}
      </Stack>
      <QaModal />
    </CustomHeader>
  );
};

export const WelcomeHeader = ({
  showSearch = true,
}: {
  showSearch?: boolean;
}) => {
  const basePath = useBasePath();
  const {
    mobile = false,
    kbDetail,
    catalogWidth,
    setQaModalOpen,
    authInfo,
  } = useStore();
  const brandTitle = kbDetail?.settings?.title || '牛牛 Wiki';
  const configuredIcon = kbDetail?.settings?.icon || '';
  const brandLogo = configuredIcon || Logo.src;
  const handleSearch = (value?: string, type: 'chat' | 'search' = 'chat') => {
    if (value?.trim()) {
      if (type === 'chat') {
        sessionStorage.setItem('chat_search_query', value.trim());
        setQaModalOpen?.(true);
      } else {
        sessionStorage.setItem('chat_search_query', value.trim());
      }
    }
  };
  return (
    <WelcomeHeaderComponent
      isDocPage={false}
      mobile={mobile}
      docWidth='full'
      catalogWidth={catalogWidth}
      logo={getImagePath(brandLogo, basePath)}
      title={brandTitle}
      placeholder={
        kbDetail?.settings?.web_app_custom_style?.header_search_placeholder
      }
      showSearch={showSearch}
      homePath={basePath || '/'}
      seamless={!showSearch}
      btns={
        kbDetail?.settings?.btns?.map((item: any) => ({
          ...item,
          url: getImagePath(item.url, basePath),
          icon: getImagePath(item.icon, basePath),
        })) || []
      }
      onSearch={handleSearch}
      onQaClick={() => setQaModalOpen?.(true)}
    >
      <Stack direction='row' alignItems='center' gap={0.5}>
        <Tooltip title='使用帮助'>
          <IconButton
            aria-label='使用帮助'
            size='small'
            onClick={() => setQaModalOpen?.(true)}
            sx={{ width: 34, height: 34, color: 'text.primary' }}
          >
            <HelpOutlineRounded sx={{ fontSize: 19 }} />
          </IconButton>
        </Tooltip>
        {!mobile && (
          <Button
            variant='text'
            startIcon={<AutoAwesomeRounded sx={{ fontSize: 15 }} />}
            onClick={() => setQaModalOpen?.(true)}
            sx={{
              minHeight: 34,
              px: 1.25,
              color: 'text.primary',
              fontSize: 12,
              '&:hover': { bgcolor: 'rgba(0,0,0,.04)', boxShadow: 'none' },
            }}
          >
            AI 问答
          </Button>
        )}
        <ThemeSwitch />
      </Stack>
      {!!authInfo && (
        <Box sx={{ ml: 2 }}>
          <LogoutButton />
        </Box>
      )}
      <QaModal />
    </WelcomeHeaderComponent>
  );
};

export default Header;
