// material-ui
import Grid from '@mui/material/Grid';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import MainCard from 'components/MainCard';

// ==============================|| PAGE VIEWS BY PAGE TITLE ||============================== //

export default function PageViews() {
  return (
    <>
      <Grid container sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Grid>
          <Typography variant="h5">Page Views by Page Title</Typography>
        </Grid>
        <Grid />
      </Grid>
      <MainCard sx={{ mt: '1.0rem' }} content={false}>
        <List sx={{ p: 0, '& .MuiListItemButton-root': { py: '1.0rem' } }}>
          <ListItemButton divider>
            <ListItemText
              primary="Admin Home"
              secondary="/demo/admin/index.html"
              slotProps={{
                primary: { variant: 'subtitle1' },
                secondary: { variant: 'body1', color: 'text.secondary', sx: { display: 'inline' } }
              }}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography variant="h5" color="primary">
                7755
              </Typography>
              <Typography variant="body2" color="text.secondary">
                31.74% (-100.00%)
              </Typography>
            </Stack>
          </ListItemButton>
          <ListItemButton divider>
            <ListItemText
              primary="Form Elements"
              secondary="/demo/admin/forms.html"
              slotProps={{
                primary: { variant: 'subtitle1' },
                secondary: { variant: 'body1', color: 'text.secondary', sx: { display: 'inline' } }
              }}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography variant="h5" color="primary">
                5215
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ display: 'block' }}>
                28.53% (-100.00%)
              </Typography>
            </Stack>
          </ListItemButton>
          <ListItemButton divider>
            <ListItemText
              primary="Utilities"
              secondary="/demo/admin/util.html"
              slotProps={{
                primary: { variant: 'subtitle1' },
                secondary: { variant: 'body1', color: 'text.secondary', sx: { display: 'inline' } }
              }}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography variant="h5" color="primary">
                4848
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ display: 'block' }}>
                25.35% (-100.00%)
              </Typography>
            </Stack>
          </ListItemButton>
          <ListItemButton divider>
            <ListItemText
              primary="Form Validation"
              secondary="/demo/admin/validation.html"
              slotProps={{
                primary: { variant: 'subtitle1' },
                secondary: { variant: 'body1', color: 'text.secondary', sx: { display: 'inline' } }
              }}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography variant="h5" color="primary">
                3275
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ display: 'block' }}>
                23.17% (-100.00%)
              </Typography>
            </Stack>
          </ListItemButton>
          <ListItemButton divider>
            <ListItemText
              primary="Modals"
              secondary="/demo/admin/modals.html"
              slotProps={{
                primary: { variant: 'subtitle1' },
                secondary: { variant: 'body1', color: 'text.secondary', sx: { display: 'inline' } }
              }}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography variant="h5" color="primary">
                3003
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ display: 'block' }}>
                22.21% (-100.00%)
              </Typography>
            </Stack>
          </ListItemButton>
        </List>
      </MainCard>
    </>
  );
}
