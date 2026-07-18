'use client';
import { useMemo, useState, useEffect } from 'react';
import Home from '@/views/home';
import { ThemeProvider } from '@ctzhian/ui';
import { WelcomeHeader } from '@/components/header';
import { Stack, createTheme } from '@mui/material';
import { createComponentStyleOverrides } from '@/theme';
import { useStore } from '@/provider';
import { THEME_TO_PALETTE } from '@niuniu-wiki/themes/constants';

const HomePage = () => {
  const { kbDetail } = useStore();
  const [showSearch, setShowSearch] = useState(false);

  useEffect(() => {
    let ticking = false;

    const checkVisibility = () => {
      const elements = document.querySelectorAll('.banner-search-box');
      if (elements.length > 0) {
        // 判断是否还有任意一个搜索框处于可视区域内
        // 顶部预留 64px 为头部占用区域
        const hasVisibleBox = Array.from(elements).some(el => {
          const rect = el.getBoundingClientRect();
          return rect.bottom > 56 && rect.top < window.innerHeight;
        });
        setShowSearch(!hasVisibleBox);
      } else {
        setShowSearch(window.scrollY >= window.innerHeight);
      }
    };

    const handleScroll = () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          checkVisibility();
          ticking = false;
        });
        ticking = true;
      }
    };

    checkVisibility();

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const theme = useMemo(() => {
    // @ts-ignore
    const themeMode = kbDetail?.settings?.web_app_landing_theme?.name || 'blue';
    return createTheme({
      cssVariables: {
        cssVarPrefix: 'welcome',
      },
      palette:
        THEME_TO_PALETTE[themeMode]?.palette ||
        THEME_TO_PALETTE['blue'].palette,
      typography: {
        fontFamily:
          "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', var(--font-gilory), 'PingFang SC', sans-serif",
      },
      components: createComponentStyleOverrides(true),
    });
    // @ts-ignore
  }, [kbDetail?.settings?.web_app_landing_theme?.name]);

  return (
    <ThemeProvider theme={theme}>
      <Stack
        justifyContent='space-between'
        sx={{
          minHeight: '100vh',
          bgcolor: '#fff',
          backgroundImage:
            'linear-gradient(rgba(107,127,159,.075) 1px, transparent 1px), linear-gradient(90deg, rgba(107,127,159,.075) 1px, transparent 1px), radial-gradient(circle at 16% 8%, rgba(142,187,255,.15), transparent 30%), radial-gradient(circle at 78% 18%, rgba(255,195,214,.13), transparent 34%)',
          backgroundSize: '64px 64px, 64px 64px, auto, auto',
          backgroundPosition: '-1px -1px, -1px -1px, 0 0, 0 0',
          isolation: 'isolate',
        }}
      >
        <WelcomeHeader showSearch={showSearch} />
        <Stack sx={{ flex: 1 }}>
          <Home />
        </Stack>
      </Stack>
    </ThemeProvider>
  );
};

export default HomePage;
