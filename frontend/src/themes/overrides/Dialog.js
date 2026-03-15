// project imports
import { withAlpha } from 'utils/colorUtils';

// ==============================|| OVERRIDES - DIALOG ||============================== //

export default function Dialog(theme) {
  const varsPalette = (theme.vars && theme.vars.palette) || theme.palette || {};
  const commonVars = varsPalette.common || theme.palette?.common || {};
  const blackColor = commonVars.black || '#000';

  return {
    MuiDialog: {
      styleOverrides: {
        root: {
          '& .MuiBackdrop-root': {
            backgroundColor: withAlpha(blackColor, 0.7)
          }
        },
        paper: {
          backgroundImage: 'none',
          borderRadius: 6  // 6px - نوافذ مدورة الزوايا
        }
      }
    }
  };
}
