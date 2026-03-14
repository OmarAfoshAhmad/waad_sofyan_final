// material-ui
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Skeleton from '@mui/material/Skeleton';

// project imports
import MainCard from 'components/MainCard';

// ===========================|| SKELETON - PRODUCT CARD ||=========================== //

export default function ProductPlaceholder() {
  return (
    <MainCard content={false} boxShadow>
      <Skeleton variant="rectangular" height={220} />
      <CardContent sx={{ p: '1.0rem' }}>
        <Grid container spacing={2}>
          <Grid size={12}>
            <Skeleton variant="rectangular" height={20} />
          </Grid>
          <Grid size={12}>
            <Skeleton variant="rectangular" height={45} />
          </Grid>
          <Grid sx={{ pt: '8px !important' }} size={12}>
            <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
              <Skeleton variant="rectangular" height={20} width={90} />
              <Skeleton variant="rectangular" height={20} width={38} />
            </Stack>
          </Grid>
          <Grid size={12}>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
              <Grid container spacing={1}>
                <Grid>
                  <Skeleton variant="rectangular" height={20} width={40} />
                </Grid>
                <Grid>
                  <Skeleton variant="rectangular" height={17} width={20} />
                </Grid>
              </Grid>
              <Skeleton variant="rectangular" height={32} width={47} />
            </Stack>
          </Grid>
        </Grid>
      </CardContent>
    </MainCard>
  );
}
