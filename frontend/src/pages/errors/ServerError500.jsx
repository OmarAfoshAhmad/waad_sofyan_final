import { Box, Button, Container, Typography } from '@mui/material';
import { Link } from 'react-router-dom';

const ServerError500 = () => {
  return (
    <Container maxWidth="md" sx={{ textAlign: 'center', py: '4.0rem' }}>
      <Box>
        <Typography variant="h1" component="h1" sx={{ fontSize: '6rem', fontWeight: 700, color: 'error.main' }}>
          500
        </Typography>
        <Typography variant="h4" component="h2" sx={{ mb: '1.0rem' }}>
          Internal Server Error
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: '2.0rem' }}>
          Something went wrong on our end. Please try again later or contact support if the problem persists.
        </Typography>
        <Button variant="contained" color="primary" component={Link} to="/dashboard" size="large">
          Go to Dashboard
        </Button>
      </Box>
    </Container>
  );
};

export default ServerError500;
