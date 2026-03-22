import React from 'react';

// MUI X Date Pickers
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';

// Global UI Components & Styling
import ThemeCustomization from 'themes';
import Locales from 'components/Locales';
import RTLLayout from 'components/RTLLayout';
import ScrollTop from 'components/ScrollTop';
import Notistack from 'components/third-party/Notistack';
import { AppearanceInjector } from 'components/AppearanceInjector';
import { SystemErrorBoundary } from 'components/ErrorBoundary';

// Global State Contexts
import { AuthProvider } from 'contexts/AuthContext';
import { EmployerFilterProvider } from 'contexts/EmployerFilterContext';
import { CompanySettingsProvider } from 'contexts/CompanySettingsContext';
import { ThemeModeProvider } from 'contexts/ThemeModeContext';
import { GlobalImportProgressProvider } from 'contexts/GlobalImportProgressContext';
import { SystemConfigProvider } from 'contexts/SystemConfigContext';

/**
 * AppProviders centralizes the deeply nested React Context providers
 * into a single component, solving the "Provider Hell" anti-pattern 
 * and keeping the root App.jsx clean and declarative.
 */
export const AppProviders = ({ children }) => {
  return (
    <SystemErrorBoundary>
      <CompanySettingsProvider>
        <ThemeCustomization>
          <RTLLayout>
            <Locales>
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                <ScrollTop>
                  <AuthProvider>
                    <AppearanceInjector />
                    <SystemConfigProvider>
                      <EmployerFilterProvider>
                        <ThemeModeProvider>
                          <GlobalImportProgressProvider>
                            <Notistack>
                              {children}
                            </Notistack>
                          </GlobalImportProgressProvider>
                        </ThemeModeProvider>
                      </EmployerFilterProvider>
                    </SystemConfigProvider>
                  </AuthProvider>
                </ScrollTop>
              </LocalizationProvider>
            </Locales>
          </RTLLayout>
        </ThemeCustomization>
      </CompanySettingsProvider>
    </SystemErrorBoundary>
  );
};
