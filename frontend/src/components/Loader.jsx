// material-ui
import LinearProgress from '@mui/material/LinearProgress';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';

// ==============================|| Loader - FIXED ||============================== //

/**
 * CRITICAL FIX:
 * - Show a FULL SCREEN centered loader instead of just a tiny progress bar
 * - Ensures users see a clear loading state (not a blank white screen)
 * - Fixes the "login page not appearing" issue during JWT initialization
 */
export default function Loader() {
  return (
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'background.default',
        zIndex: 2001,
        gap: '1.5rem'
      }}
    >
      <CircularProgress size={60} thickness={4} />
      <Typography variant="h6" color="text.secondary">
        Loading...
      </Typography>
      <LinearProgress
        color="primary"
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%'
        }}
      />
    </Box>
  );
}
