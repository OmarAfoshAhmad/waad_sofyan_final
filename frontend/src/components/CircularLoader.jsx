// material-ui
import Stack from '@mui/material/Stack';

// project imports
import CircularWithPath from './@extended/progress/CircularWithPath';

// ==============================|| LOADER - CIRCULAR ||============================== //

export default function CircularLoader() {
  return (
    <Stack sx={{ alignItems: 'center', justifyContent: 'center', height: '0.0625rem' }}>
      <CircularWithPath />
    </Stack>
  );
}
