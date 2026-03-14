import { useState } from 'react';
import PropTypes from 'prop-types';

// material-ui
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { alpha, useTheme } from '@mui/material/styles';

// icons
import Brightness4Icon from '@mui/icons-material/Brightness4'; // Moon
import Brightness7Icon from '@mui/icons-material/Brightness7'; // Sun

// project imports
import AuthFooter from 'components/cards/AuthFooter';
import Logo from 'components/logo';
import AuthCard from './AuthCard';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// assets
import AuthBackground from './AuthBackground';

// ==============================|| AUTHENTICATION - WRAPPER ||============================== //

export default function AuthWrapper({ children }) {
  const theme = useTheme();
  // FORCE LIGHT MODE (Business Requirement)
  const isDarkMode = false;
  const { companyName, getLogoSrc, hasLogo, settings } = useCompanySettings();

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%)', // Modern light medical background
        color: 'inherit',
        position: 'relative'
      }}
    >
      {/* Theme Toggle Removed */}

      <AuthBackground />

      <Stack sx={{ minHeight: '100vh', justifyContent: 'center' }}>
        {/* Login Card */}
        <Box>
          <Grid
            container
            sx={{
              justifyContent: 'center',
              alignItems: 'center'
            }}
          >
            <Grid size={{ xs: 12, sm: 8, md: 6, lg: 5, xl: 4 }} sx={{ mx: 'auto' }}>
              <AuthCard>{children}</AuthCard>
            </Grid>
          </Grid>
        </Box>

        {/* Footer */}
        <Box sx={{ p: '1.5rem' }}>
          <AuthFooter />
        </Box>
      </Stack>
    </Box>
  );
}

AuthWrapper.propTypes = { children: PropTypes.node };

