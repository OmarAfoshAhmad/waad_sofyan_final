import PropTypes from 'prop-types';
import { Grid, Card, CardActionArea, Stack, Typography, Box } from '@mui/material';
import { useNavigate } from 'react-router-dom';

/**
 * Modern Quick Actions Component
 * Displays quick action cards for common tasks
 */
const ModernQuickActions = ({ actions = [] }) => {
  const navigate = useNavigate();

  const handleAction = (action) => {
    if (action.onClick) {
      action.onClick();
    } else if (action.path) {
      navigate(action.path);
    }
  };

  if (actions.length === 0) return null;

  return (
    <Grid container spacing={2}>
      {actions.map((action, index) => (
        <Grid key={index} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <Card
            sx={{
              height: '100%',
              transition: 'transform 0.2s, box-shadow 0.2s',
              '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: 2
              }
            }}
          >
            <CardActionArea onClick={() => handleAction(action)} sx={{ height: '100%', p: '1.0rem' }}>
              <Stack direction="row" spacing={2} alignItems="center">
                {/* Icon */}
                {action.icon && (
                  <Box
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '3.0rem',
                      height: '3.0rem',
                      borderRadius: '0.375rem',
                      bgcolor: `${action.color || 'primary'}.lighter`,
                      color: `${action.color || 'primary'}.main`,
                      flexShrink: 0
                    }}
                  >
                    <action.icon sx={{ fontSize: '1.5rem' }} />
                  </Box>
                )}

                {/* Text */}
                <Stack spacing={0.5} sx={{ flex: 1 }}>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    {action.title}
                  </Typography>
                  {action.description && (
                    <Typography variant="caption" color="text.secondary">
                      {action.description}
                    </Typography>
                  )}
                </Stack>
              </Stack>
            </CardActionArea>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
};

ModernQuickActions.propTypes = {
  actions: PropTypes.arrayOf(
    PropTypes.shape({
      title: PropTypes.string.isRequired,
      description: PropTypes.string,
      icon: PropTypes.elementType,
      path: PropTypes.string,
      onClick: PropTypes.func,
      color: PropTypes.oneOf(['primary', 'secondary', 'error', 'warning', 'info', 'success'])
    })
  )
};

export default ModernQuickActions;



