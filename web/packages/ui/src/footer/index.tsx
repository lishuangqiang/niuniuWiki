'use client';

import { alpha, Box, Divider, Link, Stack, Typography } from '@mui/material';
import React from 'react';

interface DomainSocialMediaAccount {
  channel?: string;
  icon?: string;
  link?: string;
  text?: string;
  phone?: string;
}

interface CustomStyle {
  allow_theme_switching?: boolean;
  header_search_placeholder?: string;
  show_brand_info?: boolean;
  social_media_accounts?: DomainSocialMediaAccount[];
  footer_show_intro?: boolean;
}

export interface BrandGroup {
  name: string;
  links: { name: string; url: string }[];
}

interface FooterSetting {
  footer_style: 'simple' | 'complex';
  corp_name: string;
  icp: string;
  brand_name: string;
  brand_desc: string;
  brand_logo: string;
  brand_groups: BrandGroup[];
}

interface FooterProps {
  mobile?: boolean;
  catalogWidth?: number;
  showBrand?: boolean;
  isDocPage?: boolean;
  docWidth?: string;
  customStyle?: CustomStyle;
  footerSetting?: FooterSetting;
  logo?: string;
}

const Footer = React.memo((props: FooterProps) => {
  const {
    mobile = false,
    showBrand = true,
    customStyle,
    footerSetting,
    logo,
  } = props;
  const showIntro = customStyle?.footer_show_intro !== false;
  const groups = footerSetting?.brand_groups || [];
  const accounts = customStyle?.social_media_accounts || [];
  const hasDetails = showIntro || groups.length > 0;

  return (
    <Box
      component='footer'
      id='footer'
      sx={theme => ({
        width: '100%',
        px: mobile ? 2.5 : 5,
        color: 'text.secondary',
        bgcolor:
          theme.palette.mode === 'dark'
            ? alpha(theme.palette.text.primary, 0.04)
            : '#fff',
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      })}
    >
      <Box
        sx={{
          width: '100%',
          maxWidth: 1176,
          mx: 'auto',
          py: hasDetails ? (mobile ? 4 : 5) : 2.25,
        }}
      >
        {hasDetails && (
          <Stack
            direction={mobile ? 'column' : 'row'}
            justifyContent='space-between'
            gap={mobile ? 4 : 8}
          >
            {showIntro && (
              <Box sx={{ maxWidth: 420, flex: 1 }}>
                <Stack direction='row' alignItems='center' gap={1.25}>
                  {(footerSetting?.brand_logo || logo) && (
                    <Box
                      component='img'
                      src={footerSetting?.brand_logo || logo}
                      alt='牛牛 Wiki'
                      sx={{
                        width: 42,
                        height: 42,
                        objectFit: 'cover',
                        borderRadius: '13px',
                        boxShadow: '0 6px 18px rgba(0,0,0,.12)',
                      }}
                    />
                  )}
                  <Box>
                    <Typography
                      sx={{
                        color: 'text.primary',
                        fontSize: 18,
                        fontWeight: 700,
                        letterSpacing: '-0.03em',
                      }}
                    >
                      {footerSetting?.brand_name || '牛牛 Wiki'}
                    </Typography>
                    <Typography
                      sx={{
                        mt: 0.25,
                        fontSize: 10.5,
                        color: 'text.tertiary',
                        letterSpacing: '.1em',
                      }}
                    >
                      KNOWLEDGE, BEAUTIFULLY ORGANIZED
                    </Typography>
                  </Box>
                </Stack>
                {footerSetting?.brand_desc && (
                  <Typography
                    sx={{
                      mt: 2,
                      fontSize: 13,
                      lineHeight: 1.8,
                      color: 'text.tertiary',
                    }}
                  >
                    {footerSetting.brand_desc}
                  </Typography>
                )}
                {accounts.length > 0 && (
                  <Stack
                    direction='row'
                    flexWrap='wrap'
                    gap={1}
                    sx={{ mt: 2.5 }}
                  >
                    {accounts.map((account, index) => {
                      const href =
                        account.link ||
                        (account.phone ? `tel:${account.phone}` : undefined);
                      return (
                        <Link
                          key={`${account.channel}-${index}`}
                          href={href}
                          target={account.link ? '_blank' : undefined}
                          sx={theme => ({
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 0.75,
                            px: 1.25,
                            py: 0.75,
                            borderRadius: '999px',
                            color: 'text.secondary',
                            bgcolor: alpha(theme.palette.text.primary, 0.05),
                            textDecoration: 'none',
                          })}
                        >
                          {account.icon && (
                            <Box
                              component='img'
                              src={account.icon}
                              alt=''
                              sx={{
                                width: 18,
                                height: 18,
                                borderRadius: '5px',
                                objectFit: 'cover',
                              }}
                            />
                          )}
                          <Typography sx={{ fontSize: 12 }}>
                            {account.text || account.phone}
                          </Typography>
                        </Link>
                      );
                    })}
                  </Stack>
                )}
              </Box>
            )}

            {groups.length > 0 && (
              <Stack
                direction='row'
                flexWrap='wrap'
                gap={mobile ? 4 : 7}
                sx={{
                  flex: 1,
                  justifyContent: mobile ? 'flex-start' : 'flex-end',
                }}
              >
                {groups.map(group => (
                  <Box key={group.name} sx={{ minWidth: 120 }}>
                    <Typography
                      sx={{
                        mb: 1.25,
                        color: 'text.primary',
                        fontSize: 13,
                        fontWeight: 600,
                      }}
                    >
                      {group.name}
                    </Typography>
                    <Stack gap={1}>
                      {group.links?.map(link => (
                        <Link
                          key={link.name}
                          href={link.url || undefined}
                          target={link.url ? '_blank' : undefined}
                          sx={{
                            color: 'text.tertiary',
                            fontSize: 12,
                            textDecoration: 'none',
                            '&:hover': { color: 'primary.main' },
                          }}
                        >
                          {link.name}
                        </Link>
                      ))}
                    </Stack>
                  </Box>
                ))}
              </Stack>
            )}
          </Stack>
        )}

        {hasDetails && <Divider sx={{ my: mobile ? 3 : 4 }} />}
        <Stack
          direction={mobile ? 'column' : 'row'}
          alignItems={mobile ? 'flex-start' : 'center'}
          justifyContent='space-between'
          gap={1.25}
        >
          <Stack direction='row' alignItems='center' flexWrap='wrap' gap={1.25}>
            {footerSetting?.corp_name && (
              <Typography sx={{ fontSize: 11.5, color: 'text.tertiary' }}>
                {footerSetting.corp_name}
              </Typography>
            )}
            {footerSetting?.icp && (
              <Link
                href='https://beian.miit.gov.cn/'
                target='_blank'
                sx={{
                  fontSize: 11.5,
                  color: 'text.tertiary',
                  textDecoration: 'none',
                }}
              >
                {footerSetting.icp}
              </Link>
            )}
          </Stack>
          {showBrand && customStyle?.show_brand_info !== false && (
            <Stack direction='row' alignItems='center' gap={0.8}>
              {logo && (
                <Box
                  component='img'
                  src={logo}
                  alt=''
                  sx={{
                    width: 20,
                    height: 20,
                    objectFit: 'cover',
                    borderRadius: '6px',
                  }}
                />
              )}
              <Typography sx={{ fontSize: 11.5, color: 'text.tertiary' }}>
                由 牛牛 Wiki 提供支持
              </Typography>
            </Stack>
          )}
        </Stack>
      </Box>
    </Box>
  );
});

Footer.displayName = 'NiuniuFooter';

export default Footer;
