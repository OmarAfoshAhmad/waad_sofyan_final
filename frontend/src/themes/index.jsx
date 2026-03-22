import PropTypes from 'prop-types';
import { useMemo } from 'react';

// material-ui
import { createTheme, StyledEngineProvider, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import GlobalStyles from '@mui/material/GlobalStyles';

// third-party
import { generate } from '@ant-design/colors';

// project imports
import { CSS_VAR_PREFIX, DEFAULT_THEME_MODE, ThemeMode } from 'config';
import useConfig from 'hooks/useConfig';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import CustomShadows from './custom-shadows';
import componentsOverride from './overrides';
import { buildPalette } from './palette';
import Typography from './typography';
import { extendPaletteWithChannels } from 'utils/colorUtils';

// ==============================|| DEFAULT THEME - MAIN ||============================== //

export default function ThemeCustomization({ children }) {
  const { state } = useConfig();
  const { settings } = useCompanySettings();

  const themeTypography = useMemo(
    () => Typography(state.fontFamily, state.fontSize),
    [state.fontFamily, state.fontSize]
  );

  const palette = useMemo(() => {
    const base = buildPalette(state.presetColor);
    const companyPrimaryColor = settings?.primaryColor;
    if (!companyPrimaryColor) return base;

    // Generate a 10-shade scale from the company primary color
    const lightShades = generate(companyPrimaryColor);
    const contrastText = '#fff';

    // Light mode: lightest → darkest
    const primaryLight = extendPaletteWithChannels({
      lighter: lightShades[0],
      100: lightShades[1],
      200: lightShades[2],
      light: lightShades[3],
      400: lightShades[4],
      main: companyPrimaryColor,
      dark: lightShades[6],
      700: lightShades[7],
      darker: lightShades[8],
      900: lightShades[9],
      contrastText
    });

    // Dark mode: inverted shades (deepest dark becomes "lighter")
    const primaryDark = extendPaletteWithChannels({
      lighter: lightShades[9],
      100: lightShades[8],
      200: lightShades[7],
      light: lightShades[6],
      400: lightShades[5],
      main: lightShades[4],
      dark: lightShades[3],
      700: lightShades[2],
      darker: lightShades[1],
      900: lightShades[0],
      contrastText
    });

    const alertLight = {
      infoStandardBg: primaryLight.lighter,
      infoIconColor: primaryLight.main,
      infoColor: primaryLight.dark,
      infoFilledBg: primaryLight.main,
      infoFilledColor: contrastText
    };

    return {
      light: { ...base.light, primary: primaryLight, Alert: alertLight },
      dark: { ...base.dark, primary: primaryDark }
    };
  }, [state.presetColor, settings?.primaryColor]);

  const themeOptions = useMemo(
    () => ({
      breakpoints: {
        values: {
          xs: 0,
          sm: 768, // Fixed 'sm' breakpoint to 768
          md: 1024,
          lg: 1266,
          xl: 1440
        }
      },
      direction: state.themeDirection,
      shape: {
        borderRadius: 4  // قيمة التدوير الأساسية لكل sx={{ borderRadius: N }}
      },
      mixins: {
        toolbar: {
          minHeight: '3.75rem',
          paddingTop: '0.375rem',
          paddingBottom: '0.375rem'
        }
      },
      typography: themeTypography,
      colorSchemes: {
        light: {
          palette: palette.light,
          customShadows: CustomShadows(palette.light, ThemeMode.LIGHT)
        },
        dark: {
          palette: palette.dark,
          customShadows: CustomShadows(palette.dark, ThemeMode.DARK)
        }
      },
      cssVariables: {
        cssVarPrefix: CSS_VAR_PREFIX,
        colorSchemeSelector: 'data-color-scheme'
      }
    }),
    [state.themeDirection, themeTypography, palette]
  );

  const themes = createTheme(themeOptions);
  themes.components = componentsOverride(themes);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider disableTransitionOnChange theme={themes} modeStorageKey="theme-mode" defaultMode={DEFAULT_THEME_MODE}>
        <CssBaseline enableColorScheme />
        {/* Override MUI Alert info severity to follow the theme's primary color instead of hardcoded info (blue) */}
        <GlobalStyles styles={(theme) => ({
          '.MuiAlert-standardInfo': {
            backgroundColor: theme.palette.primary.lighter,
            color: theme.palette.primary.dark,
          },
          '.MuiAlert-standardInfo .MuiAlert-icon': {
            color: theme.palette.primary.main,
          },
        })} />
        {children}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

ThemeCustomization.propTypes = { children: PropTypes.node };


