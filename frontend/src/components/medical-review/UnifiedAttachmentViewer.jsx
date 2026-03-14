/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 📎 UNIFIED ATTACHMENT VIEWER - Medical Review System
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Centralized document preview component for:
 * - Claims
 * - Pre-Authorizations
 * - Medical Approvals
 *
 * Features:
 * ✓ PDF inline preview (iframe)
 * ✓ Image preview (JPG, PNG, GIF, WebP) with zoom
 * ✓ Other files (download fallback)
 * ✓ Thumbnail list navigation
 * ✓ Fixed width panel (320-380px)
 * ✓ Scroll only inside panel
 * ✓ Empty state handling
 *
 * @version 1.0
 * @date 2026-02-07
 * @author Medical Review UX Team
 */

import { useState, useEffect, useMemo, useCallback, memo } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Typography,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  IconButton,
  Stack,
  Divider,
  Tooltip,
  CircularProgress,
  Alert,
  alpha,
  useTheme
} from '@mui/material';
import {
  PictureAsPdf as PdfIcon,
  Image as ImageIcon,
  InsertDriveFile as FileIcon,
  Download as DownloadIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  Refresh as RefreshIcon,
  Close as CloseIcon,
  FolderOpen as EmptyIcon,
  BrokenImage as ErrorIcon
} from '@mui/icons-material';

// ============================================================================
// CONSTANTS
// ============================================================================

const PANEL_WIDTH = 360; // Fixed width
const MIN_ZOOM = 50;
const MAX_ZOOM = 200;
const ZOOM_STEP = 25;

const FILE_TYPES = {
  PDF: 'pdf',
  IMAGE: 'image',
  OTHER: 'other'
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Determine file type from MIME type or extension
 */
const getFileType = (mimeType, fileName) => {
  if (!mimeType && !fileName) return FILE_TYPES.OTHER;

  const mime = (mimeType || '').toLowerCase();
  const ext = (fileName || '').toLowerCase();

  if (mime.includes('pdf') || ext.endsWith('.pdf')) {
    return FILE_TYPES.PDF;
  }

  if (mime.startsWith('image/') || ext.match(/\.(jpg|jpeg|png|gif|webp|bmp)$/)) {
    return FILE_TYPES.IMAGE;
  }

  return FILE_TYPES.OTHER;
};

/**
 * Get appropriate icon for file type
 */
const getFileIcon = (fileType) => {
  switch (fileType) {
    case FILE_TYPES.PDF:
      return <PdfIcon sx={{ color: '#f44336' }} />;
    case FILE_TYPES.IMAGE:
      return <ImageIcon sx={{ color: '#2196f3' }} />;
    default:
      return <FileIcon sx={{ color: 'text.secondary' }} />;
  }
};

/**
 * Format file size
 */
const formatFileSize = (bytes) => {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

// ============================================================================
// PREVIEW COMPONENTS
// ============================================================================

/**
 * PDF Preview Component
 */
const PDFPreview = memo(({ url, onLoad, onError }) => {
  return (
    <Box
      component="iframe"
      src={`${url}#toolbar=0&navpanes=0&scrollbar=1`}
      title="PDF معاينة"
      onLoad={onLoad}
      onError={onError}
      sx={{
        width: '100%',
        height: '100%',
        border: 'none',
        bgcolor: 'white'
      }}
    />
  );
});

PDFPreview.displayName = 'PDFPreview';

PDFPreview.propTypes = {
  url: PropTypes.string.isRequired,
  onLoad: PropTypes.func,
  onError: PropTypes.func
};

/**
 * Image Preview Component
 */
const ImagePreview = memo(({ url, fileName, zoom, onLoad, onError }) => {
  return (
    <Box
      sx={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'auto',
        bgcolor: 'grey.100',
        p: '1.0rem'
      }}
    >
      <Box
        component="img"
        src={url}
        alt={fileName || 'معاينة'}
        onLoad={onLoad}
        onError={onError}
        sx={{
          maxWidth: '100%',
          maxHeight: '100%',
          objectFit: 'contain',
          transform: `scale(${zoom / 100})`,
          transition: 'transform 0.2s ease',
          boxShadow: 3
        }}
      />
    </Box>
  );
});

ImagePreview.displayName = 'ImagePreview';

ImagePreview.propTypes = {
  url: PropTypes.string.isRequired,
  fileName: PropTypes.string,
  zoom: PropTypes.number.isRequired,
  onLoad: PropTypes.func,
  onError: PropTypes.func
};

/**
 * Fallback for unsupported files
 */
const UnsupportedPreview = memo(({ fileName, fileSize, onDownload }) => {
  return (
    <Box
      sx={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '1.0rem',
        p: '2.0rem'
      }}
    >
      <FileIcon sx={{ fontSize: '4.0rem', color: 'text.disabled' }} />
      <Typography variant="body2" color="text.secondary" align="center">
        {fileName || 'مستند'}
      </Typography>
      {fileSize && (
        <Typography variant="caption" color="text.disabled">
          {formatFileSize(fileSize)}
        </Typography>
      )}
      <Typography variant="caption" color="text.secondary" align="center" sx={{ mt: 1 }}>
        لا يمكن معاينة هذا النوع من الملفات
      </Typography>
      <Tooltip title="تحميل الملف">
        <IconButton onClick={onDownload} color="primary" size="large" sx={{ mt: '1.0rem' }}>
          <DownloadIcon />
        </IconButton>
      </Tooltip>
    </Box>
  );
});

