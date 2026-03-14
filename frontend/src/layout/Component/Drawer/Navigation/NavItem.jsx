import PropTypes from 'prop-types';
import { Link, matchPath, useLocation } from 'react-router-dom';

// material-ui
import useMediaQuery from '@mui/material/useMediaQuery';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';

// project imports
import { handlerComponentDrawer } from 'api/menu';

export default function NavItem({ item }) {
  const { pathname } = useLocation();
  const downMD = useMediaQuery((theme) => theme.breakpoints.down('md'));

  let itemTarget = '_self';
  if (item.target) {
    itemTarget = '_blank';
  }

  const itemHandler = (id) => {
    if (downMD) handlerComponentDrawer(false);
  };

  const isSelectedItem = !!matchPath({ path: item.url, end: true }, pathname);

  return (
    <ListItemButton
      component={Link}
      to={item.url}
      target={itemTarget}
      disabled={item.disabled}
      onClick={() => itemHandler(item.id)}
      selected={isSelectedItem}
      sx={(theme) => ({
        pl: '2.0rem',
        py: 1,
        mb: 0.5,
        '&:hover': { bgcolor: 'primary.lighter', ...theme.applyStyles('dark', { bgcolor: 'divider' }) },
        '&.Mui-selected': {
          bgcolor: 'primary.lighter',
          borderRight: '2px solid',
          borderColor: 'primary.main',
          '&:hover': 'primary.lighter',
          ...theme.applyStyles('dark', {
            bgcolor: 'divider',
            '&:hover': { bgcolor: 'divider' }
          })
        }
      })}
    >
      <ListItemText
        primary={
          <Typography
            variant="h6"
            sx={(theme) => ({
              color: isSelectedItem ? 'primary.main' : 'text.primary',
              ...theme.applyStyles('dark', { color: isSelectedItem ? 'text.primary' : 'grey.400' })
            })}
          >
            {item.title}
          </Typography>
        }
      />
      {item.chip && (
        <Chip
          color={item.chip.color}
          variant={item.chip.variant}
          size={item.chip.size}
          label={item.chip.label}
          avatar={item.chip.avatar && <Avatar>{item.chip.avatar}</Avatar>}
        />
      )}
    </ListItemButton>
  );
}

NavItem.propTypes = { item: PropTypes.any };
