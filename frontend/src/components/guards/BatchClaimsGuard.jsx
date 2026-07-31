/**
 * BatchClaimsGuard
 *
 * Hides the legacy/internal monthly batch intake when BATCH_CLAIMS_ENABLED is
 * disabled. Provider-portal claims remain visible through their own guard.
 */

import { Box, Typography, Alert, Button } from '@mui/material';
import { FolderOff as FolderOffIcon } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import useSystemConfig from 'hooks/useSystemConfig';

const BatchClaimsGuard = ({ children }) => {
  const { flags, loading } = useSystemConfig();
  const navigate = useNavigate();

  if (loading) return null;

  if (!flags.BATCH_CLAIMS_ENABLED) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          gap: '1.5rem',
          p: '2rem'
        }}
      >
        <FolderOffIcon sx={{ fontSize: '4.5rem', color: 'text.disabled' }} />

        <Typography variant="h4" fontWeight={700} color="text.secondary" align="center">
          نظام دفعات المطالبات غير مفعل
        </Typography>

        <Alert severity="info" sx={{ maxWidth: '34rem' }}>
          تم إيقاف إدخال المطالبات عبر الدفعات. المسار التشغيلي الحالي هو استقبال المطالبات من بوابة مقدم الخدمة ثم
          مراجعتها واعتمادها من نفس محرك المطالبات.
        </Alert>

        <Button variant="contained" color="primary" onClick={() => navigate('/provider/visits')}>
          الانتقال إلى بوابة مقدم الخدمة
        </Button>
      </Box>
    );
  }

  return children;
};

export default BatchClaimsGuard;
