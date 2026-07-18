import { getApiV1KnowledgeBaseDetail } from '@/request/KnowledgeBase';
import { useAppDispatch, useAppSelector } from '@/store';
import { setKbDetail } from '@/store/slices/config';
import LogoutRounded from '@mui/icons-material/LogoutRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import { alpha, Button, Divider, IconButton, Stack, Tooltip } from '@mui/material';
import { message, Modal } from '@ctzhian/ui';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import System from '../System';
import Bread from './Bread';

const Header = () => {
  const navigate = useNavigate();
  const { kb_id } = useAppSelector(state => state.config);
  const dispatch = useAppDispatch();
  const [wikiUrl, setWikiUrl] = useState('');
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  useEffect(() => {
    if (!kb_id) return;
    getApiV1KnowledgeBaseDetail({ id: kb_id }).then(res => {
      dispatch(setKbDetail(res));
      if (res.access_settings?.base_url) {
        setWikiUrl(res.access_settings.base_url);
        return;
      }
      const host = res.access_settings?.hosts?.[0] || '';
      if (!host) return setWikiUrl('');
      const sslPorts = res.access_settings?.ssl_ports || [];
      const ports = res.access_settings?.ports || [];
      if (sslPorts.length) {
        setWikiUrl(sslPorts.includes(443) ? `https://${host}` : `https://${host}:${sslPorts[0]}`);
      } else if (ports.length) {
        setWikiUrl(ports.includes(80) ? `http://${host}` : `http://${host}:${ports[0]}`);
      }
    });
  }, [dispatch, kb_id]);

  return (
    <Stack
      component='header'
      direction='row'
      alignItems='center'
      justifyContent='space-between'
      sx={{
        minWidth: 1040,
        position: 'fixed',
        left: 252,
        top: 0,
        px: 3,
        height: 72,
        zIndex: 998,
        width: 'calc(100% - 252px)',
        bgcolor: alpha('#F5F5F7', 0.78),
        backdropFilter: 'saturate(180%) blur(22px)',
        borderBottom: '1px solid rgba(60, 60, 67, 0.08)',
      }}
    >
      <Bread />
      <Stack direction='row' alignItems='center' gap={1}>
        <Button
          size='small'
          variant='contained'
          disabled={!wikiUrl}
          endIcon={<OpenInNewRounded sx={{ fontSize: '15px !important' }} />}
          onClick={() => wikiUrl && window.open(wikiUrl, '_blank', 'noopener,noreferrer')}
          sx={{ px: 1.75, bgcolor: '#1D1D1F', '&:hover': { bgcolor: '#000' } }}
        >
          访问站点
        </Button>
        <System />
        <Divider orientation='vertical' flexItem sx={{ mx: 0.5, my: 0.75 }} />
        <Tooltip arrow title='退出登录'>
          <IconButton
            size='small'
            onClick={() => setLogoutConfirmOpen(true)}
            sx={{
              width: 36,
              height: 36,
              color: 'text.tertiary',
              bgcolor: alpha('#FFFFFF', 0.72),
              border: '1px solid rgba(60, 60, 67, 0.1)',
              '&:hover': { color: 'error.main', bgcolor: 'rgba(255, 59, 48, 0.08)' },
            }}
          >
            <LogoutRounded sx={{ fontSize: 18 }} />
          </IconButton>
        </Tooltip>
      </Stack>
      <Modal
        open={logoutConfirmOpen}
        onCancel={() => setLogoutConfirmOpen(false)}
        onOk={() => {
          localStorage.removeItem('niuniu_wiki_token');
          message.success('已安全退出');
          setTimeout(() => navigate('/login'), 400);
        }}
        title='退出牛牛 Wiki？'
        cancelText='取消'
        okText='退出'
        okButtonProps={{ color: 'error', variant: 'contained' }}
      />
    </Stack>
  );
};

export default Header;
