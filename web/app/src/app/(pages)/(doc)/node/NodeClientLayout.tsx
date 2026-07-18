'use client';

import { FooterSetting } from '@/assets/type';
import EmptyDocPlaceholder from '@/components/emptyDocPlaceholder';
import { FooterProvider } from '@/components/footer';
import Header from '@/components/header';
import { CONTENT_GAP } from '@/constant';
import { useSyncNavByDocId } from '@/hooks/useSyncNavByDocId';
import { useStore } from '@/provider';
import Catalog from '@/views/node/Catalog';
import CatalogH5 from '@/views/node/CatalogH5';
import NavBar from '@/views/node/NavBar';
import { Box, Stack } from '@mui/material';
import { useMemo } from 'react';

const PCLayout = ({ children }: { children: React.ReactNode }) => {
  const { tree, kbDetail, catalogWidth = 260 } = useStore();
  const docWidth = useMemo(
    () => kbDetail?.settings?.theme_and_style?.doc_width || 'full',
    [kbDetail],
  );

  return (
    <Stack
      sx={theme => ({
        height: '100vh',
        overflow: 'auto',
        bgcolor: 'background.paper2',
        backgroundImage: `radial-gradient(circle at 12% 2%, ${theme.palette.mode === 'dark' ? 'rgba(0,113,227,.13)' : 'rgba(0,113,227,.075)'}, transparent 30rem), radial-gradient(circle at 92% 22%, ${theme.palette.mode === 'dark' ? 'rgba(175,82,222,.1)' : 'rgba(175,82,222,.055)'}, transparent 34rem)`,
        backgroundAttachment: 'local',
      })}
      id='scroll-container'
    >
      <Header isDocPage={true} />
      <NavBar docWidth={docWidth} catalogWidth={catalogWidth} />
      {tree?.length === 0 ? (
        <EmptyDocPlaceholder />
      ) : (
        <Stack
          sx={{ flex: 1, px: { md: 3, lg: 5, xl: 7 }, alignItems: 'center' }}
        >
          <Stack
            direction='row'
            justifyContent='center'
            alignItems='flex-start'
            gap={`${CONTENT_GAP}px`}
            sx={{
              pt: { md: 3, lg: 4.5 },
              pb: 12,
              flex: 1,
              width: '100%',
            }}
          >
            <Catalog />
            {children}
          </Stack>
        </Stack>
      )}

      <FooterProvider isDocPage={true} />
    </Stack>
  );
};

const MobileLayout = ({
  children,
  footerSetting,
}: {
  children?: React.ReactNode;
  footerSetting?: FooterSetting | null;
}) => {
  const { tree } = useStore();
  return (
    <Stack
      sx={{
        position: 'relative',
        height: '100vh',
        overflow: 'auto',
        zIndex: 1,
        bgcolor: 'background.paper2',
        backgroundImage:
          'radial-gradient(circle at 10% 0%, rgba(0,113,227,.08), transparent 26rem)',
      }}
    >
      <Box sx={{ flex: 1 }}>
        <Header />
        <NavBar />
        {tree?.length === 0 ? (
          <EmptyDocPlaceholder mobile />
        ) : (
          <>
            <CatalogH5 />
            {children}
          </>
        )}
      </Box>

      <Box
        sx={{
          mt: 5,
          bgcolor: 'background.paper3',
          ...(footerSetting?.footer_style === 'complex' && {
            borderTop: '1px solid',
            borderColor: 'divider',
          }),
        }}
      >
        <FooterProvider />
      </Box>
    </Stack>
  );
};

export default function NodeClientLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { mobile, kbDetail } = useStore();
  const footerSetting = kbDetail?.settings?.footer_settings;
  useSyncNavByDocId();

  return (
    <>
      {mobile ? (
        <MobileLayout footerSetting={footerSetting}>{children}</MobileLayout>
      ) : (
        <PCLayout>{children}</PCLayout>
      )}
    </>
  );
}
