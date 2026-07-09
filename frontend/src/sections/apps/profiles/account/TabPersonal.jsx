// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| ACCOUNT PROFILE - PERSONAL ||============================== //

export default function TabPersonal() {
  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Personal Information">
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Phone Number</Typography>
                <TextField fullWidth placeholder="Enter phone number" />
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Date of Birth</Typography>
                <TextField fullWidth type="date" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Address</Typography>
                <TextField fullWidth multiline rows={3} placeholder="Enter address" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" spacing={2}>
                <Button variant="outlined" color="secondary">
                  Cancel
                </Button>
                <Button variant="contained">Save Changes</Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
