import PropTypes from 'prop-types';
// material-ui
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemAvatar from '@mui/material/ListItemAvatar';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

import Avatar from 'components/@extended/Avatar';
import Dot from 'components/@extended/Dot';

import { getImageUrl, ImagePath } from 'utils/getImageUrl';

// ==============================|| NOTIFICATION - ITEMS ||============================== //

export default function NotificationItem({ notification }) {
  const { user, message, time, context, read, file, actions, type } = notification;

  // Renders the file attachment details for 'file_upload' type
  const renderFileAttachment = () => {
    if (type !== 'file_upload' || !file) return null;
    return (
      <Stack direction="row" sx={{ gap: 0.75, alignItems: 'center' }}>
        <Avatar
          alt={file.name}
          src={file.icon}
          size="xs"
          color="secondary"
          variant="rounded"
          type="combined"
          sx={{ p: 0.125, width: '1.5rem', height: '1.5rem' }}
        />
        <Typography variant="body2">{file.name}</Typography>
        <Typography variant="body2" color="secondary">
          {file.size}
        </Typography>
      </Stack>
    );
  };

  // Renders action buttons or alerts
  const renderActions = () => {
    if (!actions) return null;
    return (
      <Stack component="span" direction="row" spacing={1}>
        {actions.map((action, index) => (
          <Box key={index}>
            {action.type === 'button' && <Button component="span" {...action?.buttonProps} />}
            {action.type === 'alert' && <Alert component="span" {...action?.alertProps} />}
          </Box>
        ))}
      </Stack>
    );
  };

  return (
    <ListItemButton key={notification.id} sx={{ p: { xs: 1.25, sm: '1.125rem' }, alignItems: 'flex-start' }} divider>
      <ListItemAvatar>
        <Avatar alt={user?.name} src={user?.avatar && getImageUrl(`${user?.avatar}`, ImagePath.USERS)} />
      </ListItemAvatar>
      <ListItemText
        disableTypography
        primary={
          <Typography
            variant="subtitle1"
            sx={{
              display: 'flex',
              gap: 0.25,
              flexDirection: 'row',
              alignItems: 'center',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: { xs: 180, sm: '160.0rem' }
            }}
          >
            {user?.name}
            <Typography variant="body1">{message}</Typography>
            {type === 'mention' && 'AlWahaCare.'}
          </Typography>
        }
        secondary={
          <Stack component="span" sx={{ gap: '0.75rem' }}>
            <Typography
              component="span"
              variant="caption"
              sx={{ display: 'flex', flexDirection: 'row', gap: 1, alignItems: 'center', color: 'text.secondary' }}
            >
              {time}
              {context && <Dot component="span" size={3} color="secondary" />}
              {context}
            </Typography>
            {renderFileAttachment()}
            {renderActions()}
          </Stack>
        }
        sx={{ my: 0 }}
      />
      {!read && <Dot size={6} style={{ marginTop: '10.0rem' }} />}
    </ListItemButton>
  );
}

NotificationItem.propTypes = { notification: PropTypes.any };


