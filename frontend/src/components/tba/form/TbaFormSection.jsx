/**
 * TbaFormSection - Unified Form Section Component
 * Phase D3 - TbaForm System (Mantis-Native)
 *
 * ⚠️ CONTRACT:
 * - Presentation only - no business logic
 * - RTL compatible
 * - Mantis theme compliant
 *
 * Usage:
 * <TbaFormSection title="المعلومات الأساسية" icon={InfoOutlinedIcon}>
 *   <Grid container spacing={2}>
 *     {children}
 *   </Grid>
 * </TbaFormSection>
 */

import PropTypes from 'prop-types';

// MUI Components
import { Box, Paper, Typography, Stack, Divider } from '@mui/material';

// ============================================================================
// COMPONENT
// ============================================================================

const TbaFormSection = ({ title, subtitle, icon: Icon, children, noPaper = false, sx = {} }) => {
  const content = (
    <Box sx={{ p: noPaper ? 0 : 2.5, ...sx }}>
      {/* Section Header */}
      {title && (
        <Stack direction="row" alignItems="flex-start" spacing={1.5} sx={{ mb: '0.75rem' }}>
          {Icon && (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '2.0rem',
                height: '2.0rem',
                borderRadius: '0.375rem',
                bgcolor: 'primary.lighter',
                color: 'primary.main',
                flexShrink: 0,
                mt: 0.25
              }}
            >
              <Icon sx={{ fontSize: '1.125rem' }} />
            </Box>
          )}
          <Box sx={{ flex: 1 }}>
            <Typography variant="subtitle1" fontWeight={600} color="text.primary" sx={{ mb: 0.25, lineHeight: 1.4 }}>
              {title}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.4 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Stack>
      )}

      {/* Divider after header */}
      {title && <Divider sx={{ mb: '1.0rem' }} />}

      {/* Section Content */}
      {children}
    </Box>
  );

  if (noPaper) {
    return content;
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        mb: '1.0rem',
        borderRadius: '0.25rem',
        overflow: 'hidden',
        borderColor: 'divider'
      }}
    >
      {content}
    </Paper>
  );
};

TbaFormSection.propTypes = {
  /** Section title */
  title: PropTypes.string,
  /** Section subtitle */
  subtitle: PropTypes.string,
  /** Icon component (not JSX) */
  icon: PropTypes.elementType,
  /** Section content */
  children: PropTypes.node,
  /** Disable Paper wrapper */
  noPaper: PropTypes.bool,
  /** Additional styles */
  sx: PropTypes.object
};

export default TbaFormSection;



