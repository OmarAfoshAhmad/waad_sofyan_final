import { useQuery } from '@tanstack/react-query';
import { getProviders } from 'services/api/providers.service';
import { Box, Paper, Typography, CircularProgress, Alert } from '@mui/material';

/**
 * TEMPORARY DEBUG PAGE
 * Used to diagnose provider list loading issue
 * Can be removed after issue is resolved
 */
const ProvidersDebugTest = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['providers-debug'],
    queryFn: async () => {
      const response = await getProviders({ page: 0, size: 1000 });
      return response;
    }
  });

  return (
    <Box sx={{ p: '1.5rem' }}>
      <Paper sx={{ p: '1.5rem' }}>
        <Typography variant="h4" gutterBottom>
          Provider API Debug Test
        </Typography>

        {isLoading && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '1.0rem', my: '1.0rem' }}>
            <CircularProgress size={20} />
            <Typography>Loading providers...</Typography>
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ my: '1.0rem' }}>
            Error: {error.message}
          </Alert>
        )}

        {data && (
          <Box sx={{ mt: '1.5rem' }}>
            <Typography variant="h6" gutterBottom>
              Raw API Response:
            </Typography>
            <Paper sx={{ p: '1.0rem', bgcolor: '#f5f5f5', maxHeight: '25.0rem', overflow: 'auto' }}>
              <pre>{JSON.stringify(data, null, 2)}</pre>
            </Paper>

            <Typography variant="h6" gutterBottom sx={{ mt: '1.5rem' }}>
              Analysis:
            </Typography>
            <Paper sx={{ p: '1.0rem' }}>
              <Typography>• Type: {Array.isArray(data) ? 'Array' : typeof data}</Typography>
              <Typography>• Is Array: {Array.isArray(data) ? 'Yes ✅' : 'No ❌'}</Typography>
              <Typography>• Has 'content': {data?.content ? 'Yes ✅' : 'No ❌'}</Typography>
              <Typography>• Has 'data': {data?.data ? 'Yes ✅' : 'No ❌'}</Typography>

              {Array.isArray(data) && <Typography>• Array Length: {data.length}</Typography>}

              {data?.content && <Typography>• content.length: {data.content.length}</Typography>}

              {data?.data && <Typography>• data.length: {Array.isArray(data.data) ? data.data.length : 'Not an array'}</Typography>}

              <Typography variant="h6" sx={{ mt: '1.0rem' }}>
                Extracted Providers:
              </Typography>
              {(() => {
                const providers = Array.isArray(data) ? data : data?.content || data?.data || [];

                return (
                  <>
                    <Typography>• Count: {providers.length}</Typography>
                    {providers.length > 0 && (
                      <Box sx={{ mt: '1.0rem' }}>
                        <Typography variant="subtitle2">First Provider:</Typography>
                        <pre style={{ fontSize: '0.75rem' }}>{JSON.stringify(providers[0], null, 2)}</pre>
                      </Box>
                    )}
                  </>
                );
              })()}
            </Paper>
          </Box>
        )}
      </Paper>
    </Box>
  );
};

export default ProvidersDebugTest;


