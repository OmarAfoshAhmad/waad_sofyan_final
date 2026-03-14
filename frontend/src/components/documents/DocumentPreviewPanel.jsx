import PropTypes from 'prop-types';
import { useState, useEffect } from 'react';
import { Box, IconButton, Typography, Stack, Paper, CircularProgress, Tooltip, Button } from '@mui/material';
import { ZoomIn, ZoomOut, NavigateBefore, NavigateNext, Download as DownloadIcon, RotateRight, CloseFullscreen } from '@mui/icons-material';
import { Document, Page } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

export default function DocumentPreviewPanel({ fileUrl, fileType, fileName, onClose }) {
  const [numPages, setNumPages] = useState(null);
  const [page, setPage] = useState(1);
  const [scale, setScale] = useState(1.0);
  const [rotation, setRotation] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Reset state when file changes
    setPage(1);
    setRotation(0);
    setScale(1.0);
    setLoading(true);
  }, [fileUrl]);

  if (!fileUrl) {
    return (
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          bgcolor: 'background.paper',
          borderRadius: '0.125rem',
          border: '1px dashed'
        }}
      >
        <Typography color="text.secondary">اختر مستنداً للمعاينة</Typography>
      </Box>
    );
  }

  const isImage = fileType?.toLowerCase().includes('image') || fileUrl.toLowerCase().match(/\.(jpg|jpeg|png|gif|webp)$/);

  const handleDownload = () => {
    window.open(fileUrl, '_blank');
  };

  return (
    <Paper sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      {/* Toolbar */}
      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        justifyContent="space-between"
        sx={{ p: 1, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.default' }}
      >
        <Typography variant="subtitle2" noWrap sx={{ maxWidth: '12.5rem' }}>
          {fileName || 'معاينة المستند'}
        </Typography>

        <Stack direction="row" spacing={0.5}>
          {!isImage && (
            <>
              <Tooltip title="تصغير">
                <IconButton size="small" onClick={() => setScale((s) => Math.max(0.5, s - 0.1))}>
                  <ZoomOut fontSize="small" />
                </IconButton>
              </Tooltip>
              <Typography variant="caption" sx={{ minWidth: '1.875rem', textAlign: 'center', pt: 0.8 }}>
                {Math.round(scale * 100)}%
              </Typography>
              <Tooltip title="تكبير">
                <IconButton size="small" onClick={() => setScale((s) => Math.min(3.0, s + 0.1))}>
                  <ZoomIn fontSize="small" />
                </IconButton>
              </Tooltip>

              <Box sx={{ borderRight: 1, borderColor: 'divider', height: '1.25rem', mx: 1 }} />

              <Tooltip title="السابق">
                <span>
                  <IconButton size="small" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}>
                    <NavigateBefore fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Typography variant="caption" sx={{ minWidth: '2.5rem', textAlign: 'center', pt: 0.8 }}>
                {page} / {numPages || '-'}
              </Typography>
              <Tooltip title="التالي">
                <span>
                  <IconButton size="small" onClick={() => setPage((p) => Math.min(numPages, p + 1))} disabled={page >= numPages}>
                    <NavigateNext fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>

              <Box sx={{ borderRight: 1, borderColor: 'divider', height: '1.25rem', mx: 1 }} />

              <Tooltip title="تدوير">
                <IconButton size="small" onClick={() => setRotation((r) => (r + 90) % 360)}>
                  <RotateRight fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}

          <Tooltip title="تحميل الملف">
            <IconButton size="small" onClick={handleDownload} color="primary">
              <DownloadIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          {onClose && (
            <Tooltip title="إغلاق المعاينة">
              <IconButton size="small" onClick={onClose}>
                <CloseFullscreen fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      </Stack>

      {/* Content Area */}
      <Box
        sx={{
          flex: 1,
          overflow: 'auto',
          bgcolor: 'grey.100',
          display: 'flex',
          justifyContent: 'center',
          p: '1.0rem',
          position: 'relative'
        }}
      >
        {isImage ? (
          <img
            src={fileUrl}
            alt="document"
            style={{
              maxWidth: '100%',
              height: 'auto',
              boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'
            }}
          />
        ) : (
          <Document
            file={fileUrl}
            onLoadSuccess={({ numPages }) => {
              setNumPages(numPages);
              setLoading(false);
            }}
            onLoadError={(error) => {
              console.error('Error loading PDF:', error);
              setLoading(false);
            }}
            loading={
              <Stack alignItems="center" spacing={1} mt={4}>
                <CircularProgress size={30} />
                <Typography variant="caption">جاري تحميل المستند...</Typography>
              </Stack>
            }
            error={
              <Stack alignItems="center" spacing={1} mt={4} color="error.main">
                <Typography>تعذر تحميل ملف PDF</Typography>
                <Button size="small" onClick={handleDownload}>
                  تحميل الملف
                </Button>
              </Stack>
            }
          >
            {numPages && (
              <Paper elevation={3}>
                <Page pageNumber={page} scale={scale} rotate={rotation} renderTextLayer={false} renderAnnotationLayer={false} />
              </Paper>
            )}
          </Document>
        )}
      </Box>
    </Paper>
  );
}

DocumentPreviewPanel.propTypes = {
  fileUrl: PropTypes.string,
  fileType: PropTypes.string,
  fileName: PropTypes.string,
  onClose: PropTypes.func
};
