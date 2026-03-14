/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 📂 DOCUMENTS VIEWER - Right Panel (40%)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * ✓ Documents list (type, status, date)
 * ✓ Live inline preview
 * ✓ Click to preview
 * ✓ Auto-select first document
 * ✓ Keyboard navigation (←→)
 *
 * Layout:
 * - Top: Documents List (compact table)
 * - Bottom: Live Preview Panel
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

import { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Typography,
  Stack,
  IconButton,
  Tooltip
} from '@mui/icons-material';
import {
  Description as FileIcon,
  Image as ImageIcon,
  PictureAsPdf as PdfIcon,
  Science as LabIcon,
  Medication as RxIcon,
  MedicalServices as ImagingIcon,
  Receipt as InvoiceIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import DocumentPreview from './DocumentPreview';
import { MEDICAL_THEME } from '../../theme/medical-theme';
import { format } from 'date-fns';
import { ar } from 'date-fns/locale';

const DocumentsViewer = ({ documents = [], entityId, entityType, onRefresh }) => {
  const [selectedDocument, setSelectedDocument] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);

  // Auto-select first document
  useEffect(() => {
    if (documents && documents.length > 0) {
      setSelectedDocument(documents[0]);
      setCurrentIndex(0);
    } else {
      setSelectedDocument(null);
      setCurrentIndex(0);
    }
  }, [documents]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyPress = (e) => {
      if (!documents || documents.length === 0) return;

      if (e.key === 'ArrowLeft' && !e.target.closest('iframe')) {
        handlePrevDocument();
      } else if (e.key === 'ArrowRight' && !e.target.closest('iframe')) {
        handleNextDocument();
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [currentIndex, documents]);

  const handlePrevDocument = () => {
    if (currentIndex > 0) {
      const newIndex = currentIndex - 1;
      setCurrentIndex(newIndex);
      setSelectedDocument(documents[newIndex]);
    }
  };

  const handleNextDocument = () => {
    if (currentIndex < documents.length - 1) {
      const newIndex = currentIndex + 1;
      setCurrentIndex(newIndex);
      setSelectedDocument(documents[newIndex]);
    }
  };

  const handleDocumentClick = (doc, index) => {
    setSelectedDocument(doc);
    setCurrentIndex(index);
  };

  // Get document type icon
  const getDocumentIcon = (docType) => {
    const type = docType?.toLowerCase() || '';

    if (type.includes('lab')) return <LabIcon fontSize="small" />;
    if (type.includes('prescription') || type.includes('rx')) return <RxIcon fontSize="small" />;
    if (type.includes('imaging') || type.includes('xray') || type.includes('scan')) return <ImagingIcon fontSize="small" />;
    if (type.includes('invoice')) return <InvoiceIcon fontSize="small" />;
    if (type.includes('pdf')) return <PdfIcon fontSize="small" />;
    if (type.includes('image') || type.includes('jpg') || type.includes('png')) return <ImageIcon fontSize="small" />;

    return <FileIcon fontSize="small" />;
  };

  // Get document type color
  const getDocumentTypeColor = (docType) => {
    const type = docType?.toLowerCase() || '';

    if (type.includes('lab')) return MEDICAL_THEME.colors.documentType.lab;
    if (type.includes('prescription')) return MEDICAL_THEME.colors.documentType.prescription;
    if (type.includes('imaging')) return MEDICAL_THEME.colors.documentType.imaging;
    if (type.includes('invoice')) return MEDICAL_THEME.colors.documentType.invoice;

    return MEDICAL_THEME.colors.documentType.other;
  };

  // Get status chip
  const getStatusChip = (status) => {
    const statusConfig = {
      UPLOADED: { label: 'مرفوع', color: 'success' },
      REQUIRED: { label: 'مطلوب', color: 'warning' },
      REJECTED: { label: 'مرفوض', color: 'error' },
      PENDING: { label: 'قيد المراجعة', color: 'info' }
    };

    const config = statusConfig[status] || { label: status, color: 'default' };

    return <Chip label={config.label} size="small" color={config.color} sx={{ fontWeight: 500, fontSize: '0.75rem' }} />;
  };

  // Format date
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    try {
      return format(new Date(dateString), 'dd/MM/yyyy HH:mm', { locale: ar });
    } catch {
      return dateString;
    }
  };

  return (
    <Box
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: MEDICAL_THEME.colors.background.sidebar,
        borderLeft: `1px solid ${MEDICAL_THEME.colors.border.light}`
      }}
    >
      {/* Header */}
      <Box
        sx={{
          padding: MEDICAL_THEME.spacing.md,
          borderBottom: `1px solid ${MEDICAL_THEME.colors.border.light}`,
          background: MEDICAL_THEME.colors.background.paper
        }}
      >
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h6" sx={{ fontWeight: 600, color: MEDICAL_THEME.colors.text.primary }}>
            📂 المستندات ({documents?.length || 0})
          </Typography>

          {onRefresh && (
            <Tooltip title="تحديث المستندات">
              <IconButton size="small" onClick={onRefresh}>
                <RefreshIcon />
              </IconButton>
            </Tooltip>
          )}
        </Stack>

        {documents && documents.length > 1 && (
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
            استخدم ← → للتنقل بين المستندات
          </Typography>
        )}
      </Box>

      {/* Documents List */}
      <Box
        sx={{
          maxHeight: '18.75rem',
          overflow: 'auto',
          borderBottom: `1px solid ${MEDICAL_THEME.colors.border.light}`
        }}
      >
        {!documents || documents.length === 0 ? (
          <Box sx={{ padding: MEDICAL_THEME.spacing.lg, textAlign: 'center' }}>
            <FileIcon sx={{ fontSize: '3.0rem', color: MEDICAL_THEME.colors.neutral.medium, mb: 1 }} />
            <Typography variant="body2" color="text.secondary">
              لا توجد مستندات مرفقة
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, background: MEDICAL_THEME.colors.background.paper }}>النوع</TableCell>
                  <TableCell sx={{ fontWeight: 600, background: MEDICAL_THEME.colors.background.paper }}>الحالة</TableCell>
                  <TableCell sx={{ fontWeight: 600, background: MEDICAL_THEME.colors.background.paper }}>التاريخ</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {documents.map((doc, index) => (
                  <TableRow
                    key={doc.id || index}
                    hover
                    onClick={() => handleDocumentClick(doc, index)}
                    selected={selectedDocument?.id === doc.id}
                    sx={{
                      cursor: 'pointer',
                      '&.Mui-selected': {
                        background: MEDICAL_THEME.colors.background.selected,
                        '&:hover': {
                          background: MEDICAL_THEME.colors.background.selected
                        }
                      }
                    }}
                  >
                    <TableCell>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Box sx={{ color: getDocumentTypeColor(doc.type) }}>{getDocumentIcon(doc.type)}</Box>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {doc.type || doc.fileName}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{getStatusChip(doc.status || 'UPLOADED')}</TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {formatDate(doc.uploadedAt || doc.createdAt)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>

      {/* Preview Panel */}
      <Box
        sx={{
          flex: 1,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {/* Preview Header */}
        {selectedDocument && (
          <Box
            sx={{
              padding: MEDICAL_THEME.spacing.sm,
              background: MEDICAL_THEME.colors.background.paper,
              borderBottom: `1px solid ${MEDICAL_THEME.colors.border.light}`
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                معاينة: {selectedDocument.fileName || selectedDocument.type}
              </Typography>

              {documents.length > 1 && (
                <Typography variant="caption" color="text.secondary">
                  {currentIndex + 1} / {documents.length}
                </Typography>
              )}
            </Stack>
          </Box>
        )}

        {/* Document Preview Component */}
        <Box sx={{ flex: 1, overflow: 'hidden' }}>
          <DocumentPreview document={selectedDocument} />
        </Box>
      </Box>
    </Box>
  );
};

export default DocumentsViewer;


