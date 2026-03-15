import PropTypes from 'prop-types';
import { Box, Stack, Typography, Paper } from '@mui/material';
import { InboxOutlined } from '@ant-design/icons';

/**
 * Modern Empty State Component
 * Displays when there is no data to show
 */
const ModernEmptyState = ({ icon: Icon = InboxOutlined, title = 'لا توجد بيانات', description, action, height = 400 }) => {
  return (
    <Paper
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: height,
        bgcolor: 'background.paper',
        border: '1px dashed',
        borderColor: 'divider',
        borderRadius: '0.25rem'
      }}
    >
      <Stack spacing={2} alignItems="center" sx={{ maxWidth: '25.0rem', px: '1.5rem', py: '2.0rem', textAlign: 'center' }}>
        {/* Icon */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '5.0rem',
            height: '5.0rem',
            borderRadius: '50%',
            bgcolor: 'primary.lighter',
            color: 'primary.main',
            opacity: 0.8
          }}
        >
          <Icon style={{ fontSize: '2.5rem' }} />
        </Box>

        {/* Title */}
        <Typography variant="h5" color="text.primary" sx={{ fontWeight: 600 }}>
          {title}
        </Typography>

        {/* Description */}
        {description && (
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        )}

        {/* Action Button */}
        {action && <Box sx={{ mt: '1.0rem' }}>{action}</Box>}
      </Stack>
    </Paper>
  );
};

ModernEmptyState.propTypes = {
  icon: PropTypes.elementType,
  title: PropTypes.string,
  description: PropTypes.string,
  action: PropTypes.node,
  height: PropTypes.number
};

export default ModernEmptyState;
