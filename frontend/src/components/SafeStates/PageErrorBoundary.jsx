/**
 * PageErrorBoundary - Page-Level Error Boundary
 * ==============================================
 *
 * PRODUCTION STABILIZATION (2026-02-03):
 * - Catches runtime errors within a page
 * - Shows user-friendly error message
 * - Provides recovery options
 * - Does NOT crash the entire application
 *
 * USAGE:
 * <PageErrorBoundary pageName="Claims">
 *   <ClaimsPage />
 * </PageErrorBoundary>
 */

import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { Box, Button, Card, CardContent, Typography, Stack, alpha, Divider } from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import HomeIcon from '@mui/icons-material/Home';
import BugReportIcon from '@mui/icons-material/BugReport';

// ==============================|| ERROR ID GENERATOR ||============================== //

const generateErrorId = () => {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 6);
  return `PG-${timestamp}-${random}`.toUpperCase();
};

// ==============================|| PAGE ERROR FALLBACK ||============================== //

const PageErrorFallback = ({ pageName, errorId, onRetry, onGoHome }) => (
  <Box
    sx={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '25.0rem',
      p: '1.5rem'
    }}
  >
    <Card
      sx={{
        maxWidth: '31.25rem',
        width: '100%',
        textAlign: 'center',
        boxShadow: (theme) => `0 4px 20px ${alpha(theme.palette.error.main, 0.1)}`
      }}
    >
      <CardContent sx={{ p: '2.0rem' }}>
        <ErrorOutlineIcon
          sx={{
            fontSize: '4.0rem',
            color: 'error.main',
            mb: '1.0rem'
          }}
        />

        <Typography variant="h5" color="error.main" gutterBottom fontWeight={600}>
          حدث خطأ في الصفحة
        </Typography>

        {pageName && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            صفحة: {pageName}
          </Typography>
        )}

        <Typography variant="body1" color="text.secondary" sx={{ mb: '1.5rem' }}>
          عذراً، واجه النظام مشكلة أثناء تحميل هذه الصفحة. يمكنك الإنتقال للصفحة الرئيسية أو إعادة المحاولة.
        </Typography>

        <Stack direction="row" spacing={2} justifyContent="center" sx={{ mb: '1.0rem' }}>
          <Button variant="contained" color="primary" startIcon={<RefreshIcon />} onClick={onRetry}>
            إعادة المحاولة
          </Button>

          <Button variant="outlined" color="primary" startIcon={<HomeIcon />} onClick={onGoHome}>
            الرئيسية
          </Button>
        </Stack>

        <Divider sx={{ my: '1.0rem' }} />

        <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
          <BugReportIcon sx={{ fontSize: '0.875rem', color: 'text.disabled' }} />
          <Typography variant="caption" color="text.disabled">
            معرف الخطأ: {errorId}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  </Box>
);

PageErrorFallback.propTypes = {
  pageName: PropTypes.string,
  errorId: PropTypes.string.isRequired,
  onRetry: PropTypes.func.isRequired,
  onGoHome: PropTypes.func.isRequired
};

// ==============================|| PAGE ERROR BOUNDARY ||============================== //

class PageErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      errorId: null
    };
  }

  static getDerivedStateFromError() {
    return {
      hasError: true,
      errorId: generateErrorId()
    };
  }

  componentDidCatch(error, errorInfo) {
    // Log error for debugging (only in development)
    if (import.meta.env.DEV) {
      console.error(`[PageErrorBoundary] ${this.props.pageName || 'Unknown'}:`, error, errorInfo);
    }

    // Store error for debugging
    try {
      const errorLog = {
        id: this.state.errorId,
        page: this.props.pageName,
        timestamp: new Date().toISOString(),
        message: error?.message,
        stack: error?.stack?.substring(0, 500)
      };
      sessionStorage.setItem(`pageError_${this.state.errorId}`, JSON.stringify(errorLog));
    } catch (e) {
      // Silent fail
    }
  }

  handleRetry = () => {
    this.setState({
      hasError: false,
      errorId: null
    });
  };

  handleGoHome = () => {
    window.location.href = '/dashboard';
  };

  render() {
    if (this.state.hasError) {
      // Custom fallback
      if (this.props.fallback) {
        return this.props.fallback({
          errorId: this.state.errorId,
          retry: this.handleRetry
        });
      }

      // Default fallback
      return (
        <PageErrorFallback
          pageName={this.props.pageName}
          errorId={this.state.errorId}
          onRetry={this.handleRetry}
          onGoHome={this.handleGoHome}
        />
      );
    }

    return this.props.children;
  }
}

PageErrorBoundary.propTypes = {
  children: PropTypes.node.isRequired,
  pageName: PropTypes.string,
  fallback: PropTypes.func
};

export default PageErrorBoundary;
