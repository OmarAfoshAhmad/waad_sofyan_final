import PropTypes from 'prop-types';
import { Box, Typography, Stack } from '@mui/material';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';

const EmptyState = ({ title = 'No data available', description = 'No records found', icon: Icon = InboxOutlinedIcon, action = null }) => {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '25.0rem',
        textAlign: 'center',
        p: '1.5rem'
      }}
    >
      <Stack spacing={2} alignItems="center">
        <Icon sx={{ fontSize: '5.0rem', color: 'text.disabled' }} />
        <Typography variant="h5" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="body2" color="text.disabled" sx={{ maxWidth: '25.0rem' }}>
          {description}
        </Typography>
        {action && <Box sx={{ mt: '1.0rem' }}>{action}</Box>}
      </Stack>
    </Box>
  );
};

EmptyState.propTypes = {
  title: PropTypes.string,
  description: PropTypes.string,
  icon: PropTypes.elementType,
  action: PropTypes.node
};

export default EmptyState;
