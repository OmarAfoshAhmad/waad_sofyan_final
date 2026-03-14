import { lazy, Suspense } from 'react';

// material-ui
import Container from '@mui/material/Container';
import Toolbar from '@mui/material/Toolbar';

// project imports
import Loader from 'components/Loader';
import ComponentLayoutPage from './ComponentLayout';
import { handlerComponentDrawer, useGetMenuMaster } from 'api/menu';

const Header = lazy(() => import('components/pages/Header'));
const Footer = lazy(() => import('components/pages/Footer'));

// ==============================|| COMPONENTS LAYOUT ||============================== //

export default function ComponentLayout() {
  const { menuMasterLoading, menuMaster } = useGetMenuMaster();
  if (menuMasterLoading) return <Loader />;

  return (
    <Suspense fallback={<Loader />}>
      <Container maxWidth="lg" sx={{ px: { xs: 0, sm: '1.0rem' } }}>
        <Header
          variant="component"
          enableComponentDrawer={true}
          onComponentDrawerToggle={handlerComponentDrawer}
          isComponentDrawerOpened={menuMaster.isComponentDrawerOpened}
        />
        <Toolbar sx={{ my: '1.0rem' }} />
        <ComponentLayoutPage />
      </Container>
      <Footer />
    </Suspense>
  );
}
