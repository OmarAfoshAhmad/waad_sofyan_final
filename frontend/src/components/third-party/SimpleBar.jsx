import PropTypes from 'prop-types';

// material-ui
import { styled, useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';

// project imports
import { ThemeDirection } from 'config';
import { withAlpha } from 'utils/colorUtils';

// third-party
import SimpleBar from 'simplebar-react';
import { BrowserView, MobileView } from 'react-device-detect';

// root style
const RootStyle = styled(BrowserView)({
  flexGrow: 1,
  height: '100%',
  overflow: 'hidden'
});

// scroll bar wrapper
const SimpleBarStyle = styled(SimpleBar)(({ theme }) => {
  const varsPalette = theme?.vars?.palette || theme.palette || {};
  const greyVars = varsPalette.grey || theme.palette?.grey || {};
  const grey500 = greyVars[500] || '#9e9e9e';
  const grey200 = greyVars[200] || '#eeeeee';

  return {
    maxHeight: '100%',
    '& .simplebar-scrollbar': {
      '&:before': {
        background: withAlpha(grey500, 0.48),
        ...theme.applyStyles('dark', { background: withAlpha(grey200, 0.48) })
      },
      '&.simplebar-visible:before': {
        opacity: 1
      }
    },
    '& .simplebar-track': {
      '&.simplebar-vertical': {
        width: '0.625rem'
      }
    },
    '& .simplebar-track.simplebar-horizontal .simplebar-scrollbar': {
      height: '0.375rem'
    },
    '& .simplebar-mask': {
      zIndex: 'inherit'
    }
  };
});

// ==============================|| SIMPLE SCROLL BAR ||============================== //

export default function SimpleBarScroll({ children, sx, ...other }) {
  const theme = useTheme();

  return (
    <>
      <RootStyle>
        <SimpleBarStyle
          clickOnTrack={false}
          sx={sx}
          data-simplebar-direction={theme.direction === ThemeDirection.RTL ? 'rtl' : 'ltr'}
          {...other}
        >
          {children}
        </SimpleBarStyle>
      </RootStyle>
      <MobileView>
        <Box sx={{ overflowX: 'auto', ...sx }} {...other}>
          {children}
        </Box>
      </MobileView>
    </>
  );
}

SimpleBarScroll.propTypes = { children: PropTypes.any, sx: PropTypes.any, other: PropTypes.any };
