import PropTypes from 'prop-types';
// material-ui
import useMediaQuery from '@mui/material/useMediaQuery';

// project imports
import DrawerHeaderStyled from './DrawerHeaderStyled';
import Logo from 'components/logo';

import useConfig from 'hooks/useConfig';
import { MenuOrientation } from 'config';

// ==============================|| DRAWER HEADER ||============================== //

export default function DrawerHeader({ open }) {
  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));

  const { state } = useConfig();
  const isHorizontal = state.menuOrientation === MenuOrientation.HORIZONTAL && !downLG;

  return (
    <DrawerHeaderStyled
      open={open}
      sx={{
        minHeight: isHorizontal ? 'unset' : '60px',
        width: isHorizontal ? { xs: '100%', lg: '424px' } : 'initial',
        paddingTop: isHorizontal ? { xs: '10px', lg: '0' } : '8px',
        paddingBottom: isHorizontal ? { xs: '18px', lg: '0' } : '8px',
        paddingLeft: isHorizontal ? { xs: '24px', lg: '0' } : open ? '24px' : 0
      }}
    >
      <Logo isIcon={!open} sx={{ width: open ? 'auto' : 35, height: '2.1875rem' }} />
    </DrawerHeaderStyled>
  );
}

DrawerHeader.propTypes = { open: PropTypes.bool };
