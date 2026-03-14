import Button from '@mui/material/Button';

// assets
import figma from 'assets/images/icons/landing-figma.svg';

export const notificationFilterOptions = [
  { key: 'all', label: 'All' },
  { key: 'unread', label: 'Unread' },
  { key: 'mentions', label: 'Mentions' }
];

// mock notification data
export const notificationData = [
  {
    id: 'notification-1',
    type: 'mention',
    user: {
      name: 'Cristina Danny',
      avatar: 'avatar-1.png'
    },
    message: 'mentioned you in',
    time: '12 min ago',
    context: 'Project',
    read: true
  },
  {
    id: 'notification-2',
    type: 'join_event',
    user: {
      name: 'Aida Burg',
      avatar: 'avatar-2.png'
    },
    message: 'joined to 🔥 Final Presentation',
    time: '2h ago',
    context: 'Social Media Plan',
    read: false
  },
  {
    id: 'notification-3',
    type: 'request',
    user: {
      name: 'Jenny Willson',
      avatar: 'avatar-1.jpg'
    },
    message: 'is requesting to Upgrade Plan',
    time: '5h ago',
    context: 'Pricing Plan',
    read: true,
    actions: [
      {
        type: 'button',
        action_id: 'accept_upgrade',
        buttonProps: {
          variant: 'contained',
          color: 'primary',
          size: 'small',
          children: 'Accept'
        }
      },
      {
        type: 'button',
        action_id: 'decline_upgrade',
        buttonProps: {
          variant: 'outlined',
          color: 'secondary',
          size: 'small',
          children: 'Decline'
        }
      }
    ]
  },
  {
    id: 'notification-4',
    type: 'file_upload',
    user: {
      name: 'Robert Fox',
      avatar: 'avatar-3.png'
    },
    message: 'upload a file',
    time: '1d ago',
    read: false,
    file: {
      name: 'alwahacare_dashboard.fig',
      size: '26mb',
      icon: figma
    }
  },
  {
    id: 'notification-5',
    type: 'reply',
    user: {
      name: 'Emily',
      avatar: 'avatar-5.png'
    },
    message: 'sent you reply',
    time: '1d ago',
    read: true,
    actions: [
      {
        type: 'alert',
        action_id: 'reply_to_emily',
        alertProps: {
          variant: 'border',
          color: 'secondary',
          children: 'We have scheduled a meeting for next week.',
          icon: false,
          action: <Button variant="contained">Replay</Button>,
          sx: { px: '0.875rem', py: 0.75, fontSize: '0.75rem' }
        }
      }
    ]
  },
  {
    id: 'notification-6',
    type: 'task_completion',
    user: {
      name: 'Annete Black',
      avatar: 'avatar-a.png'
    },
    message: 'completed Create new component',
    time: '2d ago',
    read: true
  },
  {
    id: 'notification-7',
    type: 'invitation',
    user: {
      name: 'Ian Dooley',
      avatar: 'avatar-7.png'
    },
    message: 'invited to you join Meeting',
    time: '4d ago',
    read: true
  },
  {
    id: 'notification-8',
    type: 'system_alert',
    user: {
      name: 'Cat central',
      avatar: 'avatar-a.png'
    },
    message: 'There was a failure to your setup',
    time: '4d ago',
    read: true
  }
];


