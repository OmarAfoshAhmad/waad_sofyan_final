/**
 * ExcelUploadButton - Reusable Excel File Upload Component
 * Phase: Frontend UI/UX Improvements
 *
 * Features:
 * - Drag & Drop support
 * - File type validation (.xlsx, .xls)
 * - File size display
 * - Upload preview dialog
 * - Loading states
 * - Arabic localization
 *
 * ⚠️ This is UI ONLY - actual processing should happen via Backend API
 *
 * Usage:
 * <ExcelUploadButton
 *   onUpload={(file) => console.log('Upload file:', file)}
 *   disabled={false}
 *   buttonText="رفع ملف Excel"
 * />
 */

import { useState, useRef, useCallback } from 'react';

// MUI Components
import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Stack,
  Typography,
  LinearProgress,
  Alert,
  Chip,
  Tooltip,
  Paper
} from '@mui/material';

// MUI Icons
import UploadFileIcon from '@mui/icons-material/UploadFile';
import CloseIcon from '@mui/icons-material/Close';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';

// ============================================================================
// CONSTANTS
// ============================================================================

const ALLOWED_EXTENSIONS = ['.xlsx', '.xls'];
const ALLOWED_MIME_TYPES = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', // .xlsx
  'application/vnd.ms-excel' // .xls
];
const MAX_FILE_SIZE_MB = 10;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format file size to human-readable format
 */
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
};

/**
 * Validate file type and size
 */
