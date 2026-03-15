import { RouterProvider } from 'react-router-dom';
import { Suspense } from 'react';

// MUI X Date Pickers
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';

// project imports
import router from 'routes';
import ThemeCustomization from 'themes';

import Locales from 'components/Locales';
import RTLLayout from 'components/RTLLayout';
import ScrollTop from 'components/ScrollTop';
import Snackbar from 'components/@extended/Snackbar';
import Notistack from 'components/third-party/Notistack';
import Metrics from 'metrics';
import Loader from 'components/Loader';

// Error Boundary - Production Safety
import { SystemErrorBoundary } from 'components/ErrorBoundary';

// auth-provider
import { AuthProvider } from 'contexts/AuthContext';
import { EmployerFilterProvider } from 'contexts/EmployerFilterContext';
import { CompanySettingsProvider } from 'contexts/CompanySettingsContext';
import { ThemeModeProvider } from 'contexts/ThemeModeContext';
import { GlobalImportProgressProvider } from 'contexts/GlobalImportProgressContext';
import { SystemConfigProvider } from 'contexts/SystemConfigContext';

// Production console cleanup
import { suppressMUIDeprecationWarnings } from 'utils/gridMigration';

// Initialize PDF Worker
import 'utils/pdfWorker';

// Initialize production console cleanup (handles deprecations and Emotion warnings)
suppressMUIDeprecationWarnings();

// ==============================|| APP - THEME, ROUTER, LOCAL ||============================== //
// CompanySettingsProvider - SINGLE SOURCE OF TRUTH for company branding
// Loaded ONCE at startup, consumed by Header, Logo, Exports

export default function App() {
  return (
    <SystemErrorBoundary>
      <ThemeCustomization>
        <RTLLayout>
          <Locales>
            <LocalizationProvider dateAdapter={AdapterDayjs}>
              <ScrollTop>
                <AuthProvider>
                  <CompanySettingsProvider>
                    <SystemConfigProvider>
                    <EmployerFilterProvider>
                      <ThemeModeProvider>
                        <GlobalImportProgressProvider>
                          <Notistack>
                            <Suspense fallback={<Loader />}>
                              <RouterProvider router={router} />
                            </Suspense>
                            <Snackbar />
                          </Notistack>
                        </GlobalImportProgressProvider>
                      </ThemeModeProvider>
                    </EmployerFilterProvider>
                    </SystemConfigProvider>
                  </CompanySettingsProvider>
                </AuthProvider>
              </ScrollTop>
            </LocalizationProvider>
          </Locales>
        </RTLLayout>
      </ThemeCustomization>
      <Metrics />
    </SystemErrorBoundary>
  );
}
