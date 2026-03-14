/**
 * DocumentPreview.jsx - Document Preview Component
 *
 * Renders inline preview of documents based on MIME type:
 * - Images (JPEG, PNG, GIF, WebP): <img> preview
 * - PDFs: <iframe> preview
 * - Others: Download button only
 *
 * @version 1.0 - 2026-01-29
 * Phase 1: Internal Review (Claims & PreAuthorizations)
 */

import { useState, useEffect, useCallback, memo } from 'react';
import PropTypes from 'prop-types';
import { Box, Paper, Typography, CircularProgress, IconButton, Stack, Tooltip, Button, Skeleton, alpha } from '@mui/material';
import {
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  Fullscreen as FullscreenIcon,
  FullscreenExit as FullscreenExitIcon,
  Download as DownloadIcon,
  BrokenImage as BrokenImageIcon,
  InsertDriveFile as FileIcon,
  Image as ImageIcon,
  PictureAsPdf as PdfIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';

// ============================================================================
// CONSTANTS
// ============================================================================

const SUPPORTED_IMAGE_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'];

const SUPPORTED_PDF_TYPE = 'application/pdf';

const ZOOM_LEVELS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const DEFAULT_ZOOM_INDEX = 2; // 100%

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Determine preview type from MIME type
 */
const getPreviewType = (mimeType) => {
  if (!mimeType) return 'unsupported';

  const normalizedType = mimeType.toLowerCase();

  if (SUPPORTED_IMAGE_TYPES.includes(normalizedType)) {
    return 'image';
  }

  if (normalizedType === SUPPORTED_PDF_TYPE) {
    return 'pdf';
  }

  return 'unsupported';
};

/**
 * Get file icon based on type
 */
const getFileIcon = (mimeType) => {
  const type = getPreviewType(mimeType);

  switch (type) {
    case 'image':
      return <ImageIcon sx={{ fontSize: '4.0rem', color: 'info.main' }} />;
    case 'pdf':
      return <PdfIcon sx={{ fontSize: '4.0rem', color: 'error.main' }} />;
    default:
      return <FileIcon sx={{ fontSize: '4.0rem', color: 'text.secondary' }} />;
  }
};

/**
 * Format file size for display
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '—';
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

const DocumentPreview = memo(
  ({ documentUrl, mimeType, fileName, fileSize, onDownload, loading = false, error = null, height = 400, showToolbar = true, onRetry }) => {
    const [zoomIndex, setZoomIndex] = useState(DEFAULT_ZOOM_INDEX);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [imageError, setImageError] = useState(false);
    const [imageLoading, setImageLoading] = useState(true);

    const previewType = getPreviewType(mimeType);
    const currentZoom = ZOOM_LEVELS[zoomIndex];

    // Reset state when document changes
    useEffect(() => {
      setImageError(false);
      setImageLoading(true);
      setZoomIndex(DEFAULT_ZOOM_INDEX);
    }, [documentUrl]);

    // Zoom handlers
    const handleZoomIn = useCallback(() => {
      setZoomIndex((prev) => Math.min(prev + 1, ZOOM_LEVELS.length - 1));
    }, []);

    const handleZoomOut = useCallback(() => {
      setZoomIndex((prev) => Math.max(prev - 1, 0));
    }, []);

    // Fullscreen toggle
    const handleToggleFullscreen = useCallback(() => {
      setIsFullscreen((prev) => !prev);
    }, []);

    // Image load handlers
    const handleImageLoad = useCallback(() => {
      setImageLoading(false);
      setImageError(false);
    }, []);

    const handleImageError = useCallback(() => {
      setImageLoading(false);
      setImageError(true);
    }, []);

    // Download handler
    const handleDownload = useCallback(() => {
      if (onDownload) {
        onDownload();
      } else if (documentUrl) {
        // Fallback: open in new tab
        window.open(documentUrl, '_blank');
      }
    }, [documentUrl, onDownload]);

    // ========================================
    // LOADING STATE
    // ========================================
    if (loading) {
      return (
        <Paper
          variant="outlined"
          sx={{
            height,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'grey.50',
            borderRadius: '0.125rem'
          }}
        >
          <CircularProgress size={40} />
          <Typography variant="body2" color="text.secondary" sx={{ mt: '1.0rem' }}>
            جارٍ تحميل المستند...
          </Typography>
        </Paper>
      );
    }

    // ========================================
    // ERROR STATE
    // ========================================
    if (error) {
      return (
        <Paper
          variant="outlined"
          sx={{
            height,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: (theme) => alpha(theme.palette.error.main, 0.04),
            borderColor: 'error.light',
            borderRadius: '0.125rem'
          }}
        >
          <BrokenImageIcon sx={{ fontSize: '3.0rem', color: 'error.main', mb: '1.0rem' }} />
          <Typography variant="body1" color="error.main" fontWeight={600}>
            فشل تحميل المستند
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {error}
          </Typography>
          {onRetry && (
            <Button variant="outlined" color="error" size="small" startIcon={<RefreshIcon />} onClick={onRetry} sx={{ mt: '1.0rem' }}>
              إعادة المحاولة
            </Button>
          )}
        </Paper>
      );
    }

    // ========================================
    // EMPTY STATE
    // ========================================
    if (!documentUrl) {
      return (
        <Paper
          variant="outlined"
          sx={{
            height,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'grey.50',
            borderRadius: '0.125rem'
          }}
        >
          <FileIcon sx={{ fontSize: '3.0rem', color: 'text.disabled', mb: '1.0rem' }} />
          <Typography variant="body1" color="text.secondary">
            اختر مستندًا للمعاينة
          </Typography>
        </Paper>
      );
    }

    // ========================================
    // UNSUPPORTED TYPE - DOWNLOAD ONLY
    // ========================================
    if (previewType === 'unsupported') {
      return (
        <Paper
          variant="outlined"
          sx={{
            height,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'grey.50',
            borderRadius: '0.125rem',
            p: '1.5rem'
          }}
        >
          {getFileIcon(mimeType)}
          <Typography variant="subtitle1" fontWeight={600} sx={{ mt: '1.0rem' }}>
            {fileName || 'ملف غير معروف'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
            {mimeType || 'نوع غير محدد'} • {formatFileSize(fileSize)}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem', textAlign: 'center' }}>
            لا يمكن معاينة هذا النوع من الملفات مباشرة
          </Typography>
          <Button variant="contained" startIcon={<DownloadIcon />} onClick={handleDownload}>
            تحميل الملف
          </Button>
        </Paper>
      );
    }

    // ========================================
    // PREVIEW CONTENT
    // ========================================
    const containerHeight = isFullscreen ? '100vh' : height;

    return (
      <Paper
        variant="outlined"
        sx={{
          height: containerHeight,
          display: 'flex',
          flexDirection: 'column',
          borderRadius: '0.125rem',
          overflow: 'hidden',
          position: isFullscreen ? 'fixed' : 'relative',
          top: isFullscreen ? 0 : 'auto',
          left: isFullscreen ? 0 : 'auto',
          right: isFullscreen ? 0 : 'auto',
          bottom: isFullscreen ? 0 : 'auto',
          zIndex: isFullscreen ? 1300 : 'auto',
          bgcolor: 'background.paper'
        }}
      >
        {/* Toolbar */}
        {showToolbar && (
          <Box
            sx={{
              px: '1.0rem',
              py: 1,
              borderBottom: 1,
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              bgcolor: 'grey.50'
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center">
              {getFileIcon(mimeType)}
              <Box>
                <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ maxWidth: '12.5rem' }}>
                  {fileName || 'مستند'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatFileSize(fileSize)}
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" spacing={0.5}>
              {previewType === 'image' && (
                <>
                  <Tooltip title="تصغير">
                    <span>
                      <IconButton size="small" onClick={handleZoomOut} disabled={zoomIndex === 0}>
                        <ZoomOutIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>

                  <Typography
                    variant="body2"
                    sx={{
                      px: 1,
                      display: 'flex',
                      alignItems: 'center',
                      minWidth: '3.125rem',
                      justifyContent: 'center'
                    }}
                  >
                    {Math.round(currentZoom * 100)}%
                  </Typography>

                  <Tooltip title="تكبير">
                    <span>
                      <IconButton size="small" onClick={handleZoomIn} disabled={zoomIndex === ZOOM_LEVELS.length - 1}>
                        <ZoomInIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </>
              )}

              <Tooltip title={isFullscreen ? 'إنهاء ملء الشاشة' : 'ملء الشاشة'}>
                <IconButton size="small" onClick={handleToggleFullscreen}>
                  {isFullscreen ? <FullscreenExitIcon fontSize="small" /> : <FullscreenIcon fontSize="small" />}
                </IconButton>
              </Tooltip>

              <Tooltip title="تحميل">
                <IconButton size="small" onClick={handleDownload}>
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          </Box>
        )}

        {/* Preview Area */}
        <Box
          sx={{
            flex: 1,
            overflow: 'auto',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: previewType === 'pdf' ? 'white' : 'grey.100',
            p: previewType === 'image' ? 2 : 0
          }}
        >
          {/* Image Preview */}
          {previewType === 'image' && (
            <>
              {imageLoading && <Skeleton variant="rectangular" width="100%" height="100%" sx={{ position: 'absolute' }} />}
              {imageError ? (
                <Box
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center'
                  }}
                >
                  <BrokenImageIcon sx={{ fontSize: '3.0rem', color: 'text.disabled', mb: 1 }} />
                  <Typography variant="body2" color="text.secondary">
                    فشل تحميل الصورة
                  </Typography>
                </Box>
              ) : (
                <Box
                  component="img"
                  src={documentUrl}
                  alt={fileName || 'معاينة المستند'}
                  onLoad={handleImageLoad}
                  onError={handleImageError}
                  sx={{
                    maxWidth: '100%',
                    maxHeight: '100%',
                    objectFit: 'contain',
                    transform: `scale(${currentZoom})`,
                    transition: 'transform 0.2s ease',
                    display: imageLoading ? 'none' : 'block'
                  }}
                />
              )}
            </>
          )}

          {/* PDF Preview */}
          {previewType === 'pdf' && (
            <Box
              component="iframe"
              src={`${documentUrl}#toolbar=0&navpanes=0`}
              title={fileName || 'معاينة PDF'}
              sx={{
                width: '100%',
                height: '100%',
                border: 'none'
              }}
            />
          )}
        </Box>
      </Paper>
    );
  }
);

DocumentPreview.propTypes = {
  /** URL of the document to preview */
  documentUrl: PropTypes.string,
  /** MIME type of the document */
  mimeType: PropTypes.string,
  /** File name for display */
  fileName: PropTypes.string,
  /** File size in bytes */
  fileSize: PropTypes.number,
  /** Callback for download action */
  onDownload: PropTypes.func,
  /** Show loading state */
  loading: PropTypes.bool,
  /** Error message to display */
  error: PropTypes.string,
  /** Height of the preview container */
  height: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  /** Show toolbar with zoom/fullscreen controls */
  showToolbar: PropTypes.bool,
  /** Callback for retry action */
  onRetry: PropTypes.func
};

DocumentPreview.displayName = 'DocumentPreview';

export default DocumentPreview;
