import { PaletteOptions } from '@mui/material';

const darkPalette: PaletteOptions = {
  mode: 'dark',
  primary: {
    main: '#0A84FF',
    contrastText: '#FFFFFF',
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
  divider: 'rgba(255, 255, 255, 0.12)',
  disabled: {
    main: '#666',
  },
  dark: {
    dark: '#000',
    main: '#14141B',
    light: '#202531',
    contrastText: '#fff',
  },
  light: {
    main: '#fff',
    contrastText: '#000',
  },
  background: {
    default: '#000000',
    paper: '#1C1C1E',
    paper2: '#000000',
    paper3: '#2C2C2E',
    footer: '#1C1C1E',
  },
  table: {
    head: {
      background: '#292929',
    },
    cell: {
      border: '#434343',
    },
  },
  text: {
    primary: '#FFFFFF',
    secondary: 'rgba(255, 255, 255, 0.72)',
    tertiary: 'rgba(255, 255, 255, 0.55)',
    disabled: 'rgba(255, 255, 255, 0.32)',
  },
};

export default darkPalette;
