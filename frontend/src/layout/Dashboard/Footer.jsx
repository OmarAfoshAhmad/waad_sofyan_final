// material-ui
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

export default function Footer() {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      sx={{ gap: '0.75rem', alignItems: 'center', justifyContent: 'center', p: '24px 16px 0px', mt: 'auto' }}
    >
      <Typography variant="caption" color="text.secondary">
        &copy; AlfaBeta – All Rights Reserved
      </Typography>
    </Stack>
  );
}


