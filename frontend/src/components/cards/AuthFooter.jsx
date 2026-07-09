// material-ui
import Container from '@mui/material/Container';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';

// icons

// ==============================|| FOOTER - AUTHENTICATION ||============================== //

// Landing page URL
const LANDING_PAGE_URL = import.meta.env.VITE_LANDING_PAGE_URL || '/';

export default function AuthFooter() {
  return (
    <Container maxWidth="xl">
      <Grid container spacing={2} justifyContent="space-between">
        <Grid size={{ xs: 12, md: 6 }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent={{ xs: 'center', md: 'flex-start' }}
            spacing={3}
            sx={{ textAlign: { xs: 'center', md: 'left' } }}
          >
            <Typography variant="caption" sx={{ opacity: 0.8, fontWeight: 500, color: 'text.secondary' }}>
              © 2026 نظام WaadCare لإدارة النفقات الطبية. جميع الحقوق محفوظة.
            </Typography>
          </Stack>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent={{ xs: 'center', md: 'flex-end' }}
            spacing={3}
            sx={{ textAlign: { xs: 'center', md: 'right' } }}
          >
            {/* Links removed as per user request */}
          </Stack>
        </Grid>
      </Grid>
    </Container>
  );
}