const validateFile = (file) => {
  const errors = [];

  // Check file type
  const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
  const isValidType = ALLOWED_EXTENSIONS.includes(fileExtension) || ALLOWED_MIME_TYPES.includes(file.type);

  if (!isValidType) {
    errors.push(`نوع الملف غير مدعوم. الأنواع المسموحة: ${ALLOWED_EXTENSIONS.join(', ')}`);
  }

  // Check file size
  if (file.size > MAX_FILE_SIZE_BYTES) {
    errors.push(`حجم الملف كبير جداً. الحد الأقصى: ${MAX_FILE_SIZE_MB} ميجابايت`);
  }

  if (file.size === 0) {
    errors.push('الملف فارغ');
  }

  return { isValid: errors.length === 0, errors };
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const ExcelUploadButton = ({
  onUpload,
  disabled = false,
  buttonText = 'رفع ملف Excel',
  uploadingText = 'جاري الرفع...',
  successMessage = 'تم رفع الملف بنجاح',
  size = 'medium',
  variant = 'outlined',
  color = 'primary',
  fullWidth = false
}) => {
  // ========================================
  // STATE
  // ========================================

  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [validationErrors, setValidationErrors] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState(false);
  const [dragActive, setDragActive] = useState(false);

  const fileInputRef = useRef(null);

  // ========================================
  // HANDLERS
  // ========================================

  /**
   * Open file picker
   */
  const handleButtonClick = useCallback(() => {
    if (disabled || uploading) return;
    fileInputRef.current?.click();
  }, [disabled, uploading]);

  /**
   * Handle file selection
   */
  const handleFileChange = useCallback((event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    processFile(file);

    // Reset input value to allow re-uploading same file
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, []);

  /**
   * Process and validate selected file
   */
  const processFile = useCallback((file) => {
    const validation = validateFile(file);

    setSelectedFile(file);
    setValidationErrors(validation.errors);
    setUploadSuccess(false);
    setDialogOpen(true);
  }, []);

  /**
   * Drag & Drop handlers
   */
  const handleDrag = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();

    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  }, []);

  const handleDrop = useCallback(
    (e) => {
      e.preventDefault();
      e.stopPropagation();
      setDragActive(false);

      if (disabled || uploading) return;

      const file = e.dataTransfer.files?.[0];
      if (file) {
        processFile(file);
      }
    },
    [disabled, uploading, processFile]
  );

  /**
   * Confirm upload
   */
  const handleUpload = useCallback(async () => {
    if (!selectedFile || validationErrors.length > 0 || !onUpload) return;

    setUploading(true);

    try {
      await onUpload(selectedFile);
      setUploadSuccess(true);

      // Close dialog after 1.5 seconds
      setTimeout(() => {
        handleCloseDialog();
      }, 1500);
    } catch (error) {
      console.error('[ExcelUploadButton] Upload failed:', error);
      setValidationErrors([error?.message || 'فشل رفع الملف. يرجى المحاولة لاحقاً']);
    } finally {
      setUploading(false);
    }
  }, [selectedFile, validationErrors, onUpload]);

  /**
   * Close dialog and reset state
   */
  const handleCloseDialog = useCallback(() => {
    if (uploading) return; // Prevent closing during upload

    setDialogOpen(false);
    setSelectedFile(null);
    setValidationErrors([]);
    setUploadSuccess(false);
  }, [uploading]);

  // ========================================
  // RENDER HELPERS
  // ========================================

  const renderFileInfo = () => {
    if (!selectedFile) return null;

    return (
      <Paper
        variant="outlined"
        sx={{
          p: '1.0rem',
          borderRadius: '0.125rem',
          borderStyle: 'dashed',
          borderColor: validationErrors.length > 0 ? 'error.main' : 'primary.main',
          bgcolor: validationErrors.length > 0 ? 'error.lighter' : 'primary.lighter'
        }}
      >
        <Stack spacing={2}>
          {/* File Icon & Name */}
          <Stack direction="row" spacing={2} alignItems="center">
            <DescriptionIcon sx={{ fontSize: '2.5rem', color: validationErrors.length > 0 ? 'error.main' : 'primary.main' }} />
            <Box flex={1}>
              <Typography variant="subtitle1" fontWeight="medium" noWrap>
                {selectedFile.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {formatFileSize(selectedFile.size)}
              </Typography>
            </Box>
            {validationErrors.length === 0 && <CheckCircleIcon sx={{ color: 'success.main', fontSize: '1.75rem' }} />}
          </Stack>

          {/* File Type */}
          <Box>
            <Chip
              label={selectedFile.type || 'Unknown'}
              size="small"
              variant="outlined"
              color={validationErrors.length > 0 ? 'error' : 'default'}
            />
          </Box>
        </Stack>
      </Paper>
    );
  };

  const renderDragDropArea = () => (
    <Box
      onDragEnter={handleDrag}
      onDragLeave={handleDrag}
      onDragOver={handleDrag}
      onDrop={handleDrop}
      sx={{
        position: 'relative',
        border: '2px dashed',
        borderColor: dragActive ? 'primary.main' : 'divider',
        borderRadius: '0.125rem',
        p: '2.0rem',
        textAlign: 'center',
        bgcolor: dragActive ? 'primary.lighter' : 'background.paper',
        cursor: 'pointer',
        transition: 'all 0.3s',
        '&:hover': {
          borderColor: 'primary.main',
          bgcolor: 'primary.lighter'
        }
      }}
      onClick={handleButtonClick}
    >
      <CloudUploadIcon sx={{ fontSize: '3.75rem', color: 'text.secondary', mb: '1.0rem' }} />
      <Typography variant="h6" gutterBottom>
        اسحب الملف هنا أو انقر للاختيار
      </Typography>
      <Typography variant="body2" color="text.secondary">
        الأنواع المسموحة: {ALLOWED_EXTENSIONS.join(', ')}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        الحد الأقصى: {MAX_FILE_SIZE_MB} ميجابايت
      </Typography>
    </Box>
  );

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <>
      {/* Upload Button */}
      <Tooltip title={disabled ? 'غير متاح حالياً' : buttonText}>
        <span>
          <Button
            variant={variant}
            color={color}
            size={size}
            startIcon={<UploadFileIcon />}
            onClick={handleButtonClick}
            disabled={disabled || uploading}
            fullWidth={fullWidth}
          >
            {buttonText}
          </Button>
        </span>
      </Tooltip>

      {/* Hidden File Input */}
      <input ref={fileInputRef} type="file" accept={ALLOWED_EXTENSIONS.join(',')} onChange={handleFileChange} style={{ display: 'none' }} />

      {/* Upload Preview Dialog */}
      <Dialog
        open={dialogOpen}
        onClose={handleCloseDialog}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: '0.125rem',
            minHeight: '18.75rem'
          }
        }}
      >
        {/* Dialog Title */}
        <DialogTitle>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Typography variant="h5" fontWeight="bold">
              {uploadSuccess ? 'تم الرفع بنجاح' : 'معاينة الملف'}
            </Typography>
            <IconButton onClick={handleCloseDialog} disabled={uploading}>
              <CloseIcon />
            </IconButton>
          </Stack>
        </DialogTitle>

        {/* Dialog Content */}
        <DialogContent>
          <Stack spacing={3}>
            {/* Upload Progress */}
            {uploading && (
              <Box>
                <LinearProgress />
                <Typography variant="body2" color="text.secondary" align="center" sx={{ mt: 1 }}>
                  {uploadingText}
                </Typography>
              </Box>
            )}

            {/* Success Message */}
            {uploadSuccess && (
              <Alert severity="success" icon={<CheckCircleIcon />}>
                {successMessage}
              </Alert>
            )}

            {/* Validation Errors */}
            {validationErrors.length > 0 && (
              <Alert severity="error">
                <Typography variant="subtitle2" fontWeight="medium" gutterBottom>
                  أخطاء في الملف:
                </Typography>
                <ul style={{ margin: 0, paddingInlineStart: 20 }}>
                  {validationErrors.map((error, index) => (
                    <li key={index}>
                      <Typography variant="body2">{error}</Typography>
                    </li>
                  ))}
                </ul>
              </Alert>
            )}

            {/* File Info or Drag-Drop Area */}
            {!uploading && !uploadSuccess && <>{selectedFile ? renderFileInfo() : renderDragDropArea()}</>}
          </Stack>
        </DialogContent>

        {/* Dialog Actions */}
        {!uploadSuccess && (
          <DialogActions sx={{ px: '1.5rem', pb: '1.0rem' }}>
            <Button onClick={handleCloseDialog} disabled={uploading}>
              إلغاء
            </Button>
            <Button
              variant="contained"
              onClick={handleUpload}
              disabled={!selectedFile || validationErrors.length > 0 || uploading}
              startIcon={uploading ? null : <UploadFileIcon />}
            >
              {uploading ? uploadingText : 'رفع الملف'}
            </Button>
          </DialogActions>
        )}
      </Dialog>
    </>
  );
};

export default ExcelUploadButton;
