// material-ui
import { Box, Button, Typography } from '@mui/material';
import MainCard from './MainCard';

// ==============================|| ERROR FALLBACK ||============================== //

export default function ErrorFallback({ error, resetErrorBoundary }) {
  return (
    <MainCard>
      <Box sx={{ textAlign: 'center', py: '2.5rem' }}>
        <Typography variant="h3" color="error" gutterBottom>
          Something went wrong
        </Typography>
        <Typography variant="body1" color="textSecondary" sx={{ mb: '1.5rem' }}>
          {error?.message || 'عذراً، حدث خطأ غير متوقع'}
        </Typography>
        {resetErrorBoundary && (
          <Button variant="contained" onClick={resetErrorBoundary}>
            Try again
          </Button>
        )}
      </Box>
    </MainCard>
  );
}
