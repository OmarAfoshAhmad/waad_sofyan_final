/**
 * DocumentPreviewDrawer.jsx - Side Panel Document Preview
 *
 * Reusable drawer component for previewing documents (PDF/Images)
 * Used in:
 * - Provider Documents
 * - Claims Inbox
 * - Pre-Approvals Inbox
 *
 * Features:
 * - Side drawer (right-to-left for Arabic)
 * - Supports PDF (iframe) and Images (img)
 * - Download button (optional)
 * - Close button
 * - No forced download
 * - No new tab opening
 *
 * @version 1.0 - 2026-01-30
 */

import { useState, useEffect, useCallback, memo } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Box, Typography, IconButton, Stack, CircularProgress, Paper, Divider, Button, Tooltip, alpha } from '@mui/material';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  Refresh as RefreshIcon,
  BrokenImage as BrokenImageIcon,
  PictureAsPdf as PdfIcon,
  Image as ImageIcon,
  InsertDriveFile as FileIcon
} from '@mui/icons-material';

// ============================================================================
// CONSTANTS
// ============================================================================

const DRAWER_WIDTH = 500;
const ZOOM_LEVELS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const DEFAULT_ZOOM_INDEX = 2; // 100%

const SUPPORTED_IMAGE_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'];

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

const getPreviewType = (mimeType) => {
  if (!mimeType) return 'unsupported';
  const type = mimeType.toLowerCase();

  if (SUPPORTED_IMAGE_TYPES.includes(type)) return 'image';
  if (type === 'application/pdf') return 'pdf';

  return 'unsupported';
};

