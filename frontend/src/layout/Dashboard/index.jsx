import { useEffect } from 'react';
import { Outlet, useLocation, Navigate } from 'react-router-dom';

import useMediaQuery from '@mui/material/useMediaQuery';
import Container from '@mui/material/Container';
import Toolbar from '@mui/material/Toolbar';
import Box from '@mui/material/Box';

// project imports
import Drawer from './Drawer';
import Header from './Header';
import Footer from './Footer';
import Loader from 'components/Loader';
// import Breadcrumbs from 'components/@extended/Breadcrumbs'; // ✅ Disabled: Pages use ModernPageHeader with breadcrumbs
import useAuth from 'hooks/useAuth';
import PageErrorBoundary from 'components/SafeStates/PageErrorBoundary';

import { MenuOrientation } from 'config';
import useConfig from 'hooks/useConfig';
import { handlerDrawerOpen, useGetMenuMaster } from 'api/menu';

// ==============================|| MAIN LAYOUT - HORIZONTAL NAVIGATION ||============================== //

export default function DashboardLayout() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const { menuMasterLoading } = useGetMenuMaster();
  const downXL = useMediaQuery((theme) => theme.breakpoints.down('xl'));

  const { state } = useConfig();
  const isContainer = state.container;

  // ✅ Sidebar disabled - using horizontal navigation in header
  // set media wise responsive drawer
  // useEffect(() => {
  //   if (state.menuOrientation !== MenuOrientation.MINI_VERTICAL) {
  //     handlerDrawerOpen(!downXL);
  //   }
  // }, [downXL]);

  // ✅ FIXED: Conditional returns AFTER all hooks
  // Redirect to login if not authenticated
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (menuMasterLoading) return <Loader />;

  return (
    <Box sx={{ display: 'flex', width: '100%', flexDirection: 'column' }}>
      <Header />
      {/* ✅ Drawer/Sidebar removed - using horizontal navigation */}
      {/* <Drawer /> */}

      <Box component="main" sx={{ width: '100%', flexGrow: 1 }}>
        {/* ✅ Spacer matching reduced header height */}
        <Toolbar sx={{ minHeight: { xs: 48, sm: '28.0rem' }, height: { xs: 48, sm: '28.0rem' } }} />
        <Container
          maxWidth={isContainer ? 'xl' : false}
          sx={{
            // Professional UX: Optimized spacing for data density
            // Reduced top padding (16px desktop, 12px mobile) to minimize "white bar"
            pt: { xs: 1.5, sm: '1.0rem' },
            pb: { xs: 2, sm: 3 },
            px: { xs: 1.5, sm: '1.0rem' },
            ...(isContainer && { px: { xs: 0, sm: '1.0rem' } }),
            position: 'relative',
            minHeight: 'calc(100vh - 110px)',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          {/* ✅ Breadcrumbs disabled - Pages use ModernPageHeader with integrated breadcrumbs */}
          {/* {pathname !== '/apps/profiles/account/my-account' && <Breadcrumbs />} */}

          {/* 🛡️ PageErrorBoundary - Catches runtime errors in pages */}
          <PageErrorBoundary pageName="Dashboard Content">
            <Outlet />
          </PageErrorBoundary>

          <Footer />
        </Container>
      </Box>
    </Box>
  );
}

