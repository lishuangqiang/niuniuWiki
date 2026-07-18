'use client';
import feedback from '@/assets/images/feedback.png';
import Footer from '@/components/footer';
import { useStore } from '@/provider';
import { postShareV1ChatFeedback } from '@/request/ShareChat';
import { DomainFeedbackRequest } from '@/request/types';
import { alpha, Box, Button, Stack, TextField } from '@mui/material';
import { message } from '@ctzhian/ui';
import Image from 'next/image';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

const Feedback = () => {
  const searchParams = useSearchParams();
  const { kbDetail } = useStore();
  const message_id = searchParams.get('message_id') || '';
  const conversation_id = searchParams.get('conversation_id') || '';
  const score = parseInt(searchParams.get('score') || '-1') as -1 | 1;

  const tags: string[] =
    // @ts-ignore
    kbDetail?.settings?.ai_feedback_settings?.ai_feedback_type || [];

  const [type, setType] = useState<string>('');
  const [content, setContent] = useState('');
  const [success, setSuccess] = useState(score === 1);

  const handleSubmit = async () => {
    const data: DomainFeedbackRequest = {
      conversation_id,
      message_id,
      score,
      type,
      feedback_content: content,
    };
    await postShareV1ChatFeedback(data);
    setSuccess(true);
    message.success('反馈成功');
  };

  useEffect(() => {
    if (score === 1) {
      handleSubmit();
    }
  }, [score]);

  return (
    <>
      <Box
        sx={theme => ({
          width: '100vw',
          minHeight: 'calc(100vh - 40px)',
          p: { xs: 2, sm: 4 },
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `radial-gradient(circle at 10% 0%, ${alpha(theme.palette.primary.main, 0.16)}, transparent 28rem), radial-gradient(circle at 90% 100%, ${alpha('#af52de', 0.12)}, transparent 30rem), ${theme.palette.background.paper2}`,
        })}
      >
        {success ? (
          <Box
            className='niu-rise'
            sx={theme => ({
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              width: '100%',
              maxWidth: 560,
              minHeight: 420,
              p: 5,
              bgcolor: alpha(theme.palette.background.paper, 0.82),
              backdropFilter: 'saturate(180%) blur(24px)',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '30px',
              boxShadow: `0 28px 90px ${alpha(theme.palette.text.primary, 0.1)}`,
            })}
          >
            <Image src={feedback.src} alt='success' width={220} height={220} />
            <Box
              sx={{
                fontSize: 24,
                fontWeight: 680,
                letterSpacing: '-0.035em',
                mt: 2,
              }}
            >
              感谢您的反馈！
            </Box>
            <Box sx={{ mt: 1, color: 'text.tertiary', fontSize: 13.5 }}>
              每一条建议，都会帮助知识回答变得更好。
            </Box>
          </Box>
        ) : (
          <Box
            className='niu-rise'
            sx={theme => ({
              width: '100%',
              maxWidth: 680,
              p: { xs: 3, sm: 5 },
              bgcolor: alpha(theme.palette.background.paper, 0.84),
              backdropFilter: 'saturate(180%) blur(24px)',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '30px',
              boxShadow: `0 28px 90px ${alpha(theme.palette.text.primary, 0.1)}`,
            })}
          >
            <Box
              sx={{
                fontSize: { xs: 28, sm: 34 },
                fontWeight: 700,
                letterSpacing: '-0.045em',
              }}
            >
              帮助我们做得更好。
            </Box>
            <Box
              sx={{
                mt: 1,
                mb: 4,
                color: 'text.tertiary',
                fontSize: 14,
                lineHeight: 1.7,
              }}
            >
              告诉我们这次回答哪里不够准确，或还缺少哪些信息。
            </Box>
            <Box
              sx={{
                fontSize: 14,
                fontWeight: 650,
                mb: 2,
              }}
            >
              问题类型
            </Box>
            <Stack
              direction='row'
              gap={1}
              sx={{
                flexWrap: 'wrap',
                mb: 4,
              }}
            >
              {tags.map(tag => (
                <Box
                  key={tag}
                  sx={{
                    py: 0.9,
                    px: 2,
                    fontSize: 13,
                    borderRadius: '999px',
                    border: '1px solid',
                    borderColor: type === tag ? 'primary.main' : 'divider',
                    cursor: 'pointer',
                    color: type === tag ? 'primary.main' : 'text.primary',
                    bgcolor:
                      type === tag ? 'primary.lighter' : 'background.paper3',
                    transition: 'all .2s ease',
                  }}
                  onClick={() => {
                    setType(tag);
                  }}
                >
                  {tag}
                </Box>
              ))}
            </Stack>
            <Box
              sx={{
                fontSize: 14,
                fontWeight: 650,
                my: 2,
              }}
            >
              反馈内容
            </Box>
            <Box
              sx={{
                borderRadius: '18px',
                border: '1px solid',
                borderColor: 'divider',
                bgcolor: 'background.paper3',
                p: 2.25,
                transition: 'border-color .2s ease, box-shadow .2s ease',
                '&:focus-within': {
                  borderColor: 'primary.main',
                  boxShadow: '0 0 0 4px rgba(0,113,227,.08)',
                },
              }}
            >
              <TextField
                fullWidth
                multiline
                rows={8}
                size='small'
                placeholder='请输入反馈内容'
                value={content}
                sx={{
                  '.MuiInputBase-root': {
                    p: 0,
                    overflow: 'hidden',
                    transition: 'all 0.5s ease-in-out',
                  },
                  textarea: {
                    lineHeight: '26px',
                    borderRadius: 0,
                    transition: 'all 0.5s ease-in-out',
                    '&::-webkit-scrollbar': {
                      display: 'none',
                    },
                    '&::placeholder': {
                      fontSize: 14,
                    },
                    scrollbarWidth: 'none',
                    msOverflowStyle: 'none',
                  },
                  fieldset: {
                    border: 'none',
                  },
                }}
                onChange={e => setContent(e.target.value)}
              />
            </Box>
            <Button
              variant='contained'
              fullWidth
              color='primary'
              sx={{
                mt: 4,
                height: 50,
                fontSize: 15,
              }}
              onClick={handleSubmit}
            >
              提交
            </Button>
          </Box>
        )}
      </Box>
      <Box
        sx={{
          height: 40,
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: 1000,
        }}
      >
        <Footer showBrand={false} />
      </Box>
    </>
  );
};

export default Feedback;
