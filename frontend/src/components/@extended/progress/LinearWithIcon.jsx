import PropTypes from 'prop-types';

// material-ui
import Stack from '@mui/material/Stack';
import LinearProgress from '@mui/material/LinearProgress';
import Box from '@mui/material/Box';

// ==============================|| PROGRESS - LINEAR ICON ||============================== //

export default function LinearWithIcon({ icon, value, ...others }) {
  return (
    <Stack direction="row" sx={{ alignItems: 'center' }}>
      <Box sx={{ width: '100%', mr: 1 }}>
        <LinearProgress variant="determinate" value={value} {...others} />
      </Box>
      <Box sx={{ minWidth: '2.1875rem' }}>{icon}</Box>
    </Stack>
  );
}

LinearWithIcon.propTypes = { icon: PropTypes.any, value: PropTypes.any, others: PropTypes.any };
