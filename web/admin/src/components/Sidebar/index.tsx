import Logo from '@/assets/images/niuniu-avatar.jpg';
import { ConstsUserKBPermission } from '@/request/types';
import { useAppSelector } from '@/store';
import {
  IconChilun,
  IconDuihualishi1,
  IconGongxian,
  IconJushou,
  IconNeirongguanli,
  IconPaperFull,
  IconTongjifenxi1,
} from '@niuniu-wiki/icons';
import { alpha, Box, Button, Stack, Typography } from '@mui/material';
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import RouteRounded from '@mui/icons-material/RouteRounded';
import { useEffect, useMemo } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';

const MENUS = [
  {
    label: '文档',
    description: '内容与目录',
    value: '/',
    icon: IconNeirongguanli,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDocManage,
    ],
  },
  {
    label: '数据',
    description: '访问与趋势',
    value: '/stat',
    icon: IconTongjifenxi1,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDataOperate,
    ],
  },
  {
    label: '贡献',
    description: '共创与审核',
    value: '/contribution',
    icon: IconGongxian,
    perms: [ConstsUserKBPermission.UserKBPermissionFullControl],
  },
  {
    label: '编译',
    description: 'AI 知识演化',
    value: '/compiler',
    icon: AutoAwesomeRounded,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDocManage,
    ],
  },
  {
    label: '问答',
    description: 'AI 会话洞察',
    value: '/conversation',
    icon: IconDuihualishi1,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDataOperate,
    ],
  },
  {
    label: 'Agent',
    description: '自适应检索运行',
    value: '/agentic-rag',
    icon: RouteRounded,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDataOperate,
    ],
  },
  {
    label: '反馈',
    description: '评价与建议',
    value: '/feedback',
    icon: IconJushou,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDataOperate,
    ],
  },
  {
    label: '发布',
    description: '版本与上线',
    value: '/release',
    icon: IconPaperFull,
    perms: [
      ConstsUserKBPermission.UserKBPermissionFullControl,
      ConstsUserKBPermission.UserKBPermissionDocManage,
    ],
  },
  {
    label: '设置',
    description: '站点与集成',
    value: '/setting',
    icon: IconChilun,
    perms: [ConstsUserKBPermission.UserKBPermissionFullControl],
  },
];

const Sidebar = () => {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { kbDetail } = useAppSelector(state => state.config);
  const menus = useMemo(
    () => MENUS.filter(item => item.perms.includes(kbDetail.perm!)),
    [kbDetail.perm],
  );

  useEffect(() => {
    const canAccess = menus.some(item =>
      item.value === '/' ? pathname === '/' : pathname.startsWith(item.value),
    );
    if (!canAccess && menus.length > 0) navigate(menus[0].value);
  }, [menus, navigate, pathname]);

  return (
    <Stack
      component='aside'
      sx={{
        width: 220,
        m: 2,
        zIndex: 999,
        p: 1.5,
        height: 'calc(100vh - 32px)',
        bgcolor: alpha('#FFFFFF', 0.82),
        backdropFilter: 'saturate(180%) blur(24px)',
        border: '1px solid rgba(255, 255, 255, 0.78)',
        boxShadow: '0 20px 60px rgba(0, 0, 0, 0.08)',
        borderRadius: '24px',
        position: 'fixed',
        inset: '0 auto auto 0',
        overflow: 'hidden',
      }}
    >
      <Stack direction='row' alignItems='center' gap={1.5} sx={{ p: 1, mb: 1.5 }}>
        <Box
          component='img'
          src={Logo}
          alt='牛牛 Wiki'
          sx={{
            width: 42,
            height: 42,
            objectFit: 'cover',
            borderRadius: '13px',
            boxShadow: '0 6px 18px rgba(0, 0, 0, 0.14)',
          }}
        />
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ fontSize: 17, fontWeight: 700, letterSpacing: '-0.03em' }}>
            牛牛 Wiki
          </Typography>
          <Typography sx={{ mt: 0.15, fontSize: 11, color: 'text.tertiary' }}>
            AI KNOWLEDGE OS
          </Typography>
        </Box>
      </Stack>

      <Typography sx={{ px: 1.5, mb: 0.75, fontSize: 11, color: 'text.disabled' }}>
        工作台
      </Typography>
      <Stack component='nav' sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }} gap={0.5}>
        {menus.map(item => {
          const active = item.value === '/' ? pathname === '/' : pathname.startsWith(item.value);
          const IconMenu = item.icon;
          return (
            <NavLink key={item.value} to={item.value}>
              <Button
                fullWidth
                sx={{
                  height: 52,
                  px: 1.25,
                  justifyContent: 'flex-start',
                  color: active ? 'primary.main' : 'text.primary',
                  bgcolor: active ? 'rgba(0, 122, 255, 0.1)' : 'transparent',
                  border: active
                    ? '1px solid rgba(0, 122, 255, 0.12)'
                    : '1px solid transparent',
                  boxShadow: 'none !important',
                  transform: 'none !important',
                  '&:hover': { bgcolor: active ? 'rgba(0, 122, 255, 0.13)' : 'rgba(0, 0, 0, 0.035)' },
                }}
              >
                <Stack direction='row' alignItems='center' gap={1.25}>
                  <Stack
                    alignItems='center'
                    justifyContent='center'
                    sx={{
                      width: 30,
                      height: 30,
                      borderRadius: '9px',
                      color: active ? '#fff' : 'text.tertiary',
                      bgcolor: active ? 'primary.main' : 'rgba(0, 0, 0, 0.045)',
                    }}
                  >
                    <IconMenu sx={{ fontSize: 15 }} />
                  </Stack>
                  <Box sx={{ textAlign: 'left' }}>
                    <Typography sx={{ fontSize: 14, fontWeight: active ? 600 : 500, lineHeight: 1.2 }}>
                      {item.label}
                    </Typography>
                    <Typography sx={{ mt: 0.3, fontSize: 10.5, color: active ? 'primary.main' : 'text.disabled' }}>
                      {item.description}
                    </Typography>
                  </Box>
                </Stack>
              </Button>
            </NavLink>
          );
        })}
      </Stack>

      <Stack
        direction='row'
        alignItems='center'
        gap={1}
        sx={{
          p: 1.25,
          mt: 1,
          borderRadius: '14px',
          bgcolor: 'rgba(0, 0, 0, 0.035)',
          border: '1px solid rgba(60, 60, 67, 0.07)',
        }}
      >
        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#34C759', boxShadow: '0 0 0 4px rgba(52, 199, 89, 0.12)' }} />
        <Box>
          <Typography sx={{ fontSize: 12, fontWeight: 600 }}>服务运行正常</Typography>
          <Typography sx={{ fontSize: 10.5, color: 'text.tertiary' }}>牛牛 Wiki · 本地工作区</Typography>
        </Box>
      </Stack>
    </Stack>
  );
};

export default Sidebar;
