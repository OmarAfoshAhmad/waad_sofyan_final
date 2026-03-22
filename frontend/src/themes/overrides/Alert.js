// project imports
import { withAlpha } from 'utils/colorUtils';
import getColors from 'utils/getColors';

// ==============================|| ALERT - COLORS ||============================== //

function getColorStyle({ color, theme }) {
  const colors = getColors(theme, color);
  const { lighter, light, main } = colors;

  return {
    borderColor: withAlpha(light, 0.5),
    backgroundColor: lighter,
    '& .MuiAlert-icon': { color: main }
  };
}

// ==============================|| OVERRIDES - ALERT ||============================== //

export default function Alert(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const primaryDashed = getColorStyle({ color: 'primary', theme });
  const primaryVars = (varsPalette && varsPalette.primary) || theme.palette.primary || {};

  return {
    MuiAlert: {
      styleOverrides: {
        root: {
          color: varsPalette.text?.primary ?? theme.palette.text?.primary,
          fontSize: '0.875rem',
          variants: [
            {
              props: { variant: 'standard' },
              style: ({ ownerState }) => {
                // Map 'info' severity → 'primary' so info alerts follow the theme color
                const mappedColor = ownerState.color
                  || (ownerState.severity === 'info' ? 'primary' : ownerState.severity)
                  || 'primary';
                const paletteColor = theme.palette[mappedColor] || theme.palette.primary;
                const bgColor = paletteColor?.lighter ?? primaryVars.lighter ?? primaryVars.main;
                const iconColor = paletteColor?.main ?? primaryVars.main;
                return {
                  position: 'relative',
                  backgroundColor: bgColor,
                  '& .MuiAlert-icon': {
                    color: iconColor
                  },
                  ...theme.applyStyles('dark', {
                    backgroundColor: withAlpha(varsPalette.background?.default ?? theme.palette.background?.default, 0.99),
                    '&:before': {
                      width: '100%',
                      height: '100%',
                      position: 'absolute',
                      content: '""',
                      backgroundColor: iconColor,
                      top: 0,
                      left: 0,
                      opacity: 0.05
                    }
                  })
                };
              }
            },
            {
              props: { variant: 'filled' },
              style: ({ ownerState }) => {
                const ownerColor = ownerState.color || 'primary';
                const paletteColor = theme.palette[ownerColor] || theme.palette.primary;
                return {
                  color: varsPalette.grey?.[0] ?? theme.palette.grey?.[0],
                  backgroundColor: paletteColor?.main ?? primaryVars.main,
                  ...theme.applyStyles('dark', {
                    backgroundColor: paletteColor?.dark ?? primaryVars.dark,
                    ...(ownerState.color === 'secondary' && { backgroundColor: paletteColor?.main ?? primaryVars.main })
                  })
                };
              }
            }
          ]
        },
        icon: {
          fontSize: '1rem'
        },
        message: {
          padding: 0,
          marginTop: '1.5rem'
        },
        border: {
          padding: '10px 16px',
          border: '1px solid',
          ...primaryDashed,
          '&.MuiAlert-borderPrimary': getColorStyle({ color: 'primary', theme }),
          '&.MuiAlert-borderSecondary': getColorStyle({ color: 'secondary', theme }),
          '&.MuiAlert-borderError': getColorStyle({ color: 'error', theme }),
          '&.MuiAlert-borderSuccess': getColorStyle({ color: 'success', theme }),
          '&.MuiAlert-borderInfo': getColorStyle({ color: 'info', theme }),
          '&.MuiAlert-borderWarning': getColorStyle({ color: 'warning', theme })
        },
        action: {
          '& .MuiButton-root': {
            padding: '0.125rem',
            height: 'auto',
            fontSize: '0.75rem',
            marginTop: -2
          },
          '& .MuiIconButton-root': {
            width: 'auto',
            height: 'auto',
            padding: '0.125rem',
            marginRight: '0.375rem',
            '& .MuiSvgIcon-root': {
              fontSize: '1rem'
            }
          }
        }
      }
    }
  };
}


