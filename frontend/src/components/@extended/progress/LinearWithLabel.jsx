import PropTypes from 'prop-types';
// material-ui
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// ==============================|| PROGRESS - LINEAR WITH LABEL ||============================== //

export default function LinearWithLabel({ value, ...others }) {
  return (
    <Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}>
      <Box sx={{ width: '0.0625rem' }}>
        <LinearProgress variant="determinate" value={value} {...others} />
      </Box>
      <Typography variant="body2" sx={{ color: 'text.secondary', minWidth: '2.1875rem' }}>
        {`${Math.round(value)}%`}
      </Typography>
    </Stack>
  );
}

LinearWithLabel.propTypes = { value: PropTypes.any, others: PropTypes.any };
