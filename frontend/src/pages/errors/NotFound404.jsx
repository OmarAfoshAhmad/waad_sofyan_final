import { Box, Button, Container, Typography } from '@mui/material';
import { Link } from 'react-router-dom';

const NotFound404 = () => {
  return (
    <Container maxWidth="md" sx={{ textAlign: 'center', py: '4.0rem' }}>
      <Box>
        <Typography variant="h1" component="h1" sx={{ fontSize: '6rem', fontWeight: 700, color: 'error.main' }}>
          404
        </Typography>
        <Typography variant="h4" component="h2" sx={{ mb: '1.0rem' }}>
          Page Not Found
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: '2.0rem' }}>
          The page you are looking for might have been removed, had its name changed, or is temporarily unavailable.
        </Typography>
        <Button variant="contained" color="primary" component={Link} to="/dashboard" size="large">
          Go to Dashboard
        </Button>
      </Box>
    </Container>
  );
};

export default NotFound404;
