import { useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';
import Avatar from 'components/@extended/Avatar';

const avatarImage = '/assets/images/users/avatar-1.png';

// ==============================|| ACCOUNT PROFILE - BASIC ||============================== //

export default function TabProfile() {
  const [selectedImage, setSelectedImage] = useState(undefined);
  const [avatar, setAvatar] = useState(avatarImage);

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Profile Picture">
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={2.5} alignItems="center">
                <Avatar alt="Avatar" size="xl" src={avatar} />
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>

      <Grid size={12}>
        <MainCard title="Basic Information">
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Full Name</Typography>
                <TextField fullWidth placeholder="Enter full name" />
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Stack spacing={1}>
                <Typography variant="subtitle1">Email Address</Typography>
                <TextField fullWidth placeholder="Enter email address" />
              </Stack>
            </Grid>
            <Grid size={12}>
              <Stack direction="row" justifyContent="flex-end" spacing={2}>
                <Button variant="outlined" color="secondary">
                  Cancel
                </Button>
                <Button variant="contained">Update Profile</Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
