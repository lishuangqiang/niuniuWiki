import { PaletteOptions } from '@mui/material';

const lightPalette: PaletteOptions = {
  primary: {
    main: '#007AFF',
    contrastText: '#fff',
  },
  error: {
    main: '#F64E54',
  },
  success: {
    main: '#82DDAF',
    light: '#AAF27F',
    dark: '#229A16',
    contrastText: 'rgba(0,0,0,0.7)',
  },
  warning: {
    main: '#FEA145',
    light: '#FFE16A',
    dark: '#B78103',
    contrastText: 'rgba(0,0,0,0.7)',
  },
  info: {
    main: '#0063FF',
    light: '#74CAFF',
    dark: '#0C53B7',
    contrastText: '#fff',
  },
  divider: 'rgba(60, 60, 67, 0.12)',
  dark: {
    dark: '#000',
    main: '#14141B',
    light: '#20232A',
    contrastText: '#fff',
  },
  light: {
    main: '#fff',
    contrastText: '#000',
  },
  disabled: {
    main: '#666',
  },
  background: {
    default: '#F5F5F7',
    paper: '#FFFFFF',
    paper2: '#F5F5F7',
    paper3: '#F2F2F7',
    footer: '#F5F5F7',
  },
  text: {
    primary: '#1D1D1F',
    secondary: '#424245',
    tertiary: '#6E6E73',
    disabled: '#AEAEB2',
  },
  table: {
    head: {
      background: '#F5F5F7',
    },
    cell: {
      border: 'rgba(60, 60, 67, 0.12)',
    },
  },
};

export default lightPalette;
