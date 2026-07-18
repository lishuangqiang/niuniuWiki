'use client';
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useTextAnimation } from '../hooks/useGsapAnimation';
import {
  ButtonProps,
  styled,
  TextField,
  Button,
  Stack,
  Box,
  alpha,
  lighten,
} from '@mui/material';
import { StyledTopicBox } from '../component/styledCommon';

const StyledBanner = styled('div')(({ theme }) => ({
  position: 'relative',
  overflow: 'hidden',
  backgroundColor: '#fff',
  backgroundImage: `linear-gradient(${alpha('#6b7f9f', 0.075)} 1px, transparent 1px), linear-gradient(90deg, ${alpha('#6b7f9f', 0.075)} 1px, transparent 1px), radial-gradient(circle at 16% 8%, ${alpha('#8ebbff', 0.15)}, transparent 30%), radial-gradient(circle at 78% 18%, ${alpha('#ffc3d6', 0.13)}, transparent 34%)`,
  backgroundSize: '64px 64px, 64px 64px, auto, auto',
  marginTop: 0,
}));

const StyledTitle = styled('h1')(({ theme }) => ({
  maxWidth: 900,
  fontFamily:
    'Outfit, "Avenir Next", var(--font-gilory), "PingFang SC", system-ui, sans-serif',
  fontSize: 36,
  fontWeight: 600,
  lineHeight: 1.25,
  letterSpacing: '-0.025em',
  wordBreak: 'break-word',
  color: theme.palette.text.primary,
  marginBottom: theme.spacing(3),
  [theme.breakpoints.down('md')]: {
    fontSize: 32,
  },
  [theme.breakpoints.down('sm')]: {
    fontSize: 28,
  },
}));

const StyledSubTitle = styled('h2')(({ theme }) => ({
  maxWidth: 760,
  fontWeight: 400,
  lineHeight: 1.7,
  marginBottom: theme.spacing(3.5),
  color: '#777b84',
}));

const StyledSearchBox = styled(Box)(({ theme }) => ({
  position: 'relative',
  width: '100%',
  maxWidth: 820,
  minHeight: 150,
  padding: theme.spacing(1.5),
  boxShadow: '0 4px 10px rgba(0, 0, 0, .10)',
  border: `0.5px solid ${alpha('#111c30', 0.14)}`,
  borderRadius: '12px',
  backgroundColor: '#fff',
  '&:hover': {
    borderColor: alpha('#111c30', 0.26),
  },
  '&:focus-within': {
    borderColor: alpha('#111c30', 0.38),
  },
}));

const StyledTextField = styled(TextField)(({ theme }) => ({
  '.MuiInputBase-root': {
    padding: 0,
  },
  fieldset: {
    border: 'none',
  },
  '& input::placeholder, & textarea::placeholder': {
    color: alpha(theme.palette.text.primary, 0.5),
    opacity: 1,
  },
}));

// 闪烁光标样式
const blinkAnimation = `
  @keyframes blink {
    0%, 49% {
      opacity: 1;
    }
    50%, 100% {
      opacity: 0;
    }
  }
`;

const StyledCursor = styled('span')(({ theme }) => ({
  display: 'inline-block',
  width: '1px',
  height: '18px',
  backgroundColor: alpha(theme.palette.text.primary, 1),
  marginLeft: '2px',
  animation: 'blink 1s infinite',
  flexShrink: 0,
}));

const StyledHotItem = styled(Box)(({ theme }) => ({
  color: theme.palette.text.primary,
  padding: theme.spacing(0.65, 1.5),
  borderRadius: '999px',
  border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
  fontSize: 12,
  cursor: 'pointer',
  transition: 'all 0.2s',
  '&:hover': {
    borderColor: alpha(theme.palette.primary.main, 0.1),
    color: theme.palette.primary.main,
  },
}));

interface SearchSuggestion {
  id: string;
  title: string;
  description?: string;
  type?: 'recent' | 'suggestion' | 'trending';
}

interface BannerProps {
  title: {
    text: string;
    fontSize: string;
    color: string;
  };
  subtitle: {
    text: string;
    fontSize: string;
    color: string;
  };
  bg_url?: string;
  search: {
    placeholder: string;
    hot: string[];
  };
  btns: {
    type: ButtonProps['variant'];
    text: string;
    href: string;
  }[];
  onSearch?: (value: string, type?: 'search' | 'chat') => void;
  onSearchSuggestions?: (query: string) => Promise<SearchSuggestion[]>;
  basePath?: string;
}

