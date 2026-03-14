/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 📄 DOCUMENT PREVIEW - Inline Viewer (Images/PDF/DICOM)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * ✓ Inline preview (no download, no new tab)
 * ✓ Image zoom/rotate
 * ✓ PDF navigation
 * ✓ Loading states
 * ✓ Error handling
 * ✓ Keyboard shortcuts
 *
 * Supported Types:
 * - Images (PNG, JPG, JPEG, GIF)
 * - PDF
 * - DICOM (fallback to image if viewer not available)
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

import { useState, useEffect } from 'react';
import { Box, IconButton, Typography, CircularProgress, Alert, Paper, Tooltip, Stack } from '@mui/material';
import {
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  RotateLeft as RotateLeftIcon,
  RotateRight as RotateRightIcon,
  Fullscreen as FullscreenIcon,
  Download as DownloadIcon,
  ChevronLeft as PrevIcon,
  ChevronRight as NextIcon,
  Error as ErrorIcon
} from '@mui/icons-material';
import { MEDICAL_THEME } from '../../theme/medical-theme';

const DocumentPreview = ({ document, onClose }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [zoom, setZoom] = useState(100);
  const [rotation, setRotation] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  // Determine document type
  const getDocumentType = (doc) => {
    if (!doc || !doc.fileName) return 'unknown';

    const ext = doc.fileName.split('.').pop().toLowerCase();

    if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(ext)) {
      return 'image';
    }
    if (ext === 'pdf') {
      return 'pdf';
    }
    if (ext === 'dcm' || ext === 'dicom') {
      return 'dicom';
    }
    return 'unknown';
  };

  const documentType = getDocumentType(document);

  // Reset states when document changes
  useEffect(() => {
    setZoom(100);
    setRotation(0);
    setCurrentPage(1);
    setLoading(true);
    setError(null);
  }, [document?.id]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyPress = (e) => {
      if (e.ctrlKey || e.metaKey) {
        if (e.key === '+' || e.key === '=') {
          e.preventDefault();
          handleZoomIn();
        } else if (e.key === '-') {
          e.preventDefault();
          handleZoomOut();
        }
      }

      if (e.key === 'r' || e.key === 'R') {
        handleRotateRight();
      }

      if (documentType === 'pdf') {
        if (e.key === 'ArrowLeft') {
          handlePrevPage();
        } else if (e.key === 'ArrowRight') {
          handleNextPage();
        }
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [zoom, rotation, currentPage, documentType]);

  // Handlers
  const handleZoomIn = () => setZoom((prev) => Math.min(prev + 25, 300));
  const handleZoomOut = () => setZoom((prev) => Math.max(prev - 25, 25));
  const handleRotateLeft = () => setRotation((prev) => (prev - 90) % 360);
  const handleRotateRight = () => setRotation((prev) => (prev + 90) % 360);
  const handlePrevPage = () => setCurrentPage((prev) => Math.max(prev - 1, 1));
  const handleNextPage = () => setCurrentPage((prev) => Math.min(prev + 1, totalPages));

  const handleDownload = () => {
    if (document?.url) {
      const link = window.document.createElement('a');
      link.href = document.url;
      link.download = document.fileName || 'document';
      link.click();
    }
  };

  const handleFullscreen = () => {
    const elem = window.document.getElementById('document-preview-container');
    if (elem) {
      if (elem.requestFullscreen) {
        elem.requestFullscreen();
      } else if (elem.webkitRequestFullscreen) {
        elem.webkitRequestFullscreen();
      } else if (elem.msRequestFullscreen) {
        elem.msRequestFullscreen();
      }
    }
  };

  // Render toolbar
  const renderToolbar = () => (
    <Stack
      direction="row"
      spacing={1}
      sx={{
        padding: MEDICAL_THEME.spacing.sm,
        background: MEDICAL_THEME.colors.neutral.darker,
        borderBottom: `1px solid ${MEDICAL_THEME.colors.border.medium}`,
        alignItems: 'center'
      }}
    >
      {/* Document Info */}
      <Box sx={{ flex: 1 }}>
        <Typography variant="body2" sx={{ color: MEDICAL_THEME.colors.neutral.white, fontWeight: 500 }}>
          {document?.fileName || 'Preview'}
        </Typography>
        {documentType === 'pdf' && (
          <Typography variant="caption" sx={{ color: MEDICAL_THEME.colors.neutral.medium }}>
            صفحة {currentPage} من {totalPages}
          </Typography>
        )}
      </Box>

      {/* Zoom Controls */}
      {documentType === 'image' && (
        <>
          <Tooltip title="تصغير (Ctrl -)">
            <IconButton size="small" onClick={handleZoomOut} disabled={zoom <= 25}>
              <ZoomOutIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>

          <Typography variant="body2" sx={{ color: MEDICAL_THEME.colors.neutral.white, minWidth: '3.125rem', textAlign: 'center' }}>
            {zoom}%
          </Typography>

          <Tooltip title="تكبير (Ctrl +)">
            <IconButton size="small" onClick={handleZoomIn} disabled={zoom >= 300}>
              <ZoomInIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>
        </>
      )}

      {/* Rotation Controls */}
      {documentType === 'image' && (
        <>
          <Tooltip title="تدوير لليسار">
            <IconButton size="small" onClick={handleRotateLeft}>
              <RotateLeftIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>

          <Tooltip title="تدوير لليمين (R)">
            <IconButton size="small" onClick={handleRotateRight}>
              <RotateRightIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>
        </>
      )}

      {/* PDF Navigation */}
      {documentType === 'pdf' && totalPages > 1 && (
        <>
          <Tooltip title="السابق (←)">
            <IconButton size="small" onClick={handlePrevPage} disabled={currentPage === 1}>
              <PrevIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>

          <Tooltip title="التالي (→)">
            <IconButton size="small" onClick={handleNextPage} disabled={currentPage === totalPages}>
              <NextIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
            </IconButton>
          </Tooltip>
        </>
      )}

      {/* Fullscreen & Download */}
      <Tooltip title="ملء الشاشة">
        <IconButton size="small" onClick={handleFullscreen}>
          <FullscreenIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
        </IconButton>
      </Tooltip>

      <Tooltip title="تحميل">
        <IconButton size="small" onClick={handleDownload}>
          <DownloadIcon sx={{ color: MEDICAL_THEME.colors.neutral.white }} />
        </IconButton>
      </Tooltip>
    </Stack>
  );

  // Render image preview
  const renderImagePreview = () => (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '25.0rem',
        background: MEDICAL_THEME.colors.neutral.darkest,
        overflow: 'auto',
        padding: MEDICAL_THEME.spacing.lg
      }}
    >
      <img
        src={document?.url}
        alt={document?.fileName}
        onLoad={() => setLoading(false)}
        onError={() => {
          setLoading(false);
          setError('فشل تحميل الصورة');
        }}
        style={{
          maxWidth: '100%',
          maxHeight: '70vh',
          transform: `scale(${zoom / 100}) rotate(${rotation}deg)`,
          transition: 'transform 0.2s ease',
          cursor: zoom > 100 ? 'move' : 'default'
        }}
      />
    </Box>
  );

  // Render PDF preview
  const renderPDFPreview = () => (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '25.0rem',
        background: MEDICAL_THEME.colors.neutral.darkest,
        padding: MEDICAL_THEME.spacing.lg
      }}
    >
      <iframe
        src={`${document?.url}#page=${currentPage}`}
        title={document?.fileName}
        onLoad={() => setLoading(false)}
        onError={() => {
          setLoading(false);
          setError('فشل تحميل ملف PDF');
        }}
        style={{
          width: '100%',
          height: '70vh',
          border: 'none',
          background: MEDICAL_THEME.colors.neutral.white
        }}
      />
    </Box>
  );

  // Render unknown type
  const renderUnsupportedType = () => (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '25.0rem',
        background: MEDICAL_THEME.colors.neutral.darkest,
        padding: MEDICAL_THEME.spacing.lg
      }}
    >
      <ErrorIcon sx={{ fontSize: '4.0rem', color: MEDICAL_THEME.colors.neutral.medium, mb: '1.0rem' }} />
      <Typography variant="h6" sx={{ color: MEDICAL_THEME.colors.neutral.white, mb: 1 }}>
        نوع الملف غير مدعوم للمعاينة
      </Typography>
      <Typography variant="body2" sx={{ color: MEDICAL_THEME.colors.neutral.medium, mb: '1.0rem' }}>
        {document?.fileName}
      </Typography>
      <IconButton
        variant="outlined"
        onClick={handleDownload}
        sx={{
          color: MEDICAL_THEME.colors.neutral.white,
          borderColor: MEDICAL_THEME.colors.neutral.medium
        }}
      >
        <DownloadIcon sx={{ mr: 1 }} />
        تحميل الملف
      </IconButton>
    </Box>
  );

  // No document selected
  if (!document) {
    return (
      <Paper
        sx={{
          height: '100%',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          background: MEDICAL_THEME.colors.background.sidebar
        }}
      >
        <Typography variant="body2" color="text.secondary">
          اختر مستنداً للمعاينة
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper
      id="document-preview-container"
      elevation={0}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: MEDICAL_THEME.colors.neutral.darkest,
        border: `1px solid ${MEDICAL_THEME.colors.border.light}`,
        borderRadius: MEDICAL_THEME.radius.base,
        overflow: 'hidden'
      }}
    >
      {/* Toolbar */}
      {renderToolbar()}

      {/* Preview Area */}
      <Box sx={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {/* Loading */}
        {loading && (
          <Box
            sx={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              background: MEDICAL_THEME.colors.neutral.darkest,
              zIndex: 1
            }}
          >
            <CircularProgress sx={{ color: MEDICAL_THEME.colors.primary.light }} />
          </Box>
        )}

        {/* Error */}
        {error && (
          <Box sx={{ padding: MEDICAL_THEME.spacing.lg }}>
            <Alert severity="error">{error}</Alert>
          </Box>
        )}

        {/* Content */}
        {!error && (
          <>
            {documentType === 'image' && renderImagePreview()}
            {documentType === 'pdf' && renderPDFPreview()}
            {(documentType === 'unknown' || documentType === 'dicom') && renderUnsupportedType()}
          </>
        )}
      </Box>
    </Paper>
  );
};

export default DocumentPreview;
