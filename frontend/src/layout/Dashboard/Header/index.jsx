import { useMemo } from 'react';

// material-ui
import useMediaQuery from '@mui/material/useMediaQuery';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';

// project imports
import AppBarStyled from './AppBarStyled';
import HeaderContent from './HeaderContent';
import IconButton from 'components/@extended/IconButton';

import useConfig from 'hooks/useConfig';
import { handlerDrawerOpen, useGetMenuMaster } from 'api/menu';
import { MenuOrientation, DRAWER_WIDTH, MINI_DRAWER_WIDTH } from 'config';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// assets
import MenuFoldOutlined from '@ant-design/icons/MenuFoldOutlined';
import MenuUnfoldOutlined from '@ant-design/icons/MenuUnfoldOutlined';

// ==============================|| MAIN LAYOUT - HEADER ||============================== //

export default function Header() {
  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));
  const { state } = useConfig();
  const { settings } = useCompanySettings();

  const { menuMaster } = useGetMenuMaster();
  const drawerOpen = menuMaster.isDashboardDrawerOpened;

  const isHorizontal = state.menuOrientation === MenuOrientation.HORIZONTAL && !downLG;

  const headerBg = settings?.primaryColor || '#00838F';

  // header content
  const headerContent = useMemo(() => <HeaderContent />, []);

  // common header - ✅ Reduced height to not cover page titles
  const mainHeader = (
    <Toolbar sx={{ px: { xs: 2, sm: 3 }, minHeight: { xs: 48, sm: '28.0rem' }, height: { xs: 48, sm: '28.0rem' } }}>
      {/* ✅ Sidebar toggle button removed - using horizontal navigation */}
      {headerContent}
    </Toolbar>
  );

  // app-bar params
  const appBar = {
    position: 'fixed',
    color: 'inherit',
    elevation: 0,
    sx: {
      bgcolor: headerBg,
      color: '#ffffff',
      borderBottom: '1px solid',
      borderBottomColor: 'rgba(255,255,255,0.15)',
      zIndex: 1200,
      width: '100%' // ✅ Full width - no sidebar offset
    }
  };

  return (
    <>
      {!downLG ? (
        <AppBarStyled open={drawerOpen} {...appBar}>
          {mainHeader}
        </AppBarStyled>
      ) : (
        <AppBar {...appBar}>{mainHeader}</AppBar>
      )}
    </>
  );
}