const getFileIcon = (mimeType) => {
  const type = getPreviewType(mimeType);
  switch (type) {
    case 'image':
      return <ImageIcon sx={{ fontSize: '3.0rem', color: 'info.main' }} />;
    case 'pdf':
      return <PdfIcon sx={{ fontSize: '3.0rem', color: 'error.main' }} />;
    default:
      return <FileIcon sx={{ fontSize: '3.0rem', color: 'text.secondary' }} />;
  }
};

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '';
  const units = ['بايت', 'ك.ب', 'م.ب', 'ج.ب'];
  let index = 0;
  let size = bytes;

  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index++;
  }

  return `${size.toFixed(index > 0 ? 1 : 0)} ${units[index]}`;
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const DocumentPreviewDrawer = memo(
  ({ open, onClose, documentUrl, fileName, mimeType, fileSize, documentTitle, onDownload, showDownload = true }) => {
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [zoomIndex, setZoomIndex] = useState(DEFAULT_ZOOM_INDEX);

    const previewType = getPreviewType(mimeType);
    const currentZoom = ZOOM_LEVELS[zoomIndex];

    // Reset state when document changes
    useEffect(() => {
      if (open && documentUrl) {
        setLoading(true);
        setError(false);
        setZoomIndex(DEFAULT_ZOOM_INDEX);
      }
    }, [open, documentUrl]);

    // Zoom handlers
    const handleZoomIn = useCallback(() => {
      setZoomIndex((prev) => Math.min(prev + 1, ZOOM_LEVELS.length - 1));
    }, []);

    const handleZoomOut = useCallback(() => {
      setZoomIndex((prev) => Math.max(prev - 1, 0));
    }, []);

    // Image handlers
    const handleImageLoad = useCallback(() => {
      setLoading(false);
      setError(false);
    }, []);

    const handleImageError = useCallback(() => {
      setLoading(false);
      setError(true);
    }, []);

    // PDF load handler
    const handlePdfLoad = useCallback(() => {
      setLoading(false);
      setError(false);
    }, []);

    // Download handler
    const handleDownload = useCallback(() => {
      if (onDownload) {
        onDownload();
      } else if (documentUrl) {
        // Create a download link
        const link = document.createElement('a');
        link.href = documentUrl;
        link.download = fileName || 'document';
        link.target = '_blank';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    }, [documentUrl, fileName, onDownload]);

    // Retry handler
    const handleRetry = useCallback(() => {
      setLoading(true);
      setError(false);
    }, []);

    // ========================================
    // RENDER PREVIEW CONTENT
    // ========================================
    const renderPreviewContent = () => {
      // Loading state
      if (loading && previewType !== 'unsupported') {
        return (
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              minHeight: '25.0rem'
            }}
          >
            <CircularProgress size={48} />
            <Typography variant="body2" color="text.secondary" sx={{ mt: '1.0rem' }}>
              جارٍ تحميل المستند...
            </Typography>
          </Box>
        );
      }

      // Error state
      if (error) {
        return (
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              minHeight: '25.0rem',
              bgcolor: (theme) => alpha(theme.palette.error.main, 0.04)
            }}
          >
            <BrokenImageIcon sx={{ fontSize: '4.0rem', color: 'error.main', mb: '1.0rem' }} />
            <Typography variant="body1" color="error.main" fontWeight={600}>
              فشل تحميل المستند
            </Typography>
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={handleRetry} sx={{ mt: '1.0rem' }}>
              إعادة المحاولة
            </Button>
          </Box>
        );
      }

      // Image preview
      if (previewType === 'image') {
        return (
          <Box
            sx={{
              overflow: 'auto',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'grey.100',
              p: '1.0rem'
            }}
          >
            <img
              src={documentUrl}
              alt={fileName || 'معاينة المستند'}
              onLoad={handleImageLoad}
              onError={handleImageError}
              style={{
                maxWidth: '100%',
                maxHeight: '100%',
                objectFit: 'contain',
                transform: `scale(${currentZoom})`,
                transformOrigin: 'center center',
                transition: 'transform 0.2s ease',
                display: loading ? 'none' : 'block'
              }}
            />
          </Box>
        );
      }

      // PDF preview
      if (previewType === 'pdf') {
        return (
          <Box sx={{ height: '100%', position: 'relative' }}>
            <iframe
              src={`${documentUrl}#toolbar=1&navpanes=0&scrollbar=1`}
              title={fileName || 'معاينة PDF'}
              width="100%"
              height="100%"
              style={{
                border: 'none',
                transform: `scale(${currentZoom})`,
                transformOrigin: 'top right',
                width: `${100 / currentZoom}%`,
                height: `${100 / currentZoom}%`
              }}
              onLoad={handlePdfLoad}
              onError={handleImageError}
            />
          </Box>
        );
      }

      // Unsupported type
      return (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
            minHeight: '25.0rem',
            bgcolor: 'grey.50'
          }}
        >
          {getFileIcon(mimeType)}
          <Typography variant="body1" sx={{ mt: '1.0rem', fontWeight: 600 }}>
            {fileName || 'مستند'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            لا يمكن معاينة هذا النوع من الملفات
          </Typography>
          {fileSize && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
              {formatFileSize(fileSize)}
            </Typography>
          )}
          {showDownload && (
            <Button variant="contained" startIcon={<DownloadIcon />} onClick={handleDownload} sx={{ mt: '1.5rem' }}>
              تحميل الملف
            </Button>
          )}
        </Box>
      );
    };

    return (
      <Drawer
        anchor="left"
        open={open}
        onClose={onClose}
        PaperProps={{
          sx: {
            width: DRAWER_WIDTH,
            maxWidth: '90vw'
          }
        }}
      >
        {/* Header */}
        <Paper
          elevation={1}
          sx={{
            p: '1.0rem',
            borderRadius: 0,
            bgcolor: 'primary.main',
            color: 'primary.contrastText'
          }}
        >
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Stack direction="row" alignItems="center" spacing={1}>
              {getFileIcon(mimeType)}
              <Box>
                <Typography variant="subtitle1" fontWeight={600} noWrap sx={{ maxWidth: '18.75rem' }}>
                  {documentTitle || fileName || 'معاينة المستند'}
                </Typography>
                {fileSize && (
                  <Typography variant="caption" sx={{ opacity: 0.8 }}>
                    {formatFileSize(fileSize)}
                  </Typography>
                )}
              </Box>
            </Stack>
            <IconButton onClick={onClose} sx={{ color: 'inherit' }}>
              <CloseIcon />
            </IconButton>
          </Stack>
        </Paper>

        {/* Toolbar */}
        <Paper elevation={0} sx={{ p: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            {/* Zoom controls */}
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Tooltip title="تصغير">
                <span>
                  <IconButton size="small" onClick={handleZoomOut} disabled={zoomIndex === 0 || previewType === 'unsupported'}>
                    <ZoomOutIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Typography variant="caption" sx={{ minWidth: '2.8125rem', textAlign: 'center' }}>
                {Math.round(currentZoom * 100)}%
              </Typography>
              <Tooltip title="تكبير">
                <span>
                  <IconButton
                    size="small"
                    onClick={handleZoomIn}
                    disabled={zoomIndex === ZOOM_LEVELS.length - 1 || previewType === 'unsupported'}
                  >
                    <ZoomInIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </Stack>

            {/* Download button */}
            {showDownload && documentUrl && (
              <Tooltip title="تحميل الملف">
                <IconButton size="small" onClick={handleDownload} color="primary">
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        </Paper>

        {/* Preview Content */}
        <Box sx={{ flex: 1, overflow: 'hidden', height: 'calc(100vh - 140px)' }}>
          {documentUrl ? (
            renderPreviewContent()
          ) : (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: 'text.secondary'
              }}
            >
              <Typography variant="body2">لم يتم تحديد مستند للمعاينة</Typography>
            </Box>
          )}
        </Box>
      </Drawer>
    );
  }
);

DocumentPreviewDrawer.propTypes = {
  /** Whether the drawer is open */
  open: PropTypes.bool.isRequired,
  /** Close handler */
  onClose: PropTypes.func.isRequired,
  /** URL of the document to preview */
  documentUrl: PropTypes.string,
  /** File name */
  fileName: PropTypes.string,
  /** MIME type of the document */
  mimeType: PropTypes.string,
  /** File size in bytes */
  fileSize: PropTypes.number,
  /** Title to display in header (overrides fileName) */
  documentTitle: PropTypes.string,
  /** Custom download handler */
  onDownload: PropTypes.func,
  /** Whether to show download button */
  showDownload: PropTypes.bool
};

DocumentPreviewDrawer.displayName = 'DocumentPreviewDrawer';

export default DocumentPreviewDrawer;
