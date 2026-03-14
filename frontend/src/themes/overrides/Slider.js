// project imports
import getColors from 'utils/getColors';

// ==============================|| OVERRIDES - TAB ||============================== //

function getColorStyle({ color, theme }) {
  const colors = getColors(theme, color);
  const { main } = colors;

  return { border: '2px solid', borderColor: main };
}

export default function Slider(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const secondaryVars = (varsPalette && varsPalette.secondary) || theme.palette.secondary || theme.palette.primary || {};
  return {
    MuiSlider: {
      styleOverrides: {
        track: {
          height: 1.0
        },
        thumb: {
          width: 14.0,
          height: 14.0,
          border: '2px solid',
          borderColor: varsPalette.primary?.main ?? theme.palette.primary?.main,
          backgroundColor: varsPalette.background?.paper ?? theme.palette.background?.paper,
          '&.MuiSlider-thumbColorPrimary': getColorStyle({ color: 'primary', theme }),
          '&.MuiSlider-thumbColorSecondary': getColorStyle({ color: 'secondary', theme }),
          '&.MuiSlider-thumbColorSuccess': getColorStyle({ color: 'success', theme }),
          '&.MuiSlider-thumbColorWarning': getColorStyle({ color: 'warning', theme }),
          '&.MuiSlider-thumbColorInfo': getColorStyle({ color: 'info', theme }),
          '&.MuiSlider-thumbColorError': getColorStyle({ color: 'error', theme })
        },
        mark: {
          width: 4.0,
          height: 4.0,
          borderRadius: '50%',
          border: '1px solid',
          borderColor: secondaryVars.light ?? secondaryVars.main,
          backgroundColor: varsPalette.background?.paper ?? theme.palette.background?.paper,
          '&.MuiSlider-markActive': {
            opacity: 1,
            borderColor: 'inherit',
            borderWidth: 2
          }
        },
        rail: {
          color: secondaryVars.light ?? secondaryVars.main
        },
        root: {
          '&.Mui-disabled': {
            '.MuiSlider-rail': {
              opacity: 0.25
            },
            '.MuiSlider-track': {
              color: secondaryVars.lighter ?? secondaryVars.main
            },
            '.MuiSlider-thumb': {
              border: '2px solid',
              borderColor: secondaryVars.lighter ?? secondaryVars.main
            }
          }
        },
        valueLabel: {
          backgroundColor: varsPalette.grey?.[600] ?? theme.palette.grey?.[600],
          color: varsPalette.grey?.[0] ?? theme.palette.grey?.[0]
        }
      }
    }
  };
}
