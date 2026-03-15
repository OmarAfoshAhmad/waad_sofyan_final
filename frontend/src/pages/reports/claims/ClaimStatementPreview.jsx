import { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Box,
  Card,
  CardContent,
  Stack,
  Typography,
  Button,
  Container,
  Divider,
  CircularProgress
} from '@mui/material';
import {
  Print as PrintIcon,
  PictureAsPdf as PdfIcon,
  ArrowBack as ArrowBackIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';
import { ModernPageHeader } from 'components/tba';

const ClaimStatementPreview = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  const queryParams = new URLSearchParams(location.search);
  const claimIds = queryParams.get('ids');
  const iframeRef = useRef(null);
  const [loading, setLoading] = useState(true);

  const previewUrl = `/api/reports/claims/html?claimIds=${claimIds}`;
  const pdfUrl     = `/api/reports/claims/pdf?claimIds=${claimIds}`;

  useEffect(() => {
    if (!claimIds) {
      enqueueSnackbar('لا توجد مطالبات محددة للعرض', { variant: 'warning' });
      navigate(-1);
    }
  }, [claimIds, navigate, enqueueSnackbar]);

  const handlePrint = () => {
    if (iframeRef.current) iframeRef.current.contentWindow.print();
  };

  const handleDownloadPdf = () => {
    window.open(pdfUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <Container maxWidth="xl" sx={{ mt: '1.5rem', mb: '1.5rem' }}>
      <ModernPageHeader
        title="معاينة كشف المطالبات"
        subtitle="Claim Statement Preview"
        breadcrumb={[
          { label: 'الرئيسية', path: '/' },
          { label: 'التقارير', path: '/reports' },
          { label: 'معاينة الكشف' }
        ]}
        actions={
          <Stack direction="row" spacing={1.5}>
            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)}>
              رجوع
            </Button>
            <Button
              variant="outlined"
              color="primary"
              startIcon={<PrintIcon />}
              onClick={handlePrint}
              disabled={loading}
            >
              طباعة
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<PdfIcon />}
              onClick={handleDownloadPdf}
              disabled={loading}
            >
              تنزيل PDF
            </Button>
          </Stack>
        }
      />

      <Card sx={{ mt: '1.5rem' }}>
        <CardContent sx={{ p: { xs: 1, md: 3 } }}>
          <Box
            sx={{
              position: 'relative',
              bgcolor: '#e8ecf0',
              p: { xs: 1, md: 4 },
              borderRadius: 1,
              minHeight: '50rem',
              display: 'flex',
              justifyContent: 'center',
              overflow: 'auto',
              border: '1px solid #d0d7de'
            }}
          >
            {loading && (
              <Box
                sx={{
                  position: 'absolute',
                  top: '40%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  textAlign: 'center',
                  zIndex: 10
                }}
              >
                <CircularProgress size={48} thickness={4} />
                <Typography sx={{ mt: '1rem', fontWeight: 'bold' }}>
                  جارٍ تحضير التقرير...
                </Typography>
              </Box>
            )}

            {claimIds && (
              <Box
                sx={{
                  width: '210mm',
                  minHeight: '297mm',
                  bgcolor: 'white',
                  boxShadow: '0 6px 24px rgba(0,0,0,0.12)',
                  borderRadius: '0.25rem',
                  overflow: 'hidden'
                }}
              >
                <iframe
                  title="Claim Statement Preview"
                  ref={iframeRef}
                  src={previewUrl}
                  style={{ width: '100%', height: '100%', minHeight: '297mm', border: 'none' }}
                  onLoad={() => setLoading(false)}
                />
              </Box>
            )}
          </Box>
        </CardContent>
      </Card>
    </Container>
  );
};

export default ClaimStatementPreview;


