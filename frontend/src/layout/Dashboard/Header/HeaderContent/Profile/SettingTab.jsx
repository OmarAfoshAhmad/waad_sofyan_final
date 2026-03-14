import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

// material-ui
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// assets
import SettingOutlined from '@ant-design/icons/SettingOutlined';
import SafetyOutlined from '@ant-design/icons/SafetyOutlined';

// ==============================|| HEADER PROFILE - SETTING TAB ||============================== //
// Phase D0: Simplified - removed demo links (Support, Privacy, Feedback, History)

export default function SettingTab() {
  const navigate = useNavigate();
  const location = useLocation();
  const [selectedIndex, setSelectedIndex] = useState(0);

  const handleListItemClick = (event, index, route = '') => {
    setSelectedIndex(index);

    if (route && route !== '') {
      navigate(route);
    }
  };

  useEffect(() => {
    const pathToIndex = {
      '/settings': 0
    };

    setSelectedIndex(pathToIndex[location.pathname] ?? undefined);
  }, [location.pathname]);

  return (
    <List component="nav" sx={{ p: 0, '& .MuiListItemIcon-root': { minWidth: '2.0rem' } }}>
      <ListItemButton selected={selectedIndex === 0} onClick={(event) => handleListItemClick(event, 0, '/settings')}>
        <ListItemIcon>
          <SettingOutlined />
        </ListItemIcon>
        <ListItemText primary="إعدادات النظام" />
      </ListItemButton>
      <Box sx={{ p: '1.0rem', pt: '0.75rem' }}>
        <Typography variant="caption" color="text.secondary">
          نظام وعد - إدارة مطالبات التأمين الصحي
        </Typography>
      </Box>
    </List>
  );
}


