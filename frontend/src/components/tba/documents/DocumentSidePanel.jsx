/**
 * DocumentSidePanel.jsx - Document List Side Panel Component
 *
 * Displays list of documents for Claims/PreAuthorizations review
 * with inline preview capability.
 *
 * Features:
 * - Fetches documents by referenceType + referenceId
 * - Shows document list with status badges
 * - On-click document selection for preview
 * - Collapsible panel option
 *
 * @version 1.0 - 2026-01-29
 * Phase 1: Internal Review (Claims & PreAuthorizations)
 */

import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import PropTypes from 'prop-types';
import {
  Box,
  Paper,
  Typography,
  Stack,
  Chip,
  IconButton,
  Collapse,
  Divider,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  CircularProgress,
  Alert,
  Tooltip,
  Badge,
  alpha
} from '@mui/material';
import {
  AttachFile as AttachmentIcon,
  Image as ImageIcon,
  PictureAsPdf as PdfIcon,
  InsertDriveFile as FileIcon,
  Description as DocIcon,
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  Refresh as RefreshIcon,
  CheckCircle as ApprovedIcon,
  Cancel as RejectedIcon,
  CloudUpload as UploadedIcon,
  Warning as RequiredIcon,
  Visibility as PreviewIcon,
  VisibilityOff as HideIcon
} from '@mui/icons-material';

import DocumentPreview from './DocumentPreview';

// ============================================================================
// CONSTANTS
// ============================================================================

/**
 * Document status configuration
 */
const DOCUMENT_STATUS = {
  REQUIRED: {
    value: 'REQUIRED',
    label: 'مطلوب',
    color: 'warning',
    icon: RequiredIcon
  },
  UPLOADED: {
    value: 'UPLOADED',
    label: 'مرفوع',
    color: 'info',
    icon: UploadedIcon
  },
  APPROVED: {
    value: 'APPROVED',
    label: 'مقبول',
    color: 'success',
    icon: ApprovedIcon
  },
  REJECTED: {
    value: 'REJECTED',
    label: 'مرفوض',
    color: 'error',
    icon: RejectedIcon
  }
};

/**
 * Document type labels (Arabic)
 */
const DOCUMENT_TYPE_LABELS = {
  MEDICAL_REPORT: 'تقرير طبي',
  INVOICE: 'فاتورة',
  PRESCRIPTION: 'وصفة طبية',
  LAB_RESULT: 'نتيجة مختبر',
  XRAY: 'أشعة',
  COMMITMENT_LETTER: 'خطاب تعهد',
  ID_COPY: 'صورة الهوية',
  INSURANCE_CARD: 'بطاقة التأمين',
  REFERRAL: 'تحويل طبي',
  OTHER: 'أخرى'
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get file icon based on MIME type
 */
const getFileIcon = (mimeType) => {
  if (!mimeType) return FileIcon;

  const type = mimeType.toLowerCase();

  if (type.startsWith('image/')) return ImageIcon;
  if (type === 'application/pdf') return PdfIcon;
  if (type.includes('word') || type.includes('document')) return DocIcon;

  return FileIcon;
};

/**
 * Get status configuration
 */
const getStatusConfig = (status) => {
  return DOCUMENT_STATUS[status] || DOCUMENT_STATUS.UPLOADED;
};

/**
 * Format file size
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '';
  const units = ['بايت', 'ك.ب', 'م.ب'];
  let index = 0;
  let size = bytes;

  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index++;
  }

  return `${size.toFixed(index > 0 ? 1 : 0)} ${units[index]}`;
};

// ============================================================================
// SUB-COMPONENTS
// ============================================================================

/**
 * Document Status Badge
 */
const StatusBadge = memo(({ status }) => {
  const config = getStatusConfig(status);
  const StatusIcon = config.icon;

  return (
    <Chip
      size="small"
      label={config.label}
      color={config.color}
      icon={<StatusIcon sx={{ fontSize: '0.875rem' }} />}
      sx={{
        height: '1.5rem',
        '& .MuiChip-label': { px: 1, fontSize: '0.7rem' },
        '& .MuiChip-icon': { ml: 0.5 }
      }}
    />
  );
});

StatusBadge.displayName = 'StatusBadge';

/**
 * Document List Item
 */
const DocumentListItem = memo(({ document, isSelected, onSelect }) => {
  const FileTypeIcon = getFileIcon(document.fileType || document.mimeType);
  const statusConfig = getStatusConfig(document.status);

  const hasFile = document.status !== 'REQUIRED' && document.fileName;

  return (
    <ListItem
      disablePadding
      sx={{
        borderRadius: 1,
        mb: 0.5,
        bgcolor: isSelected ? (theme) => alpha(theme.palette.primary.main, 0.08) : 'transparent',
        border: isSelected ? 1 : 0,
        borderColor: 'primary.main'
      }}
    >
      <ListItemButton
        onClick={() => onSelect(document)}
        disabled={!hasFile}
        sx={{
          borderRadius: 1,
          py: 1,
          opacity: hasFile ? 1 : 0.6
        }}
      >
        <ListItemIcon sx={{ minWidth: '2.5rem' }}>
          <Badge badgeContent={document.status === 'REJECTED' ? '!' : null} color="error" overlap="circular">
            <FileTypeIcon
              sx={{
                color: hasFile ? 'primary.main' : 'text.disabled',
                fontSize: '1.5rem'
              }}
            />
          </Badge>
        </ListItemIcon>

        <ListItemText
          primary={
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" fontWeight={isSelected ? 600 : 400} noWrap sx={{ maxWidth: '9.375rem' }}>
                {document.documentTypeLabel || DOCUMENT_TYPE_LABELS[document.documentType] || document.fileName || 'مستند'}
              </Typography>
              <StatusBadge status={document.status} />
            </Stack>
          }
          secondary={
            <Stack spacing={0.5}>
              {hasFile && (
                <Typography variant="caption" color="text.secondary" noWrap>
                  {document.fileName} • {formatFileSize(document.fileSize)}
                </Typography>
              )}
              {document.status === 'REJECTED' && document.rejectionReason && (
                <Typography
                  variant="caption"
                  color="error.main"
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5
                  }}
                >
                  <RejectedIcon sx={{ fontSize: '0.75rem' }} />
                  {document.rejectionReason}
                </Typography>
              )}
            </Stack>
          }
        />
      </ListItemButton>
    </ListItem>
  );
});

