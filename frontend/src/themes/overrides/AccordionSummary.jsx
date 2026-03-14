// assets
import RightOutlined from '@ant-design/icons/RightOutlined';

// ==============================|| OVERRIDES - ALERT TITLE ||============================== //

export default function AccordionSummary(theme) {
  const { spacing } = theme;
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const paletteSecondary = varsPalette.secondary || theme.palette.secondary || theme.palette.primary || {};

  return {
    MuiAccordionSummary: {
      defaultProps: {
        expandIcon: <RightOutlined style={{ fontSize: '0.75rem' }} />
      },
      styleOverrides: {
        root: {
          backgroundColor: paletteSecondary.lighter ?? varsPalette.primary?.lighter ?? theme.palette.primary?.lighter,
          flexDirection: 'row-reverse',
          minHeight: 46.0
        },
        expandIconWrapper: {
          '&.Mui-expanded': {
            transform: 'rotate(90deg)'
          }
        },
        content: {
          marginTop: spacing(1.25),
          marginBottom: spacing(1.25),
          marginLeft: spacing(1)
        }
      }
    }
  };
}


