import { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  CircularProgress,
  Alert,
  Dialog,
  DialogContent,
  DialogTitle,
  Chip
} from '@mui/material';
import {
  PictureAsPdf as PdfIcon,
  Image as ImageIcon,
  InsertDriveFile as FileIcon,
  Download as DownloadIcon,
  Delete as DeleteIcon,
  Close as CloseIcon
} from '@mui/icons-material';
import { format } from 'date-fns';
import { ar } from 'date-fns/locale';

/**
 * AttachmentList Component
 *
 * Displays list of uploaded attachments with preview/download/delete functionality
 */
const AttachmentList = ({
  attachments = [],
  loading = false,
  error = null,
  onPreview, // NEW: External preview handler
  onDownload,
  onDelete,
  canDelete = false,
  emptyMessage = 'لا توجد مرفقات'
}) => {
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [previewFile, setPreviewFile] = useState(null);

  const getFileIcon = (fileType) => {
    if (!fileType) return <FileIcon />;

    if (fileType.includes('pdf')) {
      return <PdfIcon sx={{ color: '#f44336' }} />;
    }
    if (fileType.startsWith('image/')) {
      return <ImageIcon sx={{ color: 'info.main' }} />;
    }
    return <FileIcon sx={{ color: 'text.secondary' }} />;
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    try {
      return format(new Date(dateString), 'dd/MM/yyyy HH:mm', { locale: ar });
    } catch (err) {
      return dateString;
    }
  };

  const handleDownload = async (attachment) => {
    if (!onDownload) return;

    try {
      const blob = await onDownload(attachment.id);

      // Create download link
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.originalFileName || attachment.fileName || 'file';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Download error:', err);
    }
  };

  const handlePreview = async (attachment) => {
    if (!attachment.fileType?.startsWith('image/')) return;
    if (!onDownload) return;

    try {
      const blob = await onDownload(attachment.id);
      const url = window.URL.createObjectURL(blob);
      setPreviewUrl(url);
      setPreviewFile(attachment);
      setPreviewOpen(true);
    } catch (err) {
      console.error('Preview error:', err);
    }
  };

  const handleClosePreview = () => {
    if (previewUrl) {
      window.URL.revokeObjectURL(previewUrl);
    }
    setPreviewOpen(false);
    setPreviewUrl(null);
    setPreviewFile(null);
  };

  const handleDelete = async (attachmentId) => {
    if (!onDelete) return;

    const confirmed = window.confirm('هل أنت متأكد من حذف هذا المرفق؟');
    if (!confirmed) return;

    try {
      await onDelete(attachmentId);
    } catch (err) {
      console.error('Delete error:', err);
    }
  };

  const getTypeLabel = (attachment) => {
    if (!attachment.attachmentType) return '';

    // Map attachment types to Arabic labels
    const typeLabels = {
      INVOICE: 'فاتورة',
      MEDICAL_REPORT: 'تقرير طبي',
      PRESCRIPTION: 'وصفة طبية',
      LAB_RESULT: 'نتيجة مختبر',
      XRAY: 'أشعة',
      MRI: 'رنين مغناطيسي',
      CT_SCAN: 'أشعة مقطعية',
      ULTRASOUND: 'موجات فوق صوتية',
      ECG: 'تخطيط قلب',
      REQUEST_FORM: 'نموذج طلب',
      DOCTOR_RECOMMENDATION: 'توصية طبيب',
      OTHER: 'أخرى'
    };

    return typeLabels[attachment.attachmentType] || attachment.attachmentType;
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" p={3}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ m: '1.0rem' }}>
        {error}
      </Alert>
    );
  }

  if (!attachments || attachments.length === 0) {
    return (
      <Box sx={{ p: '1.5rem', textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          {emptyMessage}
        </Typography>
      </Box>
    );
  }

  return (
    <>
      <List>
        {attachments.map((attachment) => (
          <ListItem
            key={attachment.id}
            sx={{
              borderBottom: '1px solid',
              borderColor: 'divider',
              '&:last-child': { borderBottom: 0 }
            }}
          >
            <ListItemIcon>{getFileIcon(attachment.fileType)}</ListItemIcon>

            <ListItemText
              primary={
                <Box display="flex" alignItems="center" gap={1}>
                  <Typography
                    variant="body2"
                    sx={{
                      cursor: attachment.fileType?.startsWith('image/') ? 'pointer' : 'default',
                      '&:hover': {
                        textDecoration: attachment.fileType?.startsWith('image/') ? 'underline' : 'none'
                      }
                    }}
                    onClick={() => handlePreview(attachment)}
                  >
                    {attachment.originalFileName || attachment.fileName}
                  </Typography>
                  {attachment.attachmentType && <Chip label={getTypeLabel(attachment)} size="small" variant="outlined" />}
                </Box>
              }
              secondary={
                <Box>
                  <Typography variant="caption" display="block">
                    {formatFileSize(attachment.fileSize)} • {formatDate(attachment.createdAt)}
                  </Typography>
                  {attachment.uploadedBy && (
                    <Typography variant="caption" color="text.secondary">
                      رفع بواسطة: {attachment.uploadedBy}
                    </Typography>
                  )}
                  {attachment.description && (
                    <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
                      {attachment.description}
                    </Typography>
                  )}
                </Box>
              }
            />

            <ListItemSecondaryAction>
              {/* Preview button - uses external preview if available */}
              {onPreview && (
                <IconButton edge="end" onClick={() => onPreview(attachment)} title="معاينة" sx={{ mr: 1 }}>
                  <ImageIcon />
                </IconButton>
              )}
              <IconButton edge="end" onClick={() => handleDownload(attachment)} title="تحميل">
                <DownloadIcon />
              </IconButton>
              {canDelete && onDelete && (
                <IconButton edge="end" onClick={() => handleDelete(attachment.id)} title="حذف" sx={{ ml: 1 }}>
                  <DeleteIcon />
                </IconButton>
              )}
            </ListItemSecondaryAction>
          </ListItem>
        ))}
      </List>

      {/* Image Preview Dialog */}
      <Dialog open={previewOpen} onClose={handleClosePreview} maxWidth="md" fullWidth>
        <DialogTitle>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">{previewFile?.originalFileName || previewFile?.fileName}</Typography>
            <IconButton onClick={handleClosePreview} size="small">
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {previewUrl && (
            <Box
              component="img"
              src={previewUrl}
              alt="Preview"
              sx={{
                width: '100%',
                height: 'auto',
                maxHeight: '70vh',
                objectFit: 'contain'
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
};

export default AttachmentList;
