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
  CircularProgress,
  ButtonGroup,
  Slider
} from '@mui/material';
import {
  Print as PrintIcon,
  Download as DownloadIcon,
  ArrowBack as ArrowBackIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import { ModernPageHeader } from 'components/tba';
import config from 'config'; // assuming config.api.baseUrl exists

const ClaimStatementPreview = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();
  
  const queryParams = new URLSearchParams(location.search);
  const claimIds = queryParams.get('ids');
  const iframeRef = useRef(null);
  
  const [loading, setLoading] = useState(true);
  const [zoom, setZoom] = useState(100);

  const previewUrl = `/api/reports/claims/html?claimIds=${claimIds}`;
  const pdfUrl = `/api/reports/claims/pdf?claimIds=${claimIds}`;

  useEffect(() => {
    if (!claimIds) {
      enqueueSnackbar('لا توجد مطالبات محددة للعرض', { variant: 'warning' });
      navigate(-1);
    }
  }, [claimIds, navigate, enqueueSnackbar]);

  const handlePrint = () => {
    if (iframeRef.current) {
      iframeRef.current.contentWindow.print();
    }
  };

  const handleDownload = () => {
    // Open PDF URL
    window.open(pdfUrl, '_blank');
  };

  const iframeStyle = {
    width: '100%',
    height: '1000px',
    border: 'none',
    transform: `scale(${zoom / 100})`,
    transformOrigin: 'top center',
    transition: 'transform 0.2s',
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 3, mb: 3 }}>
      <ModernPageHeader
        title="معاينة كشف المطالبات"
        subtitle="Claim Statement Preview"
        breadcrumb={[
          { label: 'الرئيسية', path: '/' },
          { label: 'التقارير', path: '/reports' },
          { label: 'معاينة الكشف' }
        ]}
        actions={
          <Stack direction="row" spacing={2}>
            <Button
              variant="outlined"
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate(-1)}
            >
              رجوع
            </Button>
            <Button
              variant="outlined"
              color="primary"
              startIcon={<PrintIcon />}
              onClick={handlePrint}
              disabled={loading}
            >
              طباعة (Print)
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<DownloadIcon />}
              onClick={handleDownload}
              disabled={loading}
            >
              تصدير PDF (Export PDF)
            </Button>
          </Stack>
        }
      />

      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary">
              تكبير / تصغير العرض:
            </Typography>
            <ButtonGroup size="small" variant="outlined">
              <Button onClick={() => setZoom((z) => Math.max(z - 10, 50))}><ZoomOutIcon /></Button>
              <Button disabled sx={{ color: 'text.primary', border: '1px solid rgba(0, 0, 0, 0.12) !important' }}>
                {zoom}%
              </Button>
              <Button onClick={() => setZoom((z) => Math.min(z + 10, 200))}><ZoomInIcon /></Button>
            </ButtonGroup>
            <Box sx={{ width: 150, ml: 2 }}>
              <Slider
                value={zoom}
                min={50}
                max={200}
                step={10}
                onChange={(e, v) => setZoom(v)}
                aria-label="Zoom"
                valueLabelDisplay="auto"
              />
            </Box>
          </Stack>
          
          <Divider sx={{ mb: 3 }} />

          <Box sx={{ 
            position: 'relative', 
            bgcolor: '#e0e0e0', 
            p: 2, 
            borderRadius: 1, 
            minHeight: '800px',
            overflow: 'hidden'
          }}>
            {loading && (
              <Box sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', zIndex: 10 }}>
                <Stack alignItems="center" spacing={2}>
                  <CircularProgress />
                  <Typography>جاري إعداد التقرير...</Typography>
                </Stack>
              </Box>
            )}
            
            {claimIds && (
              <Box sx={{ 
                height: `${(100 / (zoom / 100))}0px`, 
                width: `${100 / (zoom / 100)}%`,
                transform: `scale(${zoom / 100})`,
                transformOrigin: 'top center',
                transition: 'transform 0.2s',
              }}>
                <iframe
                  title="Claim Statement Preview"
                  ref={iframeRef}
                  src={previewUrl}
                  style={{ width: '100%', height: '100%', border: '1px solid #ccc', backgroundColor: 'white', borderRadius: '4px' }}
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
