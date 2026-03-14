import { useState } from 'react';

// material-ui
import Badge from '@mui/material/Badge';
import Drawer from '@mui/material/Drawer';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';

// project imports
import { notificationData } from './data';
import NotificationContent from './NotificationContent';
import IconButton from 'components/@extended/IconButton';

// assets
import BellOutlined from '@ant-design/icons/BellOutlined';

const unreadCount = notificationData.filter((n) => !n.read).length;

// ==============================|| HEADER CONTENT - NOTIFICATION ||============================== //

export default function NotificationMenu() {
  const [read, setRead] = useState(unreadCount);
  const [open, setOpen] = useState(false);
  const handleToggle = () => setOpen(!open);
  const handleMarkAllRead = () => setRead(0);

  const [filter, setFilter] = useState('all');
  const handleFilterChange = (value) => () => {
    setFilter(value);
  };

  // Logic to filter notifications
  const filteredNotifications = notificationData.filter((notification) => {
    if (!notification) return false;
    switch (filter) {
      case 'unread':
        return !notification.read;
      case 'mentions':
        return notification.type === 'mention';
      case 'all':
      default:
        return true;
    }
  });

  return (
    <>
      {/* Notification Icon Button */}
      <Box sx={{ flexShrink: 0 }}>
        <Tooltip title="Notification">
          <IconButton
            color="secondary"
            variant="light"
            sx={(theme) => ({
              color: 'text.primary',
              bgcolor: open ? 'grey.100' : 'transparent',
              ...theme.applyStyles('dark', { bgcolor: open ? 'background.default' : 'transparent' })
            })}
            aria-label="open notifications"
            onClick={handleToggle}
          >
            <Badge badgeContent={read} color="primary">
              <BellOutlined />
            </Badge>
          </IconButton>
        </Tooltip>
      </Box>

      {/* Notification Drawer */}
      <Drawer
        sx={{ zIndex: 2001 }}
        anchor="right"
        onClose={handleToggle}
        open={open}
        slotProps={{ paper: { sx: { width: { xs: 340, sm: '220.0rem' } } } }}
      >
        {open && (
          <NotificationContent
            notifications={filteredNotifications}
            filter={filter}
            setFilter={handleFilterChange}
            onMarkAllRead={handleMarkAllRead}
          />
        )}
      </Drawer>
    </>
  );
}