UnsupportedPreview.displayName = 'UnsupportedPreview';

UnsupportedPreview.propTypes = {
  fileName: PropTypes.string,
  fileSize: PropTypes.number,
  onDownload: PropTypes.func.isRequired
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

/**
 * Unified Attachment Viewer
 *
 * Used across Claims, Pre-Auth, and Approvals for consistent document preview
 */
const UnifiedAttachmentViewer = ({
  attachments = [],
  loading = false,
  onDownload,
  onRefresh,
  selectedAttachmentId,
  onSelectionChange,
  emptyMessage = 'لا توجد مستندات مرفقة',
  height = 'calc(100vh - 180px)'
}) => {
  const theme = useTheme();

  // State
  const [selectedIndex, setSelectedIndex] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState(null);
  const [zoom, setZoom] = useState(100);

  // Selected attachment
  const selectedAttachment = useMemo(() => {
    if (selectedIndex === null || selectedIndex === undefined) return null;
    return attachments[selectedIndex] || null;
  }, [attachments, selectedIndex]);

  // File type
  const fileType = useMemo(
    () =>
      selectedAttachment ? getFileType(selectedAttachment.mimeType || selectedAttachment.fileType, selectedAttachment.fileName) : null,
    [selectedAttachment]
  );

  // Keep selection valid when attachments list changes (no auto-select)
  useEffect(() => {
    if (!attachments.length) {
      setSelectedIndex(null);
      return;
    }

    if (selectedIndex !== null && selectedIndex >= attachments.length) {
      setSelectedIndex(null);
    }
  }, [attachments, selectedIndex]);

  useEffect(() => {
    if (!attachments.length) return;

    if (selectedAttachmentId === undefined || selectedAttachmentId === null) {
      setSelectedIndex(null);
      return;
    }

    const nextIndex = attachments.findIndex((attachment) => `${attachment.id}` === `${selectedAttachmentId}`);
    if (nextIndex >= 0 && nextIndex !== selectedIndex) {
      setSelectedIndex(nextIndex);
    }
  }, [attachments, selectedAttachmentId, selectedIndex]);

  // Reset zoom when changing attachment
  useEffect(() => {
    setZoom(100);
    setPreviewError(null);
  }, [selectedIndex]);

  // Handlers
  const handleZoomIn = useCallback(() => {
    setZoom((prev) => Math.min(prev + ZOOM_STEP, MAX_ZOOM));
  }, []);

  const handleZoomOut = useCallback(() => {
    setZoom((prev) => Math.max(prev - ZOOM_STEP, MIN_ZOOM));
  }, []);

  const handleDownload = useCallback(() => {
    if (selectedAttachment && onDownload) {
      onDownload(selectedAttachment);
    }
  }, [selectedAttachment, onDownload]);

  const handlePreviewLoad = useCallback(() => {
    setPreviewLoading(false);
    setPreviewError(null);
  }, []);

  const handlePreviewError = useCallback(() => {
    setPreviewLoading(false);
    setPreviewError('فشل في تحميل المعاينة');
  }, []);

  // Empty state
  if (!loading && attachments.length === 0) {
    return (
      <Paper
        sx={{
          width: PANEL_WIDTH,
          height,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '1.0rem',
          p: '2.0rem',
          bgcolor: 'background.default',
          borderRadius: '0.125rem'
        }}
      >
        <EmptyIcon sx={{ fontSize: '4.0rem', color: 'text.disabled' }} />
        <Typography variant="body2" color="text.secondary" align="center">
          {emptyMessage}
        </Typography>
        {onRefresh && (
          <Tooltip title="تحديث">
            <IconButton onClick={onRefresh} size="small">
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        )}
      </Paper>
    );
  }

  return (
    <Paper
      elevation={2}
      sx={{
        width: PANEL_WIDTH,
        height,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        borderRadius: '0.125rem'
      }}
    >
      {/* Header */}
      <Box
        sx={{
          px: '1.0rem',
          py: '0.75rem',
          bgcolor: theme.palette.mode === 'dark' ? 'grey.900' : 'grey.50',
          borderBottom: 1,
          borderColor: 'divider'
        }}
      >
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="subtitle2" fontWeight={600}>
            المستندات ({attachments.length})
          </Typography>
          {onRefresh && (
            <Tooltip title="تحديث">
              <IconButton onClick={onRefresh} size="small" disabled={loading}>
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      </Box>

      {/* Thumbnail List */}
      <Box
        sx={{
          maxHeight: '12.5rem',
          overflow: 'auto',
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper'
        }}
      >
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: '1.5rem' }}>
            <CircularProgress size={24} />
          </Box>
        ) : (
          <List dense disablePadding>
            {attachments.map((attachment, index) => {
              const type = getFileType(attachment.mimeType || attachment.fileType, attachment.fileName);
              const isSelected = index === selectedIndex;

              return (
                <ListItem key={attachment.id || index} disablePadding>
                  <ListItemButton
                    selected={isSelected}
                    onClick={() => {
                      setSelectedIndex(index);
                      onSelectionChange?.(attachment.id || null);
                    }}
                    sx={{
                      py: 1,
                      px: '1.0rem',
                      '&.Mui-selected': {
                        bgcolor: alpha(theme.palette.primary.main, 0.12),
                        '&:hover': {
                          bgcolor: alpha(theme.palette.primary.main, 0.18)
                        }
                      }
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: '2.25rem' }}>{getFileIcon(type)}</ListItemIcon>
                    <ListItemText
                      primary={attachment.fileName || attachment.name || `مستند ${index + 1}`}
                      secondary={formatFileSize(attachment.fileSize || attachment.size)}
                      primaryTypographyProps={{
                        variant: 'body2',
                        noWrap: true
                      }}
                      secondaryTypographyProps={{
                        variant: 'caption'
                      }}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        )}
      </Box>

      {/* Preview Controls (for images) */}
      {fileType === FILE_TYPES.IMAGE && (
        <Box
          sx={{
            px: '1.0rem',
            py: 1,
            bgcolor: 'background.default',
            borderBottom: 1,
            borderColor: 'divider'
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
            <Stack direction="row" spacing={0.5}>
              <Tooltip title="تصغير">
                <span>
                  <IconButton size="small" onClick={handleZoomOut} disabled={zoom <= MIN_ZOOM}>
                    <ZoomOutIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Typography variant="caption" sx={{ px: 1, py: 0.5, alignSelf: 'center', minWidth: '2.8125rem', textAlign: 'center' }}>
                {zoom}%
              </Typography>
              <Tooltip title="تكبير">
                <span>
                  <IconButton size="small" onClick={handleZoomIn} disabled={zoom >= MAX_ZOOM}>
                    <ZoomInIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </Stack>

            <Tooltip title="تحميل">
              <IconButton size="small" onClick={handleDownload}>
                <DownloadIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        </Box>
      )}

      {/* Preview Area */}
      <Box sx={{ flex: 1, position: 'relative', overflow: 'hidden', bgcolor: 'grey.50' }}>
        {previewLoading && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'background.paper',
              zIndex: 1
            }}
          >
            <CircularProgress />
          </Box>
        )}

        {previewError ? (
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              gap: '1.0rem',
              p: '1.5rem'
            }}
          >
            <ErrorIcon sx={{ fontSize: '3.0rem', color: 'error.main' }} />
            <Typography variant="body2" color="error">
              {previewError}
            </Typography>
            <Tooltip title="إعادة المحاولة">
              <IconButton onClick={() => setPreviewError(null)} color="primary">
                <RefreshIcon />
              </IconButton>
            </Tooltip>
          </Box>
        ) : selectedAttachment ? (
          <>
            {fileType === FILE_TYPES.PDF && selectedAttachment.url && (
              <PDFPreview
                url={selectedAttachment.url || selectedAttachment.downloadUrl}
                onLoad={handlePreviewLoad}
                onError={handlePreviewError}
              />
            )}

            {fileType === FILE_TYPES.IMAGE && selectedAttachment.url && (
              <ImagePreview
                url={selectedAttachment.url || selectedAttachment.downloadUrl}
                fileName={selectedAttachment.fileName || selectedAttachment.name}
                zoom={zoom}
                onLoad={handlePreviewLoad}
                onError={handlePreviewError}
              />
            )}

            {(fileType === FILE_TYPES.OTHER || !selectedAttachment.url) && (
              <UnsupportedPreview
                fileName={selectedAttachment.fileName || selectedAttachment.name}
                fileSize={selectedAttachment.fileSize || selectedAttachment.size}
                onDownload={handleDownload}
              />
            )}
          </>
        ) : (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%'
            }}
          >
            <Typography variant="body2" color="text.secondary">
              اختر مستنداً للمعاينة
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
};

UnifiedAttachmentViewer.propTypes = {
  /** Array of attachments to display */
  attachments: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      fileName: PropTypes.string,
      name: PropTypes.string,
      fileSize: PropTypes.number,
      size: PropTypes.number,
      mimeType: PropTypes.string,
      fileType: PropTypes.string,
      url: PropTypes.string,
      downloadUrl: PropTypes.string
    })
  ),
  /** Loading state */
  loading: PropTypes.bool,
  /** Download handler */
  onDownload: PropTypes.func,
  /** Refresh handler */
  onRefresh: PropTypes.func,
  /** Controlled selected attachment id */
  selectedAttachmentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  /** Called when user selects an attachment */
  onSelectionChange: PropTypes.func,
  /** Empty state message */
  emptyMessage: PropTypes.string,
  /** Panel height */
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default memo(UnifiedAttachmentViewer);


