/**
 * DocumentPreviewDrawer.jsx - Unified Side Panel Document Preview
 *
 * Reusable drawer component for previewing documents (PDF/Images)
 * Used in:
 * - Provider Documents
 * - Claims Inbox
 * - Pre-Approvals Inbox
 *
 * Features:
 * - Side drawer/workbench (right-to-left for Arabic)
 * - Optional slide strip for multiple documents
 * - Supports PDF (iframe) and Images (img)
 * - Safe Word/Office fallback with download/open affordance
 * - Download button (optional)
 * - Close button
 * - No forced download
 * - No new tab opening
 *
 * @version 1.0 - 2026-01-30
 */

import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Box, Typography, IconButton, Stack, CircularProgress, Paper, Button, Tooltip, alpha, Chip } from '@mui/material';
import axiosClient from 'utils/axios';
import {
  Close as CloseIcon,
  Download as DownloadIcon,
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  Refresh as RefreshIcon,
  BrokenImage as BrokenImageIcon,
  PictureAsPdf as PdfIcon,
  Image as ImageIcon,
  InsertDriveFile as FileIcon,
  Article as WordIcon
} from '@mui/icons-material';

// ============================================================================
// CONSTANTS
// ============================================================================

const DRAWER_WIDTH = 980;
const SLIDE_STRIP_WIDTH = 260;
const ZOOM_LEVELS = [0.5, 0.75, 1, 1.25, 1.5, 2, 2.5];
const DEFAULT_ZOOM_INDEX = 2; // 100%

const SUPPORTED_IMAGE_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'];
const WORD_TYPES = [
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-word',
  'application/doc',
  'application/docx'
];

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

const getPreviewType = (mimeType) => {
  if (!mimeType) return 'unsupported';
  const type = mimeType.toLowerCase();

  if (SUPPORTED_IMAGE_TYPES.includes(type)) return 'image';
  if (type === 'application/pdf') return 'pdf';
  if (WORD_TYPES.includes(type) || type.includes('wordprocessingml')) return 'word';

  return 'unsupported';
};

