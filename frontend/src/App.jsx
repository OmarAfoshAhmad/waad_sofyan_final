import { RouterProvider } from 'react-router-dom';
import { Suspense } from 'react';

// project imports
import router from 'routes';
import Snackbar from 'components/@extended/Snackbar';
import Metrics from 'metrics';
import Loader from 'components/Loader';
import { AppProviders } from 'contexts/AppProviders';

// Production console cleanup
import { suppressMUIDeprecationWarnings } from 'utils/gridMigration';

// Initialize PDF Worker
import 'utils/pdfWorker';

// Initialize production console cleanup (handles deprecations and Emotion warnings)
suppressMUIDeprecationWarnings();

// ==============================|| APP - THEME, ROUTER, LOCAL ||============================== //
// AppProviders abstracts away the deeply nested context tree
// It encapsulates everything from ThemeCustomization to AuthProvider

export default function App() {
  return (
    <>
      <AppProviders>
        <Suspense fallback={<Loader />}>
          <RouterProvider router={router} />
        </Suspense>
        <Snackbar />
      </AppProviders>
      <Metrics />
    </>
  );
}
