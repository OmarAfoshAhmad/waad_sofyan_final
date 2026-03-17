import { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Box,
  Stack,
  Typography,
  Button,
  Divider,
  CircularProgress,
  Chip
} from '@mui/material';
import {
  Print as PrintIcon,
  ArrowBack as ArrowBackIcon,
  ReceiptLong as ReceiptIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

const ClaimStatementPreview = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  const queryParams = new URLSearchParams(location.search);
  const claimIds = queryParams.get('ids');
  const iframeRef = useRef(null);
  const [loading, setLoading] = useState(true);

  const previewUrl = `/api/reports/claims/html?claimIds=${claimIds}`;
  const claimCount = claimIds ? claimIds.split(',').length : 0;

  useEffect(() => {
    if (!claimIds) {
      enqueueSnackbar('لا توجد مطالبات محددة للعرض', { variant: 'warning' });
      navigate(-1);
    }
  }, [claimIds, navigate, enqueueSnackbar]);

  const handlePrint = () => {
    if (iframeRef.current) iframeRef.current.contentWindow.print();
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        bgcolor: '#1a2332',
        overflow: 'hidden'
      }}
    >
      {/* Toolbar */}
      <Box
        sx={{
          flexShrink: 0,
          bgcolor: '#0f1923',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          px: 3,
          py: 1.25,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          boxShadow: '0 2px 12px rgba(0,0,0,0.4)'
        }}
      >
        <Button
          variant="text"
          size="small"
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate(-1)}
          sx={{
            color: 'rgba(255,255,255,0.65)',
            minWidth: 0,
            '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' }
          }}
        >
          رجوع
        </Button>

        <Divider orientation="vertical" flexItem sx={{ borderColor: 'rgba(255,255,255,0.12)' }} />

        <Stack direction="row" alignItems="center" spacing={1}>
          <ReceiptIcon sx={{ color: 'rgba(255,255,255,0.45)', fontSize: '1.1rem' }} />
          <Typography variant="subtitle2" sx={{ color: '#e8edf2', fontWeight: 600 }}>
            معاينة كشف المطالبات
          </Typography>
          <Chip
            label={`${claimCount} مطالبة`}
            size="small"
            sx={{
              bgcolor: 'rgba(25,118,210,0.25)',
              color: '#90caf9',
              border: '1px solid rgba(25,118,210,0.45)',
              height: '1.4rem',
              fontSize: '0.68rem',
              fontWeight: 700
            }}
          />
        </Stack>

        <Box sx={{ flexGrow: 1 }} />

        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<PrintIcon />}
            onClick={handlePrint}
            disabled={loading}
            sx={{
              color: 'rgba(255,255,255,0.8)',
              borderColor: 'rgba(255,255,255,0.22)',
              '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.07)', color: '#fff' },
              '&.Mui-disabled': { color: 'rgba(255,255,255,0.25)', borderColor: 'rgba(255,255,255,0.1)' }
            }}
          >
            طباعة
          </Button>
        </Stack>
      </Box>

      {/* Document area */}
      <Box
        sx={{
          flex: 1,
          overflow: 'auto',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'flex-start',
          py: 5,
          px: 2,
          background: 'radial-gradient(ellipse at 50% 0%, #243447 0%, #1a2332 60%)'
        }}
      >
        {/* A4 Page */}
        <Box
          sx={{
            position: 'relative',
            width: '210mm',
            minHeight: '297mm',
            bgcolor: '#fff',
            boxShadow: '0 4px 6px rgba(0,0,0,0.3), 0 12px 40px rgba(0,0,0,0.5)',
            borderRadius: '1px',
            overflow: 'hidden',
            flexShrink: 0
          }}
        >
          {loading && (
            <Box
              sx={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: '#fff',
                zIndex: 10,
                gap: 1.5
              }}
            >
              <CircularProgress size={40} thickness={3} sx={{ color: '#1976d2' }} />
              <Typography variant="body2" sx={{ color: '#555', fontWeight: 500, letterSpacing: 0.3 }}>
                جارٍ تحضير التقرير...
              </Typography>
            </Box>
          )}
          {claimIds && (
            <iframe
              title="Claim Statement Preview"
              ref={iframeRef}
              src={previewUrl}
              style={{ width: '100%', height: '100%', minHeight: '297mm', border: 'none', display: 'block' }}
              onLoad={() => setLoading(false)}
            />
          )}
        </Box>
      </Box>
    </Box>
  );
};

export default ClaimStatementPreview;