const getFileIcon = (mimeType) => {
  const type = getPreviewType(mimeType);
  switch (type) {
    case 'image':
      return <ImageIcon sx={{ fontSize: '3.0rem', color: 'info.main' }} />;
    case 'pdf':
      return <PdfIcon sx={{ fontSize: '3.0rem', color: 'error.main' }} />;
    case 'word':
      return <WordIcon sx={{ fontSize: '3.0rem', color: 'primary.main' }} />;
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

const normalizeDocument = (doc = {}, fallback = {}) => ({
  id: doc.id ?? doc.documentId ?? doc.url ?? doc.documentUrl ?? fallback.id ?? 'single-document',
  documentUrl: doc.documentUrl ?? doc.url ?? doc.downloadUrl ?? fallback.documentUrl,
  fileName: doc.fileName ?? doc.name ?? fallback.fileName,
  mimeType: doc.mimeType ?? doc.fileType ?? doc.contentType ?? fallback.mimeType,
  fileSize: doc.fileSize ?? doc.size ?? fallback.fileSize,
  documentTitle: doc.documentTitle ?? doc.title ?? doc.documentTypeLabel ?? doc.documentType ?? fallback.documentTitle,
  onDownload: doc.onDownload ?? fallback.onDownload
});

const DocumentPreviewDrawer = memo(
  ({
    open,
    onClose,
    documentUrl,
    fileName,
    mimeType,
    fileSize,
    documentTitle,
    onDownload,
    showDownload = true,
    documents = [],
    initialDocumentId = null
  }) => {
    const normalizedDocuments = useMemo(() => {
      const provided = Array.isArray(documents) ? documents.filter(Boolean).map((doc) => normalizeDocument(doc)) : [];
      if (provided.length) return provided;
      if (!documentUrl && !fileName) return [];
      return [
        normalizeDocument(
          {},
          {
            id: 'single-document',
            documentUrl,
            fileName,
            mimeType,
            fileSize,
            documentTitle,
            onDownload
          }
        )
      ];
    }, [documents, documentUrl, fileName, mimeType, fileSize, documentTitle, onDownload]);

    const [selectedDocumentId, setSelectedDocumentId] = useState(initialDocumentId);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [zoomIndex, setZoomIndex] = useState(DEFAULT_ZOOM_INDEX);
    const [blobUrl, setBlobUrl] = useState(null);

    useEffect(() => {
      if (!open) return;
      const hasInitial = initialDocumentId && normalizedDocuments.some((doc) => doc.id === initialDocumentId);
      setSelectedDocumentId(hasInitial ? initialDocumentId : normalizedDocuments[0]?.id ?? null);
    }, [open, initialDocumentId, normalizedDocuments]);

    const selectedDocument = useMemo(
      () => normalizedDocuments.find((doc) => doc.id === selectedDocumentId) || normalizedDocuments[0] || null,
      [normalizedDocuments, selectedDocumentId]
    );

    const selectedDocumentUrl = selectedDocument?.documentUrl;
    const selectedFileName = selectedDocument?.fileName;
    const selectedMimeType = selectedDocument?.mimeType;
    const selectedFileSize = selectedDocument?.fileSize;
    const selectedTitle = selectedDocument?.documentTitle;
    const selectedDownloadHandler = selectedDocument?.onDownload || onDownload;

    const previewType = getPreviewType(selectedMimeType);
    const currentZoom = ZOOM_LEVELS[zoomIndex];

    // Fetch the document as an authenticated blob — a native <img>/<iframe> src=
    // request never carries the axios Authorization header, so fetching the raw
    // documentUrl directly always 401s against these protected endpoints and the
    // preview silently fails. Fetch via axios instead, then preview the blob URL.
    useEffect(() => {
      let cancelled = false;
      let objectUrl = null;

      if (!open || !selectedDocumentUrl) {
        setBlobUrl(null);
        return undefined;
      }

      setLoading(true);
      setError(false);
      setZoomIndex(DEFAULT_ZOOM_INDEX);

      // documentUrl is treated like any normal axiosClient url: relative to the
      // '/api/v1' instance baseURL. Some callers historically pre-resolved a
      // full '/api/v1/...' path themselves — the shared request interceptor in
      // utils/axios.js already strips a leading '/api/v1/' before axios adds its
      // own baseURL, so both styles resolve to the same final URL without a
      // double prefix. Do NOT override baseURL here.
      axiosClient
        .get(selectedDocumentUrl, { responseType: 'blob' })
        .then((response) => {
          if (cancelled) return;
          objectUrl = window.URL.createObjectURL(new Blob([response.data], { type: selectedMimeType || response.data.type }));
          setBlobUrl(objectUrl);
          // Fetch succeeded — the blob is ready. Do NOT wait for <img>/<iframe>
          // onLoad to clear `loading`: those elements only mount once
          // loading=false, so waiting on their onLoad here deadlocks forever
          // (element never mounts -> onLoad never fires -> loading never clears).
          setLoading(false);
        })
        .catch(() => {
          if (!cancelled) {
            setLoading(false);
            setError(true);
          }
        });

      return () => {
        cancelled = true;
        if (objectUrl) window.URL.revokeObjectURL(objectUrl);
      };
    }, [open, selectedDocumentUrl, selectedMimeType]);

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
      if (selectedDownloadHandler) {
        selectedDownloadHandler(selectedDocument);
      } else if (blobUrl) {
        // Create a download link from the already-fetched authenticated blob
        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = selectedFileName || 'document';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    }, [blobUrl, selectedDocument, selectedDownloadHandler, selectedFileName]);

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
              src={blobUrl}
              alt={selectedFileName || 'معاينة المستند'}
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
              src={`${blobUrl}#toolbar=1&navpanes=0&scrollbar=1`}
              title={selectedFileName || 'معاينة PDF'}
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

      // Word/Office files cannot be safely rendered from protected local blob URLs
      // without a dedicated server-side conversion pipeline. Keep behavior honest:
      // show a clean preview card and provide download/open action.
      if (previewType === 'word') {
        return (
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              minHeight: '25.0rem',
              bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04),
              textAlign: 'center',
              p: 3
            }}
          >
            {getFileIcon(selectedMimeType)}
            <Typography variant="h6" sx={{ mt: '1.0rem', fontWeight: 700 }}>
              {selectedFileName || 'ملف Word'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1, maxWidth: '25rem' }}>
              ملفات Word تحتاج تحويلًا آمنًا من الخادم قبل عرضها داخل المتصفح. يمكنك تحميل الملف الآن، ويمكن لاحقًا ربط نفس
              المكوّن بخدمة تحويل DOC/DOCX إلى PDF.
            </Typography>
            {selectedFileSize && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
                {formatFileSize(selectedFileSize)}
              </Typography>
            )}
            {showDownload && (
              <Button variant="contained" startIcon={<DownloadIcon />} onClick={handleDownload} sx={{ mt: '1.5rem' }}>
                تحميل الملف
              </Button>
            )}
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
            {selectedFileName || 'مستند'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            لا يمكن معاينة هذا النوع من الملفات
          </Typography>
          {selectedFileSize && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
              {formatFileSize(selectedFileSize)}
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

    const renderSlideStrip = () => {
      if (normalizedDocuments.length <= 1) return null;

      return (
        <Paper
          elevation={0}
          sx={{
            width: SLIDE_STRIP_WIDTH,
            flexShrink: 0,
            borderRight: '1px solid',
            borderColor: 'divider',
            bgcolor: 'grey.50',
            overflowY: 'auto',
            p: 1
          }}
        >
          <Stack spacing={1}>
            <Typography variant="subtitle2" fontWeight={700} color="primary.main" sx={{ px: 1, py: 0.5 }}>
              الشرائح / المستندات ({normalizedDocuments.length})
            </Typography>
            {normalizedDocuments.map((doc, index) => {
              const selected = selectedDocument?.id === doc.id;
              return (
                <Paper
                  key={doc.id}
                  component="button"
                  type="button"
                  onClick={() => setSelectedDocumentId(doc.id)}
                  elevation={selected ? 2 : 0}
                  sx={{
                    width: '100%',
                    textAlign: 'right',
                    cursor: 'pointer',
                    border: '1px solid',
                    borderColor: selected ? 'primary.main' : 'divider',
                    bgcolor: selected ? (theme) => alpha(theme.palette.primary.main, 0.08) : 'background.paper',
                    borderRadius: '0.5rem',
                    p: 1,
                    transition: 'all 0.15s ease',
                    '&:hover': {
                      borderColor: 'primary.main',
                      bgcolor: (theme) => alpha(theme.palette.primary.main, 0.06)
                    }
                  }}
                >
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Box
                      sx={{
                        width: 44,
                        height: 56,
                        borderRadius: '0.35rem',
                        bgcolor: 'grey.100',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}
                    >
                      {getFileIcon(doc.mimeType)}
                    </Box>
                    <Box sx={{ minWidth: 0, flex: 1 }}>
                      <Stack direction="row" alignItems="center" spacing={0.5}>
                        <Chip label={index + 1} size="small" color={selected ? 'primary' : 'default'} sx={{ height: 20 }} />
                        <Typography variant="caption" color="text.secondary">
                          {formatFileSize(doc.fileSize)}
                        </Typography>
                      </Stack>
                      <Typography variant="body2" fontWeight={selected ? 700 : 500} noWrap title={doc.documentTitle || doc.fileName}>
                        {doc.documentTitle || doc.fileName || 'مستند'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {doc.fileName || '—'}
                      </Typography>
                    </Box>
                  </Stack>
                </Paper>
              );
            })}
          </Stack>
        </Paper>
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
            maxWidth: '96vw'
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
                  {selectedTitle || selectedFileName || 'معاينة المستند'}
                </Typography>
                {selectedFileSize && (
                  <Typography variant="caption" sx={{ opacity: 0.8 }}>
                    {formatFileSize(selectedFileSize)}
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
            {showDownload && selectedDocumentUrl && (
              <Tooltip title="تحميل الملف">
                <IconButton size="small" onClick={handleDownload} color="primary">
                  <DownloadIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        </Paper>

        {/* Preview Content */}
        <Box sx={{ flex: 1, overflow: 'hidden', height: 'calc(100vh - 140px)', display: 'flex', minHeight: 0 }}>
          {renderSlideStrip()}
          <Box sx={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
            {selectedDocumentUrl ? (
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
  showDownload: PropTypes.bool,
  /** Optional list of documents for slide-strip preview */
  documents: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      documentUrl: PropTypes.string,
      url: PropTypes.string,
      downloadUrl: PropTypes.string,
      fileName: PropTypes.string,
      name: PropTypes.string,
      mimeType: PropTypes.string,
      fileType: PropTypes.string,
      contentType: PropTypes.string,
      fileSize: PropTypes.number,
      size: PropTypes.number,
      documentTitle: PropTypes.string,
      title: PropTypes.string,
      documentTypeLabel: PropTypes.string,
      documentType: PropTypes.string,
      onDownload: PropTypes.func
    })
  ),
  /** Optional selected document id when opening */
  initialDocumentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

DocumentPreviewDrawer.displayName = 'DocumentPreviewDrawer';

export default DocumentPreviewDrawer;
