// material-ui
import AvatarGroup from '@mui/material/AvatarGroup';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemAvatar from '@mui/material/ListItemAvatar';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

// project imports
import Avatar from 'components/@extended/Avatar';
import MainCard from 'components/MainCard';

// assets
import CheckOutlined from '@ant-design/icons/CheckOutlined';
import CloseOutlined from '@ant-design/icons/CloseOutlined';
import ClockCircleOutlined from '@ant-design/icons/ClockCircleOutlined';

import avatar1 from 'assets/images/users/avatar-1.png';
import avatar2 from 'assets/images/users/avatar-2.png';
import avatar3 from 'assets/images/users/avatar-3.png';
import avatar4 from 'assets/images/users/avatar-4.png';

// avatar style
const avatarSX = {
  width: '2.25rem',
  height: '2.25rem',
  fontSize: '1rem'
};

// action style
const actionSX = {
  mt: 0.75,
  ml: 1,
  top: 'auto',
  right: 'auto',
  alignSelf: 'flex-start',
  transform: 'none'
};

// ==============================|| TRANSACTION HISTORY ||============================== //

export default function TransactionHistory() {
  return (
    <>
      <Grid container sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Grid>
          <Typography variant="h5">Transaction History</Typography>
        </Grid>
        <Grid />
      </Grid>
      <MainCard sx={{ mt: '1.0rem' }} content={false}>
        <List
          component="nav"
          sx={{
            px: 0,
            py: 0,
            '& .MuiListItemButton-root': {
              py: '0.75rem',
              px: '1.0rem',
              '& .MuiAvatar-root': avatarSX,
              '& .MuiListItemSecondaryAction-root': { ...actionSX, position: 'relative' }
            }
          }}
        >
          <ListItem
            component={ListItemButton}
            divider
            secondaryAction={
              <Stack sx={{ alignItems: 'flex-end' }}>
                <Typography variant="subtitle1" noWrap>
                  + $1,430
                </Typography>
                <Typography variant="h6" color="secondary" noWrap>
                  78%
                </Typography>
              </Stack>
            }
          >
            <ListItemAvatar>
              <Avatar sx={{ color: 'success.main', bgcolor: 'success.lighter' }}>
                <CheckOutlined />
              </Avatar>
            </ListItemAvatar>
            <ListItemText primary={<Typography variant="subtitle1">Payment from #002434</Typography>} secondary="Today, 2:00 AM" />
          </ListItem>
          <ListItem
            component={ListItemButton}
            divider
            secondaryAction={
              <Stack sx={{ alignItems: 'flex-end' }}>
                <Typography variant="subtitle1" noWrap>
                  + $302
                </Typography>
                <Typography variant="h6" color="secondary" noWrap>
                  8%
                </Typography>
              </Stack>
            }
          >
            <ListItemAvatar>
              <Avatar sx={{ color: 'error.main', bgcolor: 'error.lighter' }}>
                <CloseOutlined />
              </Avatar>
            </ListItemAvatar>
            <ListItemText primary={<Typography variant="subtitle1">Payment from #002434</Typography>} secondary="Today 6:00 AM" />
          </ListItem>
          <ListItem
            component={ListItemButton}
            secondaryAction={
              <Stack sx={{ alignItems: 'flex-end' }}>
                <Typography variant="subtitle1" noWrap>
                  + $682
                </Typography>
                <Typography variant="h6" color="secondary" noWrap>
                  16%
                </Typography>
              </Stack>
            }
          >
            <ListItemAvatar>
              <Avatar sx={{ color: 'primary.main', bgcolor: 'primary.lighter' }}>
                <ClockCircleOutlined />
              </Avatar>
            </ListItemAvatar>
            <ListItemText primary={<Typography variant="subtitle1">Pending from #002435</Typography>} secondary="Today 2:00 AM" />
          </ListItem>
        </List>
      </MainCard>
      <MainCard sx={{ mt: '1.0rem' }}>
        <Stack sx={{ gap: '1.5rem' }}>
          <Grid container sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
            <Grid>
              <Stack>
                <Typography variant="h5" noWrap>
                  Help & Support Chat
                </Typography>
                <Typography variant="caption" color="secondary" noWrap>
                  Typical replay within 5 min
                </Typography>
              </Stack>
            </Grid>
            <Grid>
              <AvatarGroup sx={{ '& .MuiAvatar-root': { width: '2.0rem', height: '2.0rem' } }}>
                <Avatar alt="Remy Sharp" src={avatar1} />
                <Avatar alt="Travis Howard" src={avatar2} />
                <Avatar alt="Cindy Baker" src={avatar3} />
                <Avatar alt="Agnes Walker" src={avatar4} />
              </AvatarGroup>
            </Grid>
          </Grid>
          <Button size="small" variant="contained" sx={{ textTransform: 'capitalize', maxWidth: 'max-content', px: '1.125rem', py: 0.75 }}>
            Need Help?
          </Button>
        </Stack>
      </MainCard>
    </>
  );
}


