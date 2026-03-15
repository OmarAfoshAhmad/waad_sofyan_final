// material-ui
import Avatar from '@mui/material/Avatar';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Skeleton from '@mui/material/Skeleton';

// project imports
import MainCard from 'components/MainCard';

// assets
import ContactsOutlined from '@ant-design/icons/ContactsOutlined';

// ===========================|| SKELETON - USER EMPTY CARD ||=========================== //

export default function UserCard() {
  return (
    <MainCard
      border={false}
      content={false}
      boxShadow
      sx={{ boxShadow: `rgba(50, 50, 93, 0.25) 0px 13px 27px -5px, rgba(0, 0, 0, 0.3) 0px 8px 16px -8px`, borderRadius: '0.25rem' }}
    >
      <CardContent sx={{ p: '1.0rem' }}>
        <Grid container spacing={2}>
          <Grid size={12}>
            <Stack direction="row" sx={{ alignItems: 'center' }}>
              <Avatar>
                <ContactsOutlined style={{ visibility: 'inherit' }} />
              </Avatar>
              <Stack sx={{ width: '0.0625rem', pl: '1.25rem' }}>
                <Skeleton animation={false} height={20} width="80%" />
                <Skeleton animation={false} height={20} width="40%" />
              </Stack>
            </Stack>
          </Grid>
          <Grid size={12}>
            <Skeleton animation={false} height={20} width={45} />
            <Skeleton animation={false} height={20} />
            <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
              <Skeleton animation={false} height={20} width={90} />
              <Skeleton animation={false} height={20} width={38} />
            </Stack>
          </Grid>
          <Grid size={12}>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
              <Grid container spacing={1}>
                <Grid>
                  <Skeleton animation={false} height={20} width={40} />
                </Grid>
                <Grid>
                  <Skeleton animation={false} height={17} width={20} />
                </Grid>
              </Grid>
              <Skeleton animation={false} height={32} width={47} />
            </Stack>
          </Grid>
        </Grid>
      </CardContent>
    </MainCard>
  );
}
