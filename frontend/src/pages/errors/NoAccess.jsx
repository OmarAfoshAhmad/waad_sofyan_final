import { Link } from 'react-router-dom';
import { Box, Button, Container, Typography, Paper } from '@mui/material';
import { Block as BlockIcon, Home as HomeIcon } from '@mui/icons-material';

// ==============================|| ERROR 403 - ACCESS DENIED ||============================== //

const NoAccess = () => {
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default'
      }}
    >
      <Container maxWidth="sm">
        <Paper
          elevation={3}
          sx={{
            p: '2.5rem',
            textAlign: 'center',
            borderRadius: '0.1875rem'
          }}
        >
          {/* Icon */}
          <Box sx={{ mb: '1.5rem' }}>
            <BlockIcon
              sx={{
                fontSize: '7.5rem',
                color: 'error.main',
                opacity: 0.8
              }}
            />
          </Box>

          {/* Error Code */}
          <Typography
            variant="h1"
            sx={{
              fontSize: '5rem',
              fontWeight: 700,
              color: 'error.main',
              mb: '1.0rem'
            }}
          >
            403
          </Typography>

          {/* Title */}
          <Typography
            variant="h4"
            sx={{
              fontWeight: 600,
              mb: '1.0rem',
              color: 'text.primary'
            }}
          >
            Access Denied
          </Typography>

          {/* Description */}
          <Typography
            variant="body1"
            sx={{
              color: 'text.secondary',
              mb: '2.0rem',
              lineHeight: 1.8
            }}
          >
            You don't have permission to access this page.
            <br />
            Please contact your system administrator if you believe this is an error.
          </Typography>

          {/* Action Button */}
          <Button
            component={Link}
            to="/"
            variant="contained"
            size="large"
            startIcon={<HomeIcon />}
            sx={{
              px: '2.0rem',
              py: '0.75rem',
              borderRadius: '0.125rem',
              textTransform: 'none',
              fontSize: '1rem'
            }}
          >
            Go to Home
          </Button>

          {/* Additional Info */}
          <Typography
            variant="caption"
            sx={{
              display: 'block',
              mt: '2.0rem',
              color: 'text.disabled'
            }}
          >
            Error Code: 403 | Forbidden Access
          </Typography>
        </Paper>
      </Container>
    </Box>
  );
};

export default NoAccess;


