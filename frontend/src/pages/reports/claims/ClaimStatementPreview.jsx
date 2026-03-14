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
  Slider,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText
} from '@mui/material';
import {
  Print as PrintIcon,
  Download as DownloadIcon,
  ArrowBack as ArrowBackIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  PictureAsPdf as PictureAsPdfIcon,
  Layers as LayersIcon
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
  const [exportAnchor, setExportAnchor] = useState(null);
  const [exporting, setExporting] = useState(false);

  const previewUrl = `/api/reports/claims/html?claimIds=${claimIds}`;
  const pdfUrl = `/api/reports/claims/pdf?claimIds=${claimIds}`;
  const jasperPdfUrl = `/api/reports/claims/jasper?claimIds=${claimIds}`;

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

  const handleDownloadJasper = () => {
    try {
      setExporting(true);
      // استخدم فتح في تبويب جديد لتجنب مشاكل الكاش/الهيدر
      const url = jasperPdfUrl.startsWith('http')
        ? jasperPdfUrl
        : `${window.location.origin}${jasperPdfUrl}`;
      window.open(url, '_blank', 'noopener,noreferrer');
      enqueueSnackbar('جارٍ توليد تقرير Jasper في تبويب جديد', { variant: 'info' });
    } catch (err) {
      console.error(err);
      enqueueSnackbar('تعذر فتح تقرير Jasper', { variant: 'error' });
    } finally {
      setExporting(false);
      setExportAnchor(null);
    }
  };

  const handleOpenExportMenu = (e) => setExportAnchor(e.currentTarget);
  const handleCloseExportMenu = () => setExportAnchor(null);

  const iframeStyle = {
    width: '100%',
    height: '62.5rem',
    border: 'none',
    transform: `scale(${zoom / 100})`,
    transformOrigin: 'top center',
    transition: 'transform 0.2s',
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
              onClick={handleOpenExportMenu}
              disabled={loading || exporting}
            >
              تصدير التقرير
            </Button>
            <Menu
              anchorEl={exportAnchor}
              open={Boolean(exportAnchor)}
              onClose={handleCloseExportMenu}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            >
              <MenuItem onClick={() => { handleCloseExportMenu(); handleDownload(); }}>
                <ListItemIcon><PictureAsPdfIcon fontSize="small" /></ListItemIcon>
                <ListItemText primary="PDF (HTML) - النمط الحالي" secondary="مناسب للعرض السريع" />
              </MenuItem>
              <MenuItem onClick={handleDownloadJasper} disabled={exporting}>
                <ListItemIcon><LayersIcon fontSize="small" /></ListItemIcon>
                <ListItemText primary="PDF (Jasper) - مطابق الورقي" secondary="يشمل الخطاب وصفحة الجدول" />
              </MenuItem>
            </Menu>
          </Stack>
        }
      />

      <Card sx={{ mt: '1.5rem' }}>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: '1.0rem' }}>
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
            <Box sx={{ width: '9.375rem', ml: '1.0rem' }}>
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
          
          <Divider sx={{ mb: '1.5rem' }} />

          <Box sx={{ 
            position: 'relative', 
            bgcolor: '#ebeef2', 
            p: { xs: 1, md: 4 }, 
            borderRadius: 1, 
            minHeight: '50.0rem',
            display: 'flex',
            justifyContent: 'center',
            overflow: 'auto',
            border: '1px solid #d1d9e0'
          }}>
            {loading && (
              <Box sx={{ 
                position: 'absolute', 
                top: '100.0rem', 
                left: '50%', 
                transform: 'translateX(-50%)', 
                zIndex: 10,
                textAlign: 'center'
              }}>
                <CircularProgress size={50} thickness={4} />
                <Typography sx={{ mt: '1.0rem', fontWeight: 'bold' }}>جاري إعداد التقرير وتجهيز المعاينة...</Typography>
              </Box>
            )}
            
            {claimIds && (
              <Box sx={{ 
                width: '100%',
                display: 'flex',
                justifyContent: 'center',
                // This wrapper handles the internal scaling of the iframe
              }}>
                <Box sx={{
                  width: '210mm', // Fixed A4 width
                  height: '297mm', // Approximate A4 height for single page preview
                  minHeight: '75.0rem',
                  bgcolor: 'white',
                  boxShadow: '0 10px 30px rgba(0,0,0,0.15)',
                  transform: `scale(${zoom / 100})`,
                  transformOrigin: 'top center',
                  transition: 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                  overflow: 'hidden',
                  borderRadius: '0.375rem'
                }}>
                  <iframe
                    title="Claim Statement Preview"
                    ref={iframeRef}
                    src={previewUrl}
                    style={{ 
                      width: '100%', 
                      height: '100%', 
                      border: 'none',
                      backgroundColor: 'white' 
                    }}
                    onLoad={() => setLoading(false)}
                  />
                </Box>
              </Box>
            )}
          </Box>
        </CardContent>
      </Card>
    </Container>
  );
};

export default ClaimStatementPreview;

