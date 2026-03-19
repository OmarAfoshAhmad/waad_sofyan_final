// third-party
import { presetDarkPalettes, presetPalettes } from '@ant-design/colors';

// project imports
import ThemeOption from './theme';
import { ThemeMode } from 'config';
import { extendPaletteWithChannels, withAlpha } from 'utils/colorUtils';

const greyAscent = ['#fafafa', '#bfbfbf', '#434343', '#1f1f1f'];

// ==============================|| GREY COLORS BUILDER ||============================== //

function buildGrey(mode) {
  let greyPrimary = [
    '#ffffff',
    '#fafafa',
    '#f5f5f5',
    '#f0f0f0',
    '#d9d9d9',
    '#bfbfbf',
    '#8c8c8c',
    '#595959',
    '#262626',
    '#141414',
    '#000000'
  ];
  let greyConstant = ['#fafafb', '#e6ebf1'];

  if (mode === ThemeMode.DARK) {
    greyPrimary = ['#000000', '#141414', '#1e1e1e', '#595959', '#8c8c8c', '#bfbfbf', '#d9d9d9', '#f0f0f0', '#f5f5f5', '#fafafa', '#ffffff'];
    greyConstant = ['#121212', '#d3d8db'];
  }

  return [...greyPrimary, ...greyAscent, ...greyConstant];
}

// ==============================|| DEFAULT THEME - PALETTE ||============================== //

export function buildPalette(presetColor = 'default') {
  // light colors
  const lightColors = { ...presetPalettes, grey: buildGrey(ThemeMode.LIGHT) };
  const lightPaletteColor = ThemeOption(lightColors, presetColor, ThemeMode.LIGHT);

  // dark colors
  const darkColors = { ...presetDarkPalettes, grey: buildGrey(ThemeMode.DARK) };
  const darkPaletteColor = ThemeOption(darkColors, presetColor, ThemeMode.DARK);

  const commonColor = { common: { black: '#000', white: '#fff' } };

  const extendedLight = extendPaletteWithChannels(lightPaletteColor);
  const extendedDark = extendPaletteWithChannels(darkPaletteColor);
  const extendedCommon = extendPaletteWithChannels(commonColor);

  return {
    light: {
      mode: 'light',
      ...extendedCommon,
      ...extendedLight,
      text: {
        primary: extendedLight.grey[700],
        secondary: extendedLight.grey[500],
        disabled: extendedLight.grey[400]
      },
      action: { disabled: extendedLight.grey[300] },
      divider: extendedLight.grey[200],
      background: {
        paper: '#ffffff',
        default: '#f4fafa'  // أبيض مع مسحة تيل خفيفة — بدلاً من الفضي #fafafb
      }
    },
    dark: {
      mode: 'dark',
      ...extendedCommon,
      ...extendedDark,
      text: {
        primary: withAlpha(extendedDark.grey[900], 0.87),
        secondary: withAlpha(extendedDark.grey[900], 0.45),
        disabled: withAlpha(extendedDark.grey[900], 0.1)
      },
      action: { disabled: extendedDark.grey[300] },
      divider: withAlpha(extendedDark.grey[900], 0.05),
      background: {
        paper: extendedDark.grey[100],
        default: extendedDark.grey.A50
      }
    }
  };
}