DocumentListItem.displayName = 'DocumentListItem';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const DocumentSidePanel = memo(
  ({
    documents = [],
    loading = false,
    error = null,
    onRefresh,
    onDocumentSelect,
    selectedDocumentId,
    title = 'المستندات المرفقة',
    showPreview = true,
    previewHeight = 350,
    collapsible = true,
    defaultExpanded = true,
    emptyMessage = 'لا توجد مستندات مرفوعة',
    downloadUrlBuilder,
    variant = 'split' // 'split' | 'list-only' | 'preview-only'
  }) => {
    const [expanded, setExpanded] = useState(defaultExpanded);
    const [selectedDocument, setSelectedDocument] = useState(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [previewError, setPreviewError] = useState(null);
    const [showPreviewPanel, setShowPreviewPanel] = useState(showPreview);

    // Count documents by status
    const statusCounts = useMemo(() => {
      return documents.reduce((acc, doc) => {
        acc[doc.status] = (acc[doc.status] || 0) + 1;
        return acc;
      }, {});
    }, [documents]);

    // Total uploaded documents
    const uploadedCount = useMemo(() => {
      return documents.filter((d) => d.status !== 'REQUIRED').length;
    }, [documents]);

    // Auto-select first document with file
    useEffect(() => {
      if (documents.length > 0 && !selectedDocument) {
        const firstWithFile = documents.find((d) => d.status !== 'REQUIRED' && d.fileName);
        if (firstWithFile) {
          handleDocumentSelect(firstWithFile);
        }
      }
    }, [documents]);

    // Handle document selection
    const handleDocumentSelect = useCallback(
      async (document) => {
        setSelectedDocument(document);
        setPreviewError(null);

        if (onDocumentSelect) {
          onDocumentSelect(document);
        }

        // Build preview URL
        if (downloadUrlBuilder && document.id) {
          try {
            setPreviewLoading(true);
            const url = await downloadUrlBuilder(document);
            setPreviewUrl(url);
          } catch (err) {
            console.error('Error building preview URL:', err);
            setPreviewError('فشل تحميل المستند');
          } finally {
            setPreviewLoading(false);
          }
        }
      },
      [downloadUrlBuilder, onDocumentSelect]
    );

    // Handle download
    const handleDownload = useCallback(() => {
      if (previewUrl) {
        window.open(previewUrl, '_blank');
      }
    }, [previewUrl]);

    // Toggle expand
    const handleToggleExpand = useCallback(() => {
      setExpanded((prev) => !prev);
    }, []);

    // Toggle preview
    const handleTogglePreview = useCallback(() => {
      setShowPreviewPanel((prev) => !prev);
    }, []);

    // ========================================
    // RENDER
    // ========================================

    return (
      <Paper
        variant="outlined"
        sx={{
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          borderRadius: '0.25rem',
          overflow: 'hidden'
        }}
      >
        {/* Header */}
        <Box
          sx={{
            px: '1.0rem',
            py: '0.75rem',
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <AttachmentIcon />
            <Typography variant="subtitle1" fontWeight={600}>
              {title}
            </Typography>
            <Chip
              size="small"
              label={`${uploadedCount} / ${documents.length}`}
              sx={{
                bgcolor: 'rgba(255,255,255,0.2)',
                color: 'inherit',
                height: '1.375rem',
                fontSize: '0.75rem'
              }}
            />
          </Stack>

          <Stack direction="row" spacing={0.5}>
            {variant === 'split' && (
              <Tooltip title={showPreviewPanel ? 'إخفاء المعاينة' : 'عرض المعاينة'}>
                <IconButton size="small" onClick={handleTogglePreview} sx={{ color: 'inherit' }}>
                  {showPreviewPanel ? <HideIcon fontSize="small" /> : <PreviewIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
            )}

            {onRefresh && (
              <Tooltip title="تحديث">
                <IconButton size="small" onClick={onRefresh} disabled={loading} sx={{ color: 'inherit' }}>
                  {loading ? <CircularProgress size={18} color="inherit" /> : <RefreshIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
            )}

            {collapsible && (
              <IconButton size="small" onClick={handleToggleExpand} sx={{ color: 'inherit' }}>
                {expanded ? <CollapseIcon /> : <ExpandIcon />}
              </IconButton>
            )}
          </Stack>
        </Box>

        {/* Status Summary Bar */}
        <Box sx={{ px: '1.0rem', py: 1, bgcolor: 'grey.50', borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {Object.entries(DOCUMENT_STATUS).map(([key, config]) => {
              const count = statusCounts[key] || 0;
              if (count === 0) return null;
              return (
                <Chip
                  key={key}
                  size="small"
                  label={`${config.label}: ${count}`}
                  color={config.color}
                  variant="outlined"
                  sx={{ height: '1.375rem', fontSize: '0.7rem' }}
                />
              );
            })}
          </Stack>
        </Box>

        {/* Content */}
        <Collapse in={expanded} sx={{ flex: 1, minHeight: 0 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Error State */}
            {error && (
              <Alert severity="error" sx={{ m: '1.0rem' }}>
                {error}
              </Alert>
            )}

            {/* Loading State */}
            {loading && documents.length === 0 && (
              <Box
                sx={{
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  p: '1.5rem'
                }}
              >
                <CircularProgress size={32} />
              </Box>
            )}

            {/* Empty State */}
            {!loading && documents.length === 0 && (
              <Box
                sx={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  p: '1.5rem',
                  color: 'text.secondary'
                }}
              >
                <AttachmentIcon sx={{ fontSize: '3.0rem', color: 'text.disabled', mb: 1 }} />
                <Typography variant="body2">{emptyMessage}</Typography>
              </Box>
            )}

            {/* Document List + Preview */}
            {documents.length > 0 && (
              <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                {/* Document List */}
                {(variant === 'split' || variant === 'list-only') && (
                  <Box
                    sx={{
                      maxHeight: showPreviewPanel && variant === 'split' ? 200 : 'auto',
                      overflow: 'auto',
                      px: 1,
                      py: 1
                    }}
                  >
                    <List disablePadding>
                      {documents.map((doc) => (
                        <DocumentListItem
                          key={doc.id || `${doc.documentType}-${doc.referenceId}`}
                          document={doc}
                          isSelected={selectedDocument?.id === doc.id}
                          onSelect={handleDocumentSelect}
                        />
                      ))}
                    </List>
                  </Box>
                )}

                {/* Preview Panel */}
                {showPreviewPanel && (variant === 'split' || variant === 'preview-only') && (
                  <>
                    <Divider />
                    <Box sx={{ flex: 1, p: 1, minHeight: previewHeight }}>
                      <DocumentPreview
                        documentUrl={previewUrl}
                        mimeType={selectedDocument?.fileType || selectedDocument?.mimeType}
                        fileName={selectedDocument?.fileName}
                        fileSize={selectedDocument?.fileSize}
                        loading={previewLoading}
                        error={previewError}
                        onDownload={handleDownload}
                        height={previewHeight}
                        showToolbar={true}
                      />
                    </Box>
                  </>
                )}
              </Box>
            )}
          </Box>
        </Collapse>
      </Paper>
    );
  }
);

DocumentSidePanel.propTypes = {
  /** Array of document objects */
  documents: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      documentType: PropTypes.string,
      documentTypeLabel: PropTypes.string,
      status: PropTypes.oneOf(['REQUIRED', 'UPLOADED', 'APPROVED', 'REJECTED']),
      fileName: PropTypes.string,
      fileSize: PropTypes.number,
      fileType: PropTypes.string,
      mimeType: PropTypes.string,
      rejectionReason: PropTypes.string,
      referenceId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
    })
  ),
  /** Loading state */
  loading: PropTypes.bool,
  /** Error message */
  error: PropTypes.string,
  /** Callback to refresh documents */
  onRefresh: PropTypes.func,
  /** Callback when document is selected */
  onDocumentSelect: PropTypes.func,
  /** Currently selected document ID */
  selectedDocumentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  /** Panel title */
  title: PropTypes.string,
  /** Show inline preview */
  showPreview: PropTypes.bool,
  /** Preview panel height */
  previewHeight: PropTypes.number,
  /** Allow collapse */
  collapsible: PropTypes.bool,
  /** Default expanded state */
  defaultExpanded: PropTypes.bool,
  /** Empty state message */
  emptyMessage: PropTypes.string,
  /** Function to build download URL: (document) => Promise<string> | string */
  downloadUrlBuilder: PropTypes.func,
  /** Panel variant */
  variant: PropTypes.oneOf(['split', 'list-only', 'preview-only'])
};

DocumentSidePanel.displayName = 'DocumentSidePanel';

export default DocumentSidePanel;


