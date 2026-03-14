import PropTypes from 'prop-types';
import { Box } from '@mui/material';

const ScrollX = ({ children, ...other }) => {
  return (
    <Box
      sx={{
        overflowX: 'auto',
        width: '100%',
        '&::-webkit-scrollbar': { height: '0.375rem' },
        '&::-webkit-scrollbar-thumb': {
          backgroundColor: 'rgba(0,0,0,.2)',
          borderRadius: '0.375rem'
        }
      }}
      {...other}
    >
      {children}
    </Box>
  );
};

ScrollX.propTypes = {
  children: PropTypes.node
};

export default ScrollX;


