import { Box, Container, Typography, Paper, Stack, Chip } from '@mui/material';
import { Construction, Schedule, Info } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

/**
 * 🚧 Under Development Placeholder Page
 *
 * Purpose: Generic reusable page for features not yet implemented
 * Usage: Prevents route breaking while showing clear status to users
 * Design: Clean, professional, informative (no errors/warnings)
 */

const UnderDevelopment = () => {
  const theme = useTheme();

  return (
    <Container maxWidth="md" sx={{ mt: '4.0rem', mb: '2.0rem' }}>
      <Paper
        elevation={0}
        sx={{
          p: '3.0rem',
          textAlign: 'center',
          border: `2px dashed ${theme.palette.divider}`,
          borderRadius: '0.25rem',
          background: theme.palette.mode === 'dark' ? theme.palette.grey[900] : theme.palette.grey[50]
        }}
      >
        <Stack spacing={4} alignItems="center">
          {/* Icon */}
          <Box
            sx={{
              width: '7.5rem',
              height: '7.5rem',
              borderRadius: '50%',
              backgroundColor: theme.palette.warning.lighter || theme.palette.warning.light,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Construction
              sx={{
                fontSize: '4.0rem',
                color: theme.palette.warning.main
              }}
            />
          </Box>

          {/* Status Chip */}
          <Chip
            icon={<Schedule />}
            label="قيد التطوير"
            color="warning"
            variant="outlined"
            size="medium"
            sx={{ px: '1.0rem', py: 0.5, fontSize: '0.95rem' }}
          />

          {/* Main Message - Arabic */}
          <Box>
            <Typography variant="h3" fontWeight="bold" color="text.primary" gutterBottom>
              هذه الميزة قيد التطوير
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mt: '1.0rem', fontSize: '1.1rem', lineHeight: 1.8 }}>
              نعمل حالياً على تطوير هذه الصفحة.
              <br />
              سيتم إطلاقها في التحديثات القادمة.
            </Typography>
          </Box>

          {/* Main Message - English */}
          <Box sx={{ mt: '1.5rem' }}>
            <Typography variant="h4" fontWeight="600" color="text.primary" gutterBottom>
              This feature is under development
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ fontSize: '1rem', lineHeight: 1.8 }}>
              We are currently working on developing this page.
              <br />
              It will be available in upcoming releases.
            </Typography>
          </Box>

          {/* Info Section */}
          <Paper
            elevation={0}
            sx={{
              mt: '2.0rem',
              p: '1.5rem',
              backgroundColor: theme.palette.info.lighter || theme.palette.info.light,
              border: `1px solid ${theme.palette.info.main}`,
              borderRadius: 1,
              width: '100%'
            }}
          >
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <Info
                sx={{
                  color: theme.palette.info.main,
                  fontSize: '1.75rem',
                  mt: 0.5
                }}
              />
              <Box sx={{ textAlign: 'left' }}>
                <Typography variant="subtitle1" fontWeight="600" color="info.dark" gutterBottom>
                  للاستفسار عن موعد الإطلاق:
                </Typography>
                <Typography variant="body2" color="info.dark" sx={{ lineHeight: 1.8 }}>
                  يرجى التواصل مع فريق الدعم التقني أو إدارة النظام.
                </Typography>
                <Typography variant="body2" color="info.dark" sx={{ mt: 1, fontStyle: 'italic' }}>
                  For inquiries about release date, please contact technical support or system administration.
                </Typography>
              </Box>
            </Stack>
          </Paper>

          {/* Roadmap Hint */}
          <Typography variant="caption" color="text.disabled" sx={{ mt: '1.5rem', fontSize: '0.85rem' }}>
            معظم الميزات الأساسية متاحة حالياً. هذه الصفحة من الميزات الاختيارية المخطط لها مستقبلاً.
            <br />
            Most core features are currently available. This page is an optional feature planned for the future.
          </Typography>
        </Stack>
      </Paper>
    </Container>
  );
};

export default UnderDevelopment;
