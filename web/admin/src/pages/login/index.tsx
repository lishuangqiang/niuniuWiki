import Logo from '@/assets/images/niuniu-avatar.jpg';
import { useURLSearchParams } from '@/hooks';
import { postApiV1UserLogin } from '@/request/User';
import {
  IconBukejian,
  IconIcon_tool_close,
  IconKejian,
  IconMima,
  IconZhanghao,
} from '@niuniu-wiki/icons';
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import LibraryBooksRounded from '@mui/icons-material/LibraryBooksRounded';
import { alpha, Box, Button, IconButton, Stack, TextField, Typography } from '@mui/material';
import { message } from '@ctzhian/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const Login = () => {
  const navigate = useNavigate();
  const [searchParams] = useURLSearchParams();
  const redirect = searchParams.get('redirect') || '/';
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const submit = () => {
    if (!account.trim() || !password) return;
    setLoading(true);
    postApiV1UserLogin({ account: account.trim(), password })
      .then(res => {
        localStorage.setItem('niuniu_wiki_token', res.token!);
        message.success('欢迎回来');
        navigate(redirect);
      })
      .finally(() => setLoading(false));
  };

  return (
    <Box
      sx={{
        width: '100vw',
        minWidth: 960,
        minHeight: '100vh',
        overflow: 'hidden',
        position: 'relative',
        bgcolor: '#F5F5F7',
        background:
          'radial-gradient(circle at 12% 18%, rgba(0,122,255,.16), transparent 28%), radial-gradient(circle at 82% 72%, rgba(175,82,222,.12), transparent 32%), #F5F5F7',
      }}
    >
      <Stack direction='row' alignItems='stretch' sx={{ minHeight: '100vh', p: 2 }}>
        <Stack
          justifyContent='space-between'
          sx={{
            flex: 1.12,
            p: 7,
            borderRadius: '30px',
            color: '#fff',
            overflow: 'hidden',
            position: 'relative',
            background: 'linear-gradient(145deg, #111214 0%, #24262B 58%, #10263D 100%)',
            boxShadow: '0 30px 80px rgba(0, 0, 0, 0.16)',
          }}
        >
          <Box
            sx={{
              position: 'absolute',
              width: 420,
              height: 420,
              right: -120,
              top: -150,
              borderRadius: '50%',
              background: 'radial-gradient(circle, rgba(0,122,255,.55), transparent 64%)',
              filter: 'blur(10px)',
            }}
          />
          <Stack direction='row' alignItems='center' gap={1.5} sx={{ position: 'relative' }}>
            <Box component='img' src={Logo} alt='牛牛 Wiki' sx={{ width: 48, height: 48, objectFit: 'cover', borderRadius: '15px', boxShadow: '0 10px 24px rgba(0,0,0,.28)' }} />
            <Box>
              <Typography sx={{ fontSize: 19, fontWeight: 700, letterSpacing: '-0.03em' }}>牛牛 Wiki</Typography>
              <Typography sx={{ fontSize: 11, color: 'rgba(255,255,255,.52)', letterSpacing: '.12em' }}>KNOWLEDGE, BEAUTIFULLY ORGANIZED</Typography>
            </Box>
          </Stack>

          <Box sx={{ position: 'relative', maxWidth: 620 }}>
            <Typography sx={{ fontSize: 58, lineHeight: 1.02, fontWeight: 700, letterSpacing: '-0.055em' }}>
              让知识被看见，
              <br />也被真正理解。
            </Typography>
            <Typography sx={{ mt: 3, maxWidth: 500, fontSize: 17, lineHeight: 1.8, color: 'rgba(255,255,255,.62)' }}>
              将文档、智能检索与 AI 问答放进同一个安静、清晰的工作空间。
            </Typography>
            <Stack direction='row' gap={1.5} sx={{ mt: 5 }}>
              {[
                { icon: LibraryBooksRounded, label: '结构化知识' },
                { icon: AutoAwesomeRounded, label: 'AI 驱动问答' },
              ].map(item => (
                <Stack key={item.label} direction='row' alignItems='center' gap={1} sx={{ px: 1.75, py: 1.1, borderRadius: '999px', bgcolor: 'rgba(255,255,255,.08)', border: '1px solid rgba(255,255,255,.1)' }}>
                  <item.icon sx={{ fontSize: 16, color: '#64D2FF' }} />
                  <Typography sx={{ fontSize: 12.5, color: 'rgba(255,255,255,.78)' }}>{item.label}</Typography>
                </Stack>
              ))}
            </Stack>
          </Box>

          <Typography sx={{ position: 'relative', fontSize: 12, color: 'rgba(255,255,255,.34)' }}>
            NIUNIU WIKI · YOUR PRIVATE KNOWLEDGE OS
          </Typography>
        </Stack>

        <Stack alignItems='center' justifyContent='center' sx={{ flex: 0.88, px: 8 }}>
          <Box sx={{ width: '100%', maxWidth: 410 }}>
            <Typography sx={{ fontSize: 34, fontWeight: 700, letterSpacing: '-0.045em' }}>欢迎回来</Typography>
            <Typography sx={{ mt: 1, mb: 5, color: 'text.tertiary', fontSize: 14 }}>登录以继续管理你的知识空间</Typography>
            <Stack gap={2}>
              <TextField
                value={account}
                fullWidth
                onChange={event => setAccount(event.target.value)}
                placeholder='账号'
                autoFocus
                slotProps={{ input: {
                  startAdornment: <IconZhanghao sx={{ fontSize: 17, mr: 1.5, color: 'text.tertiary' }} />,
                  endAdornment: account ? <IconButton onClick={() => setAccount('')} size='small'><IconIcon_tool_close sx={{ fontSize: 13 }} /></IconButton> : null,
                } }}
                sx={{ '& .MuiOutlinedInput-root': { height: 54, bgcolor: alpha('#FFFFFF', .72), borderRadius: '14px !important' } }}
              />
              <TextField
                value={password}
                fullWidth
                onChange={event => setPassword(event.target.value)}
                onKeyDown={event => event.key === 'Enter' && submit()}
                placeholder='密码'
                type={showPassword ? 'text' : 'password'}
                slotProps={{ input: {
                  startAdornment: <IconMima sx={{ fontSize: 17, mr: 1.5, color: 'text.tertiary' }} />,
                  endAdornment: password ? (
                    <Stack direction='row'>
                      <IconButton onClick={() => setShowPassword(value => !value)} size='small'>
                        {showPassword ? <IconKejian sx={{ fontSize: 18 }} /> : <IconBukejian sx={{ fontSize: 18 }} />}
                      </IconButton>
                      <IconButton onClick={() => setPassword('')} size='small'><IconIcon_tool_close sx={{ fontSize: 13 }} /></IconButton>
                    </Stack>
                  ) : null,
                } }}
                sx={{ '& .MuiOutlinedInput-root': { height: 54, bgcolor: alpha('#FFFFFF', .72), borderRadius: '14px !important' } }}
              />
              <Button fullWidth variant='contained' loading={loading} disabled={!account.trim() || !password} onClick={submit} sx={{ mt: 1, height: 52, borderRadius: '14px', fontSize: 15, fontWeight: 600 }}>
                登录
              </Button>
            </Stack>
            <Typography sx={{ mt: 3, textAlign: 'center', fontSize: 11.5, color: 'text.disabled' }}>
              登录即表示你正在访问受保护的牛牛 Wiki 管理空间
            </Typography>
          </Box>
        </Stack>
      </Stack>
    </Box>
  );
};

export default Login;
