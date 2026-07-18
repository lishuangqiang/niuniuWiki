import { createTheme, CssVarsThemeOptions } from '@mui/material';
import type { Shadows } from '@mui/material';
import { zhCN } from '@mui/material/locale';
import { zhCN as CuiZhCN } from '@ctzhian/ui/dist/local';
import onData from '@/assets/images/nodata.png';
import { darkPalette, lightPalette } from '@niuniu-wiki/themes';

const defaultTheme = createTheme();

const componentStyleOverrides = (
  defaultColor: boolean = true,
): CssVarsThemeOptions['components'] => ({
  MuiCssBaseline: {
    styleOverrides: {
      body: {
        fontFamily:
          "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'PingFang SC', 'Helvetica Neue', sans-serif",
      },
    },
  },
  MuiTabs: {
    styleOverrides: {
      indicator: {
        backgroundColor: '#21222D',
      },
    },
  },
  MuiPaper: {
    styleOverrides: {
      root: {
        backgroundImage: 'none',
      },
    },
  },
  MuiTextField: {
    styleOverrides: {
      root: ({ theme }) => ({
        label: {
          color: theme.palette.text.secondary,
        },
        'label.Mui-focused': {
          color: theme.palette.text.primary,
        },
        '& .MuiInputBase-input::placeholder': {
          fontSize: '12px',
        },
      }),
    },
  },
  MuiInputBase: {
    styleOverrides: {
      root: ({ theme }) => ({
        fontSize: 14,
        borderRadius: '12px !important',
        backgroundColor: theme.palette.background.paper3,
        '.MuiOutlinedInput-notchedOutline': {
          borderColor: `${theme.palette.background.paper3} !important`,
          borderWidth: '1px !important',
        },
        '&.Mui-focused': {
          '.MuiOutlinedInput-notchedOutline': {
            borderColor: `${theme.palette.primary.main} !important`,
            borderWidth: '1px !important',
          },
        },
        '&:hover': {
          '.MuiOutlinedInput-notchedOutline': {
            borderColor: `${theme.palette.primary.main} !important`,
            borderWidth: '1px !important',
          },
        },
        input: {
          height: '19px',
          '&.Mui-disabled': {
            color: `${theme.palette.text.secondary} !important`,
            WebkitTextFillColor: `${theme.palette.text.secondary} !important`,
          },
        },
      }),
    },
  },

  MuiCheckbox: {
    styleOverrides: {
      root: {
        padding: 0,
        svg: {
          fontSize: '18px',
        },
      },
    },
  },
  MuiPagination: {
    defaultProps: {
      color: 'dark',
    },
  },
  MuiButton: {
    defaultProps: {
      color: defaultColor ? 'dark' : 'primary',
    },
    styleOverrides: {
      root: {
        minHeight: 36,
        fontWeight: 500,
        borderRadius: '10px',
        letterSpacing: '-0.01em',
        boxShadow: 'none',
        '&:hover': {
          boxShadow: '0 4px 16px rgba(0, 122, 255, 0.14)',
          transform: 'translateY(-1px)',
        },
      },
    },
  },
  MuiInputLabel: {
    styleOverrides: {
      root: {
        fontSize: 14,
      },
    },
  },
  MuiMenu: {
    styleOverrides: {
      paper: {
        borderRadius: '14px',
        border: '1px solid rgba(60, 60, 67, 0.1)',
        boxShadow: '0 16px 48px rgba(0, 0, 0, 0.12)',
      },
    },
  },
  MuiMenuItem: {
    styleOverrides: {
      root: {
        fontSize: '14px',
      },
    },
  },
  MuiAutocomplete: {
    defaultProps: {
      slotProps: {
        paper: {
          elevation: 8,
        },
      },
    },
    styleOverrides: {
      paper: {
        borderRadius: '14px',
      },
      option: {
        fontSize: '14px',
      },
    },
  },
  MuiFormLabel: {
    styleOverrides: {
      root: {
        color: 'unset',
        fontSize: '0.8rem',
      },
      asterisk: {
        color: '#F64E54',
      },
    },
  },
  MuiLink: {
    styleOverrides: {
      root: {
        textDecoration: 'none',
      },
    },
  },
  MuiRadio: {
    styleOverrides: {
      root: {
        fontSize: '0.8rem',
      },
    },
  },
  MuiFormControlLabel: {
    styleOverrides: {
      label: {
        fontSize: '0.8rem',
      },
    },
  },
  MuiTableBody: {
    styleOverrides: {
      root: ({ theme }) => ({
        '.MuiTableRow-root:hover': {
          '.MuiTableCell-root:not(.cx-table-empty-td)': {
            backgroundColor: 'rgba(0, 122, 255, 0.035)',
            overflowX: 'hidden',
            '.primary-color': {
              color: theme.palette.primary.main,
            },
            '.no-title-url': {
              color: `${theme.palette.primary.main} !important`,
            },
            '.error-color': {
              opacity: 1,
            },
          },
        },
      }),
    },
  },
  MuiTableCell: {
    styleOverrides: {
      root: ({ theme }) => ({
        borderColor: theme.palette.background.paper,
        paddingTop: '16px !important',
        paddingBottom: '16px !important',
        paddingLeft: '24px !important',
        height: 72,
      }),
      head: {
        paddingTop: '0 !important',
        paddingBottom: '0 !important',
        height: '50px',
        backgroundColor: '#F5F5F7',
        borderBottom: 'none !important',
        fontSize: '12px',
        color: '#000',
      },
      body: {
        borderBottom: '1px dashed',
        borderColor: 'rgba(60, 60, 67, 0.1)',
      },
    },
  },
  MuiSelect: {
    styleOverrides: {
      root: ({ theme }) => ({
        height: '36px',
        borderRadius: '12px !important',
        backgroundColor: theme.palette.background.paper3,
      }),
      select: {
        paddingRight: '0 !important',
      },
    },
  },
});

const themeOptions = [
  {
    // colorSchemes: {
    //   light: {
    //     palette: lightPalette,
    //   },
    //   dark: {
    //     palette: darkPalette,
    //   },
    // },
    typography: {
      fontFamily:
        "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'PingFang SC', 'Helvetica Neue', sans-serif",
    },

    shadows: [
      ...defaultTheme.shadows.slice(0, 8),
      '0px 10px 20px 0px rgba(54,59,76,0.2)',
      ...defaultTheme.shadows.slice(9),
    ] as Shadows,
    components: componentStyleOverrides(false),
  },
  zhCN,
  CuiZhCN,
  {
    components: {
      CuiEmpty: {
        defaultProps: {
          image: onData,
          imageStyle: {
            width: '150px',
          },
        },
      },
    },
  },
];

const theme = createTheme(
  {
    cssVariables: true,
    palette: lightPalette,
    typography: {
      fontFamily:
        "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'PingFang SC', 'Helvetica Neue', sans-serif",
    },
    shadows: [
      ...defaultTheme.shadows.slice(0, 8),
      '0px 10px 20px 0px rgba(54,59,76,0.2)',
      ...defaultTheme.shadows.slice(9),
    ] as Shadows,
    components: componentStyleOverrides(true),
  },
  zhCN,
  CuiZhCN,
  {
    components: {
      CuiEmpty: {
        defaultProps: {
          image: onData,
          imageStyle: {
            width: '150px',
          },
        },
      },
    },
  },
);

export { theme, themeOptions };
