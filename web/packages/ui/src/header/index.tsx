'use client';

import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import {
  alpha,
  Box,
  Button,
  IconButton,
  Link,
  Menu,
  MenuItem,
  Stack,
  Typography,
} from '@mui/material';
import React, { useEffect, useState } from 'react';
import { NavBtn } from './NavBtns';

interface SearchSuggestion {
  id: string;
  title: string;
  description?: string;
  type?: 'recent' | 'suggestion' | 'trending';
}

interface HeaderProps {
  isDocPage?: boolean;
  mobile?: boolean;
  docWidth?: string;
  catalogWidth?: number;
  logo?: string;
  placeholder?: string;
  title?: string;
  showSearch?: boolean;
  onSearch?: (value?: string, type?: 'search' | 'chat') => void;
  onSearchSuggestions?: (query: string) => Promise<SearchSuggestion[]>;
  btns?: NavBtn[];
  children?: React.ReactNode;
  onQaClick?: () => void;
  homePath?: string;
  seamless?: boolean;
}

const Header = React.memo(
  ({
    mobile = false,
    logo = '',
    placeholder = '搜索知识库',
    title = '牛牛 Wiki',
    showSearch = true,
    btns = [],
    children,
    onQaClick,
    homePath = '/',
    seamless = false,
  }: HeaderProps) => {
    const [shortcut, setShortcut] = useState('');
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    useEffect(() => {
      setShortcut(
        /Mac|iPod|iPhone|iPad/.test(navigator.userAgent) ? '⌘ K' : 'Ctrl K',
      );
      const handleKeyDown = (event: KeyboardEvent) => {
        if (
          (event.metaKey || event.ctrlKey) &&
          event.key.toLowerCase() === 'k'
        ) {
          event.preventDefault();
          onQaClick?.();
        }
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }, [onQaClick]);

    const visibleButtons = btns.slice(0, 2);
    const extraButtons = btns.slice(2);

    return (
      <Stack
        component='header'
        direction='row'
        alignItems='center'
        sx={theme => ({
          position: 'sticky',
          top: 0,
          zIndex: 110,
          height: 56,
          flexShrink: 0,
          px: mobile ? 2 : 3,
          bgcolor: seamless
            ? 'transparent'
            : alpha(theme.palette.background.default, 0.9),
          backgroundImage:
            theme.palette.mode === 'dark'
              ? 'none'
              : seamless
                ? 'none'
                : `linear-gradient(${alpha('#6b7f9f', 0.065)} 1px, transparent 1px), linear-gradient(90deg, ${alpha('#6b7f9f', 0.065)} 1px, transparent 1px)`,
          backgroundSize: '64px 64px',
          backgroundPosition: '-1px -1px',
          backdropFilter: seamless ? 'none' : 'saturate(140%) blur(16px)',
          borderBottom: seamless
            ? '1px solid transparent'
            : `1px solid ${alpha(theme.palette.text.primary, 0.07)}`,
          boxShadow: 'none',
        })}
      >
        <Stack
          direction='row'
          alignItems='center'
          justifyContent='space-between'
          gap={mobile ? 1 : 2}
          sx={{ width: '100%', maxWidth: 1480, mx: 'auto', minWidth: 0 }}
        >
          <Link
            href={homePath}
            aria-label='返回首页'
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.9,
              minWidth: 0,
              color: 'text.primary',
              textDecoration: 'none',
            }}
          >
            {logo && (
              <Box
                component='img'
                src={logo}
                alt='牛牛 Wiki'
                sx={{
                  width: mobile ? 30 : 32,
                  height: mobile ? 30 : 32,
                  flexShrink: 0,
                  objectFit: 'cover',
                  borderRadius: '50%',
                  boxShadow: '0 2px 8px rgba(0,0,0,.10)',
                }}
              />
            )}
            <Typography
              sx={{
                maxWidth: mobile ? 150 : 260,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                fontFamily:
                  'Outfit, "Avenir Next", var(--font-gilory), "PingFang SC", system-ui, sans-serif',
                fontSize: mobile ? 17 : 20,
                fontWeight: 650,
                letterSpacing: '-0.025em',
              }}
            >
              {title}
            </Typography>
          </Link>

          {showSearch && !mobile && (
            <Button
              onClick={() => onQaClick?.()}
              startIcon={<SearchRounded sx={{ fontSize: '18px !important' }} />}
              endIcon={
                <Stack direction='row' alignItems='center' gap={0.6}>
                  <AutoAwesomeRounded
                    sx={{ fontSize: 14, color: 'primary.main' }}
                  />
                  <Typography sx={{ fontSize: 11, color: 'text.tertiary' }}>
                    {shortcut}
                  </Typography>
                </Stack>
              }
              sx={theme => ({
                width: 'min(420px, 34vw)',
                height: 36,
                px: 1.5,
                justifyContent: 'flex-start',
                color: 'text.secondary',
                bgcolor: alpha(theme.palette.background.paper, 0.78),
                border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                borderRadius: '999px',
                boxShadow: 'none !important',
                transform: 'none !important',
                backdropFilter: 'blur(16px)',
                '& .MuiButton-endIcon': { ml: 'auto' },
                '&:hover': {
                  bgcolor: theme.palette.background.paper,
                  borderColor: alpha(theme.palette.text.primary, 0.2),
                },
              })}
            >
              <Typography noWrap sx={{ fontSize: 13 }}>
                {placeholder || '搜索知识库'}
              </Typography>
            </Button>
          )}

          <Stack
            direction='row'
            alignItems='center'
            justifyContent='flex-end'
            gap={0.75}
          >
            {mobile && showSearch && (
              <IconButton
                aria-label='搜索与问答'
                onClick={() => onQaClick?.()}
                sx={{ width: 38, height: 38 }}
              >
                <SearchRounded sx={{ fontSize: 21 }} />
              </IconButton>
            )}
            {!mobile &&
              visibleButtons.map((item, index) => (
                <Button
                  component={Link}
                  href={item.url}
                  target={item.target}
                  key={`${item.text}-${index}`}
                  variant={item.variant === 'contained' ? 'contained' : 'text'}
                  sx={{
                    px: 1.5,
                    height: 36,
                    whiteSpace: 'nowrap',
                    fontSize: 13,
                  }}
                >
                  {item.text}
                </Button>
              ))}
            {!mobile && extraButtons.length > 0 && (
              <>
                <IconButton
                  onClick={event => setAnchorEl(event.currentTarget)}
                  sx={{ width: 36, height: 36 }}
                >
                  <MoreHorizRounded sx={{ fontSize: 20 }} />
                </IconButton>
                <Menu
                  anchorEl={anchorEl}
                  open={Boolean(anchorEl)}
                  onClose={() => setAnchorEl(null)}
                >
                  {extraButtons.map((item, index) => (
                    <MenuItem
                      component={Link}
                      href={item.url}
                      target={item.target}
                      key={`${item.text}-${index}`}
                      onClick={() => setAnchorEl(null)}
                    >
                      {item.text}
                    </MenuItem>
                  ))}
                </Menu>
              </>
            )}
            {children}
          </Stack>
        </Stack>
      </Stack>
    );
  },
);

Header.displayName = 'NiuniuHeader';

export default Header;
