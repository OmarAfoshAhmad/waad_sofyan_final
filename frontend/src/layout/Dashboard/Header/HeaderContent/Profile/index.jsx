import PropTypes from 'prop-types';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';

// material-ui
import { useTheme } from '@mui/material/styles';
import ButtonBase from '@mui/material/ButtonBase';
import CardContent from '@mui/material/CardContent';
import ClickAwayListener from '@mui/material/ClickAwayListener';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Popper from '@mui/material/Popper';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// project imports
import ProfileTab from './ProfileTab';
import Avatar from 'components/@extended/Avatar';
import MainCard from 'components/MainCard';
import Transitions from 'components/@extended/Transitions';

import useAuth from 'hooks/useAuth';

// ==============================|| ROLE NAME TRANSLATIONS ||============================== //

const ROLE_TRANSLATIONS = {
  SUPER_ADMIN: 'مدير النظام',
  ACCOUNTANT: 'محاسب',
  MEDICAL_REVIEWER: 'مراجع طبي',
  PROVIDER_STAFF: 'موظف مقدم خدمة',
  EMPLOYER_ADMIN: 'مدير جهة العمل',
  DATA_ENTRY: 'مدخل بيانات',
  FINANCE_VIEWER: 'مشاهد مالي'
};

/**
 * Get translated role name from roles array
 * @param {string[]} roles - Array of role names
 * @returns {string} Translated role name in Arabic
 */
const getTranslatedRole = (roles) => {
  if (!roles || !Array.isArray(roles) || roles.length === 0) {
    return 'مستخدم النظام';
  }
  const primaryRole = roles[0];
  return ROLE_TRANSLATIONS[primaryRole] || primaryRole;
};

// ==============================|| HEADER CONTENT - PROFILE ||============================== //
// Simplified: Avatar with fallback + Profile/Logout menu only

// Generate deterministic color from string
function stringToColor(string) {
  let hash = 0;
  let i;

  for (i = 0; i < string.length; i += 1) {
    hash = string.charCodeAt(i) + ((hash << 5) - hash);
  }

  let color = '#';

  for (i = 0; i < 3; i += 1) {
    const value = (hash >> (i * 8)) & 0xff;
    color += `00${value.toString(16)}`.slice(-2);
  }

  return color;
}

// Get avatar props with fallback
function getAvatarProps(user) {
  if (user?.profileImageUrl) {
    return { src: user.profileImageUrl };
  }

  // Fallback to first letter
  const name = user?.fullName || user?.name || user?.username || 'U';
  const firstLetter = name.charAt(0).toUpperCase();
  const bgColor = stringToColor(user?.username || 'default');

  return {
    sx: { bgcolor: bgColor, color: '#fff' },
    children: firstLetter
  };
}

export default function Profile() {
  const theme = useTheme();
  const navigate = useNavigate();

  const { logout, user } = useAuth();
  const handleLogout = async () => {
    try {
      await logout();
      navigate(`/login`, {
        state: {
          from: ''
        }
      });
    } catch (err) {
      console.error(err);
    }
  };

  const anchorRef = useRef(null);
  const [open, setOpen] = useState(false);
  const handleToggle = () => {
    setOpen((prevOpen) => !prevOpen);
  };

  const handleClose = (event) => {
    if (anchorRef.current && anchorRef.current.contains(event.target)) {
      return;
    }
    setOpen(false);
  };

  const avatarProps = getAvatarProps(user);

  return (
    <Box sx={{ flexShrink: 0, ml: 'auto' }}>
      <Tooltip title="الملف الشخصي" disableInteractive>
        <ButtonBase
          sx={(theme) => ({
            p: 0.25,
            borderRadius: 1,
            '&:focus-visible': { outline: `2px solid ${theme.vars.palette.secondary.dark}`, outlineOffset: 2 }
          })}
          aria-label="open profile"
          ref={anchorRef}
          aria-controls={open ? 'profile-grow' : undefined}
          aria-haspopup="true"
          onClick={handleToggle}
        >
          <Avatar
            alt="profile user"
            {...avatarProps}
            size="sm"
            sx={{
              '&:hover': { outline: '1px solid', outlineColor: 'primary.main' },
              ...avatarProps.sx
            }}
          />
        </ButtonBase>
      </Tooltip>
      <Popper
        placement="bottom-end"
        open={open}
        anchorEl={anchorRef.current}
        role={undefined}
        transition
        disablePortal
        popperOptions={{
          modifiers: [
            {
              name: 'offset',
              options: {
                offset: [0, 9]
              }
            }
          ]
        }}
      >
        {({ TransitionProps }) => (
          <Transitions type="grow" position="top-right" in={open} {...TransitionProps}>
            <Paper sx={(theme) => ({ boxShadow: theme.vars.customShadows.z1, width: '18rem', minWidth: '15rem', maxWidth: { xs: '15.5rem', md: '18rem' } })}>
              <ClickAwayListener onClickAway={handleClose}>
                <MainCard elevation={0} border={false} content={false}>
                  <CardContent sx={{ px: '1.25rem', pt: '1.5rem', pb: '1.0rem' }}>
                    <Stack direction="row" sx={{ gap: '0.75rem', alignItems: 'center', mb: '1.0rem' }}>
                      <Avatar alt="profile user" {...avatarProps} sx={{ width: '3rem', height: '3rem', ...avatarProps.sx }} />
                      <Stack>
                        <Typography variant="h6">{user?.fullName || user?.name || user?.username}</Typography>
                        {user?.providerName && (
                          <Typography variant="caption" color="primary.main" fontWeight="medium">
                            {user.providerName}
                          </Typography>
                        )}
                        <Typography variant="body2" color="text.secondary">
                          {getTranslatedRole(user?.roles)}
                        </Typography>
                      </Stack>
                    </Stack>
                  </CardContent>

                  <ProfileTab handleLogout={handleLogout} />
                </MainCard>
              </ClickAwayListener>
            </Paper>
          </Transitions>
        )}
      </Popper>
    </Box>
  );
}


