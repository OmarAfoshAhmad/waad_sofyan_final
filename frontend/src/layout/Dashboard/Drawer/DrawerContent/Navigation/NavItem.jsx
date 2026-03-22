import PropTypes from 'prop-types';
import { Link, useLocation, matchPath } from 'react-router-dom';

// material-ui
import { useColorScheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import Dot from 'components/@extended/Dot';
import IconButton from 'components/@extended/IconButton';

import { MenuOrientation, ThemeMode, NavActionType } from 'config';
import useConfig from 'hooks/useConfig';
import { handlerDrawerOpen, useGetMenuMaster } from 'api/menu';

// ==============================|| NAVIGATION - LIST ITEM ||============================== //

export default function NavItem({ item, level, isParents = false, setSelectedID }) {
  const { menuMaster } = useGetMenuMaster();
  const drawerOpen = menuMaster.isDashboardDrawerOpened;

  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));
  const { colorScheme } = useColorScheme();
  const { state } = useConfig();

  let itemTarget = '_self';
  if (item.target) {
    itemTarget = '_blank';
  }

  const itemHandler = () => {
    if (downLG) handlerDrawerOpen(false);

    if (isParents && setSelectedID) {
      setSelectedID(item.id);
    }
  };

  const Icon = item.icon;
  const itemIcon = item.icon ? (
    <Icon
      style={{
        fontSize: drawerOpen ? '1rem' : '1.25rem',
        ...(state.menuOrientation === MenuOrientation.HORIZONTAL && isParents && { fontSize: '1.25rem', stroke: '1.5' })
      }}
    />
  ) : (
    false
  );

  const { pathname } = useLocation();
  const isSelected = !!matchPath({ path: item?.link ? item.link : item.url, end: false }, pathname);

  // Professional Dark Sidebar Colors
  const textColor = 'rgba(255, 255, 255, 0.7)';
  const iconSelectedColor = '#ffffff';
  const hoverBgColor = 'rgba(255, 255, 255, 0.05)';

  return (
    <>
      {state.menuOrientation !== MenuOrientation.HORIZONTAL || downLG ? (
        <Box sx={{ position: 'relative' }}>
          <ListItemButton
            component={Link}
            to={item.url}
            target={itemTarget}
            disabled={item.disabled}
            selected={isSelected}
            sx={(theme) => ({
              zIndex: 1201,
              pl: drawerOpen ? `${level * 28}px` : 1.5,
              py: 1,
              mb: 0.5, // Spacing between items
              mx: 1, // Side margins for button look
              borderRadius: '0.375rem', // Rounded corners
              color: textColor,
              transition: 'all 0.2s ease-in-out',

              ...(drawerOpen && {
                '&:hover': { bgcolor: hoverBgColor, color: '#fff' },
                '&.Mui-selected': {
                  bgcolor: 'primary.main',
                  color: '#fff',
                  '&:hover': { bgcolor: 'primary.dark' },
                  '& .MuiTypography-root': { fontWeight: 600, color: '#fff' },
                  '& .MuiListItemIcon-root': { color: '#fff' }
                }
              }),
              ...(!drawerOpen && {
                '&:hover': { bgcolor: 'transparent' },
                '&.Mui-selected': { '&:hover': { bgcolor: 'transparent' }, bgcolor: 'transparent' }
              })
            })}
            onClick={() => itemHandler()}
          >
            {itemIcon && (
              <ListItemIcon
                sx={(theme) => ({
                  minWidth: '1.75rem',
                  color: isSelected ? iconSelectedColor : textColor,
                  ...(!drawerOpen && {
                    borderRadius: '0.375rem',
                    width: '2.25rem',
                    height: '2.25rem',
                    alignItems: 'center',
                    justifyContent: 'center',
                    '&:hover': { bgcolor: 'secondary.lighter', ...theme.applyStyles('dark', { bgcolor: 'secondary.light' }) }
                  }),
                  ...(!drawerOpen &&
                    isSelected && {
                      bgcolor: 'primary.lighter',
                      ...theme.applyStyles('dark', { bgcolor: 'primary.900' }),
                      '&:hover': { bgcolor: 'primary.lighter', ...theme.applyStyles('dark', { bgcolor: 'primary.darker' }) }
                    })
                })}
              >
                {itemIcon}
              </ListItemIcon>
            )}
            {(drawerOpen || (!drawerOpen && level !== 1)) && (
              <ListItemText
                primary={
                  <Typography variant="h6" sx={{ color: isSelected ? iconSelectedColor : textColor }}>
                    {item.title}
                  </Typography>
                }
              />
            )}
            {(drawerOpen || (!drawerOpen && level !== 1)) && item.chip && (
              <Chip
                color={item.chip.color}
                variant={item.chip.variant}
                size={item.chip.size}
                label={item.chip.label}
                avatar={item.chip.avatar && <Avatar>{item.chip.avatar}</Avatar>}
              />
            )}
          </ListItemButton>
          {(drawerOpen || (!drawerOpen && level !== 1)) &&
            item?.actions &&
            item?.actions.map((action, index) => {
              const ActionIcon = action.icon;
              const callAction = action?.function;
              return (
                <IconButton
                  key={index}
                  {...(action.type === NavActionType.FUNCTION && {
                    onClick: (event) => {
                      event.stopPropagation();
                      callAction();
                    }
                  })}
                  {...(action.type === NavActionType.LINK && {
                    component: Link,
                    to: action.url,
                    target: action.target ? '_blank' : '_self'
                  })}
                  color="secondary"
                  variant="outlined"
                  sx={{
                    position: 'absolute',
                    top: '6.0rem',
                    right: '1.25rem',
                    zIndex: 1202,
                    width: '1.25rem',
                    height: '1.25rem',
                    mr: -1,
                    ml: 1,
                    color: 'secondary.dark',
                    borderColor: isSelected ? 'primary.light' : 'secondary.light',
                    '&:hover': { borderColor: isSelected ? 'primary.main' : 'secondary.main' }
                  }}
                >
                  <ActionIcon style={{ fontSize: '0.75rem' }} />
                </IconButton>
              );
            })}
        </Box>
      ) : (
        <ListItemButton
          component={Link}
          to={item.url}
          target={itemTarget}
          disabled={item.disabled}
          selected={isSelected}
          onClick={() => itemHandler()}
          sx={{
            zIndex: 1201,
            borderRadius: '0.375rem', // Rounded Button
            mx: 0.5, // Spacing between buttons
            px: '1.0rem',
            py: 1,
            color: isSelected ? '#fff' : 'text.secondary',
            bgcolor: isSelected ? '#0066e6' : 'transparent', // Blue when selected
            border: isSelected ? 'none' : '1px solid transparent',
            transition: 'all 0.2s',

            '&:hover': {
              bgcolor: isSelected ? '#0052b8' : 'rgba(0, 102, 230, 0.08)', // Light blue hover
              color: isSelected ? '#fff' : '#0066e6'
            },
            '&.Mui-selected': {
              bgcolor: '#0066e6',
              color: '#fff',
              '&:hover': { bgcolor: '#0052b8' }
            },

            ...(isParents && { p: 1, mr: 1 })
          }}
        >
          {itemIcon && (
            <ListItemIcon
              sx={{
                minWidth: '1.75rem',
                color: isSelected ? '#fff' : 'inherit', // Icon color fix
                ...(!drawerOpen && {
                  borderRadius: '0.375rem',
                  width: '1.75rem',
                  height: '1.75rem',
                  alignItems: 'center',
                  justifyContent: 'flex-start',
                  '&:hover': { bgcolor: 'transparent' }
                }),
                ...(!drawerOpen && isSelected && { bgcolor: 'transparent', '&:hover': { bgcolor: 'transparent' } })
              }}
            >
              {itemIcon}
            </ListItemIcon>
          )}

          {!itemIcon && (
            <ListItemIcon
              sx={{
                color: isSelected ? 'primary.main' : 'secondary.dark',
                ...(!drawerOpen && {
                  borderRadius: '0.375rem',
                  alignItems: 'center',
                  justifyContent: 'flex-start',
                  '&:hover': { bgcolor: 'transparent' }
                }),
                ...(!drawerOpen && isSelected && { bgcolor: 'transparent', '&:hover': { bgcolor: 'transparent' } })
              }}
            >
              <Dot size={4} color={isSelected ? 'primary' : 'secondary'} />
            </ListItemIcon>
          )}
          <ListItemText
            primary={
              <Typography variant="h6" sx={{ color: isSelected ? '#fff' : 'text.primary', fontWeight: isSelected ? 600 : 400 }}>
                {item.title}
              </Typography>
            }
          />
          {(drawerOpen || (!drawerOpen && level !== 1)) && item.chip && (
            <Chip
              color={item.chip.color}
              variant={item.chip.variant}
              size={item.chip.size}
              label={item.chip.label}
              avatar={item.chip.avatar && <Avatar>{item.chip.avatar}</Avatar>}
            />
          )}
        </ListItemButton>
      )}
    </>
  );
}

NavItem.propTypes = {
  item: PropTypes.any,
  level: PropTypes.number,
  isParents: PropTypes.bool,
  setSelectedID: PropTypes.oneOfType([PropTypes.any, PropTypes.func])
};