const Banner = React.memo(
  ({
    title,
    subtitle,
    bg_url,
    search,
    btns = [],
    onSearch,
    onSearchSuggestions,
    basePath = '',
  }: BannerProps) => {
    const [searchText, setSearchText] = useState('');
    const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [anchorElWidth, setAnchorElWidth] = useState<number | null>(null);
    const [selectedIndex, setSelectedIndex] = useState(-1);
    const [isFocused, setIsFocused] = useState(false);
    const [typedText, setTypedText] = useState('');
    const debounceTimer = useRef<NodeJS.Timeout | null>(null);
    const typewriterTimer = useRef<NodeJS.Timeout | null>(null);

    // 添加文字动画效果
    const titleRef = useTextAnimation(0, 0.1);
    const subtitleRef = useTextAnimation(0.2, 0.1);

    // 打字机效果
    useEffect(() => {
      if (isFocused || !search.hot || search.hot.length === 0) {
        return;
      }

      let currentIndex = 0;
      let currentCharIndex = 0;
      let isDeleting = false;
      let isPaused = false;

      const typeWriter = () => {
        const currentWord = search.hot[currentIndex];

        if (isPaused) {
          typewriterTimer.current = setTimeout(() => {
            isPaused = false;
            typeWriter();
          }, 1000); // 暂停1秒
          return;
        }

        if (!isDeleting) {
          // 打字阶段
          if (currentCharIndex < currentWord.length) {
            setTypedText(currentWord.substring(0, currentCharIndex + 1));
            currentCharIndex++;
            typewriterTimer.current = setTimeout(typeWriter, 100); // 打字速度（调慢）
          } else {
            // 打完了，暂停后开始删除
            isPaused = true;
            isDeleting = true;
            typeWriter();
          }
        } else {
          // 删除阶段
          if (currentCharIndex > 0) {
            currentCharIndex--;
            setTypedText(currentWord.substring(0, currentCharIndex));
            typewriterTimer.current = setTimeout(typeWriter, 80); // 删除速度（调慢）
          } else {
            // 删完了，切换到下一个词
            isDeleting = false;
            currentIndex = (currentIndex + 1) % search.hot.length;
            typewriterTimer.current = setTimeout(typeWriter, 200); // 切换词之间的延迟
          }
        }
      };

      typeWriter();

      return () => {
        if (typewriterTimer.current) {
          clearTimeout(typewriterTimer.current);
        }
      };
    }, [isFocused, search.hot]);

    // 防抖搜索
    const debouncedSearch = useCallback(
      (query: string) => {
        if (debounceTimer.current) {
          clearTimeout(debounceTimer.current);
        }
        debounceTimer.current = setTimeout(async () => {
          if (query.trim() && onSearchSuggestions) {
            setIsLoading(true);
            try {
              const results = await onSearchSuggestions(query);
              setSuggestions(results);
            } catch (error) {
              console.error('搜索建议获取失败:', error);
              setSuggestions([]);
            } finally {
              setIsLoading(false);
            }
          } else {
            setSuggestions([]);
          }
        }, 300);
      },
      [onSearchSuggestions],
    );

    // 处理输入变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      setSearchText(value);
      setSelectedIndex(-1);

      if (value.trim()) {
        debouncedSearch(value);
        if (onSearch) {
          setAnchorEl(e.currentTarget.parentElement);
          setAnchorElWidth(e.currentTarget.parentElement?.offsetWidth || 0);
        }
      } else {
        setSuggestions([]);
        setAnchorEl(null);
      }
    };

    // 处理键盘事件
    const handleKeyDown = (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        // e.preventDefault();
        // if (selectedIndex >= 0 && suggestions[selectedIndex]) {
        //   const selectedSuggestion = suggestions[selectedIndex];
        //   setSearchText(selectedSuggestion.title);
        //   onSearch?.(selectedSuggestion.title);
        // } else {
        //   onSearch?.(searchText);
        // }
        onSearch?.(searchText, 'chat');
        setSearchText('');
        setAnchorEl(null);
        setSelectedIndex(-1);
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex(prev =>
          prev < suggestions.length - 1 ? prev + 1 : prev,
        );
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex(prev => (prev > 0 ? prev - 1 : -1));
      } else if (e.key === 'Escape') {
        setAnchorEl(null);
        setSelectedIndex(-1);
      }
    };

    // 处理输入框聚焦
    const handleInputFocus = (e: React.FocusEvent) => {
      setIsFocused(true);
      setTypedText(''); // 清空打字机文本
      if (searchText.trim()) {
        setAnchorEl(e.currentTarget.parentElement);
        setAnchorElWidth(e.currentTarget.parentElement?.offsetWidth || 0);
      }
    };

    // 处理输入框失焦
    const handleInputBlur = () => {
      setIsFocused(false);
    };

    // 清理定时器
    useEffect(() => {
      return () => {
        if (debounceTimer.current) {
          clearTimeout(debounceTimer.current);
        }
      };
    }, []);

    return (
      <StyledBanner
        sx={{
          ...(bg_url
            ? {
                backgroundImage: `url(${bg_url})`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat',
              }
            : {}),
        }}
      >
        <StyledTopicBox
          sx={{
            alignItems: 'center',
            textAlign: 'center',
            gap: 0,
            py: { xs: 10, md: '108px' },
            pt: { xs: 10, md: '90px' },
          }}
        >
          <StyledTitle ref={titleRef}>{title.text}</StyledTitle>
          {/* {subtitle.text && ( */}
          <StyledSubTitle
            ref={subtitleRef}
            sx={{
              fontSize: `${subtitle.fontSize || 16}px`,
            }}
          >
            {subtitle.text}
          </StyledSubTitle>
          {/* )} */}

          <StyledSearchBox className='banner-search-box'>
            <Box sx={{ position: 'relative' }}>
              <style>{blinkAnimation}</style>
              {!isFocused && !searchText && typedText && (
                <Box
                  sx={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    pointerEvents: 'none',
                    color: theme => alpha(theme.palette.text.primary, 0.85),
                    fontSize: '16px',
                    lineHeight: 1.5,
                    display: 'inline-flex',
                    alignItems: 'center',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  <span>{typedText}</span>
                  <StyledCursor />
                </Box>
              )}
              <StyledTextField
                fullWidth
                placeholder={isFocused || searchText ? search.placeholder : ''}
                value={searchText}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                onFocus={handleInputFocus}
                onBlur={handleInputBlur}
                multiline
                minRows={1}
                maxRows={4}
              />
            </Box>
            <Stack direction='row' alignItems='center' gap={1} flexWrap='wrap'>
              <Stack direction='row' gap='8px 16px' flexWrap='wrap'>
                {search.hot?.map(hot => (
                  <StyledHotItem key={hot} onClick={() => onSearch?.(hot)}>
                    {hot}
                  </StyledHotItem>
                ))}
              </Stack>
              <Button
                variant='contained'
                size='small'
                sx={{
                  fontSize: 12,
                  borderRadius: 4,
                  flexShrink: 0,
                  ml: 'auto',
                  bgcolor: '#111',
                  '&:hover': { bgcolor: '#303030' },
                }}
                onClick={() => onSearch?.(searchText, 'chat')}
              >
                AI 智能问答
              </Button>
            </Stack>
          </StyledSearchBox>

          {btns.length > 0 && (
            <Stack
              direction='row'
              gap={{
                xs: '16px 24px',
                md: '16px 40px',
              }}
              sx={{ mt: 5 }}
              flexWrap='wrap'
            >
              {btns.map(btn => (
                <Button
                  key={btn.text}
                  variant={btn.type}
                  href={btn.href}
                  target='_blank'
                  size='large'
                  color='primary'
                  sx={theme => ({
                    ...(btn.type === 'outlined' && {
                      borderWidth: 2,
                      bgcolor: theme.palette.background.default,
                      borderColor: alpha(theme.palette.primary.main, 0.8),
                      '&:hover': {
                        borderColor: theme.palette.primary.main,
                      },
                    }),
                    lineHeight: 1.5,
                    fontSize: {
                      xs: 14,
                      md: 18,
                    },
                    px: {
                      xs: 3,
                      md: '69px',
                    },
                    py: {
                      xs: 1,
                      md: '12px',
                    },
                  })}
                >
                  {btn.text}
                </Button>
              ))}
            </Stack>
          )}
        </StyledTopicBox>
      </StyledBanner>
    );
  },
);

export default Banner;
