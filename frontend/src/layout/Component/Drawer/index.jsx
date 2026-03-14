import { useState } from 'react';

// material-ui
import useMediaQuery from '@mui/material/useMediaQuery';
import MuiDrawer from '@mui/material/Drawer';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';

// project imports
import DrawerContent from './DrawerContent';
import MainCard from 'components/MainCard';

import { DRAWER_WIDTH } from 'config';
import { handlerComponentDrawer, useGetMenuMaster } from 'api/menu';

// assets
import SearchOutlined from '@ant-design/icons/SearchOutlined';

// ==============================|| COMPONENTS - DRAWER ||============================== //

export default function Drawer() {
  const downMD = useMediaQuery((theme) => theme.breakpoints.down('md'));

  const { menuMaster } = useGetMenuMaster();
  const open = menuMaster.isComponentDrawerOpened;

  const [searchValue, setSearchValue] = useState();

  const handleSearchValue = (event) => {
    const search = event.target.value.trim().toLowerCase();
    setSearchValue(search);
  };

  return (
    <MuiDrawer
      sx={{
        display: { xs: open ? 'block' : 'none' },
        width: DRAWER_WIDTH,
        flexShrink: 0,
        position: { xs: 'fixed', md: 'sticky' },
        top: { xs: 0, md: 84, xl: 90 },
        height: { xs: 'auto', md: 'calc(100vh - 140px)', xl: 'calc(100vh - 176px)' }
      }}
      variant={downMD ? 'temporary' : 'persistent'}
      anchor="left"
      open={open}
      ModalProps={{ keepMounted: true }}
      slotProps={{ paper: { sx: { position: 'relative', border: 'none' } } }}
      onClose={() => handlerComponentDrawer(false)}
    >
      <MainCard sx={{ height: '100%' }} content={false}>
        <Box sx={{ p: '1.0rem' }}>
          <TextField
            fullWidth
            sx={(theme) => ({
              borderRadius: '0.375rem',
              bgcolor: 'background.paper',
              boxShadow: theme.vars.customShadows.primary,
              border: '1px solid',
              borderColor: 'primary.main'
            })}
            slotProps={{
              input: {
                startAdornment: <SearchOutlined />,
                placeholder: 'Search Components',
                type: 'search'
              }
            }}
            onChange={handleSearchValue}
          />
        </Box>
        <DrawerContent searchValue={searchValue} />
      </MainCard>
    </MuiDrawer>
  );
}

