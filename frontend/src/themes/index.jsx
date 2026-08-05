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

  const themeTypography = useMemo(() => Typography(state.fontFamily, state.fontSize), [state.fontFamily, state.fontSize]);

  const palette = useMemo(() => {
    const base = buildPalette(state.presetColor);
    const companyPrimaryColor = settings?.primaryColor;
    if (!companyPrimaryColor) return base;

    const buildSemanticScale = (main, fallback, darkMode = false) => {
      const shades = generate(main || fallback);
      return extendPaletteWithChannels({
        lighter: darkMode ? shades[9] : shades[0],
        light: darkMode ? shades[6] : shades[3],
        main: darkMode ? shades[4] : (main || fallback),
        dark: darkMode ? shades[3] : shades[6],
        darker: darkMode ? shades[1] : shades[8],
        contrastText: '#fff'
      });
    };

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

    const semanticLight = {
      secondary: buildSemanticScale(settings?.secondaryColor, '#42A5F5'),
      info: buildSemanticScale(settings?.infoColor, '#00A2AE'),
      success: buildSemanticScale(settings?.successColor, '#00A854'),
      warning: buildSemanticScale(settings?.warningColor, '#FFBF00'),
      error: buildSemanticScale(settings?.errorColor, '#F04134')
    };
    const semanticDark = {
      secondary: buildSemanticScale(settings?.secondaryColor, '#42A5F5', true),
      info: buildSemanticScale(settings?.infoColor, '#00A2AE', true),
      success: buildSemanticScale(settings?.successColor, '#00A854', true),
      warning: buildSemanticScale(settings?.warningColor, '#FFBF00', true),
      error: buildSemanticScale(settings?.errorColor, '#F04134', true)
    };
    const alertLight = {
      infoStandardBg: semanticLight.info.lighter,
      infoIconColor: semanticLight.info.main,
      infoColor: semanticLight.info.dark,
      infoFilledBg: semanticLight.info.main,
      infoFilledColor: contrastText
    };

    return {
      light: { ...base.light, primary: primaryLight, ...semanticLight, Alert: alertLight },
      dark: { ...base.dark, primary: primaryDark, ...semanticDark }
    };
  }, [
    state.presetColor,
    settings?.primaryColor,
    settings?.secondaryColor,
    settings?.infoColor,
    settings?.successColor,
    settings?.warningColor,
    settings?.errorColor
  ]);

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
        borderRadius: 4 // قيمة التدوير الأساسية لكل sx={{ borderRadius: N }}
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
        {/* Keep informational alerts aligned with the configurable semantic palette. */}
        <GlobalStyles
          styles={(theme) => ({
            '.MuiAlert-standardInfo': {
              backgroundColor: theme.palette.info.lighter,
              color: theme.palette.info.dark
            },
            '.MuiAlert-standardInfo .MuiAlert-icon': {
              color: theme.palette.info.main
            }
          })}
        />
        {children}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

ThemeCustomization.propTypes = { children: PropTypes.node };
