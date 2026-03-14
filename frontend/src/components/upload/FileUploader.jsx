import { useState, useCallback } from 'react';
import {
  Box,
  Button,
  LinearProgress,
  Typography,
  Alert,
  IconButton,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  TextField
} from '@mui/material';
import { CloudUpload as UploadIcon, Close as CloseIcon, InsertDriveFile as FileIcon } from '@mui/icons-material';
import { useFileUpload } from '../../hooks/useFileUpload';

/**
 * FileUploader Component
 *
 * Handles file uploads with progress tracking and validation
 */
const FileUploader = ({
  uploadFn,
  attachmentTypes = [],
  onUploadSuccess,
  onUploadError,
  maxSize = 10 * 1024 * 1024, // 10MB default
  accept = '*/*',
  showTypeSelector = true,
  showDescription = false,
  label = 'رفع ملف'
}) => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [attachmentType, setAttachmentType] = useState('');
  const [description, setDescription] = useState('');
  const [preview, setPreview] = useState(null);
  const [validationError, setValidationError] = useState(null);

  const { upload, uploading, progress, error, reset } = useFileUpload({
    uploadFn,
    onSuccess: (result) => {
      if (onUploadSuccess) {
        onUploadSuccess(result);
      }
      handleReset();
    },
    onError: (err) => {
      if (onUploadError) {
        onUploadError(err);
      }
    }
  });

  const validateFile = (file) => {
    // Size validation
    if (file.size > maxSize) {
      return `حجم الملف يتجاوز الحد المسموح (${(maxSize / (1024 * 1024)).toFixed(0)} MB)`;
    }

    return null;
  };

  const handleFileSelect = (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const validation = validateFile(file);
    if (validation) {
      setValidationError(validation);
      return;
    }

    setValidationError(null);
    setSelectedFile(file);

    // Generate preview for images
    if (file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreview(reader.result);
      };
      reader.readAsDataURL(file);
    } else {
      setPreview(null);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setValidationError('الرجاء اختيار ملف');
      return;
    }

    if (showTypeSelector && !attachmentType) {
      setValidationError('الرجاء اختيار نوع المرفق');
      return;
    }

    try {
      if (showTypeSelector) {
        await upload(selectedFile, attachmentType, description);
      } else {
        await upload(selectedFile);
      }
    } catch (err) {
      // Error handled by hook
    }
  };

  const handleReset = () => {
    setSelectedFile(null);
    setAttachmentType('');
    setDescription('');
    setPreview(null);
    setValidationError(null);
    reset();
  };

  const formatFileSize = (bytes) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  return (
    <Box>
      {/* File Input */}
      <input
        accept={accept}
        style={{ display: 'none' }}
        id="file-upload-input"
        type="file"
        onChange={handleFileSelect}
        disabled={uploading}
      />

      <label htmlFor="file-upload-input">
        <Button variant="outlined" component="span" startIcon={<UploadIcon />} disabled={uploading} fullWidth>
          {label}
        </Button>
      </label>

      {/* Selected File Display */}
      {selectedFile && (
        <Box sx={{ mt: '1.0rem' }}>
          <Box
            sx={{
              p: '1.0rem',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              display: 'flex',
              alignItems: 'center',
              gap: '1.0rem'
            }}
          >
            {preview ? (
              <Box
                component="img"
                src={preview}
                alt="Preview"
                sx={{
                  width: '3.75rem',
                  height: '3.75rem',
                  objectFit: 'cover',
                  borderRadius: 1
                }}
              />
            ) : (
              <FileIcon sx={{ fontSize: '2.5rem', color: 'text.secondary' }} />
            )}

            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="body2" noWrap>
                {selectedFile.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatFileSize(selectedFile.size)}
              </Typography>
            </Box>

            {!uploading && (
              <IconButton size="small" onClick={handleReset}>
                <CloseIcon />
              </IconButton>
            )}
          </Box>

          {/* Type Selector */}
          {showTypeSelector && attachmentTypes.length > 0 && (
            <FormControl fullWidth sx={{ mt: '1.0rem' }}>
              <InputLabel>نوع المرفق *</InputLabel>
              <Select value={attachmentType} label="نوع المرفق *" onChange={(e) => setAttachmentType(e.target.value)} disabled={uploading}>
                {attachmentTypes.map((type) => (
                  <MenuItem key={type.value} value={type.value}>
                    {type.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          {/* Description Field */}
          {showDescription && (
            <TextField
              fullWidth
              label="وصف (اختياري)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              rows={2}
              disabled={uploading}
              sx={{ mt: '1.0rem' }}
            />
          )}

          {/* Progress Bar */}
          {uploading && (
            <Box sx={{ mt: '1.0rem' }}>
              <LinearProgress variant="determinate" value={progress} />
              <Typography variant="caption" color="text.secondary" align="center" display="block">
                {progress}%
              </Typography>
            </Box>
          )}

          {/* Upload Button */}
          {!uploading && (
            <Button fullWidth variant="contained" onClick={handleUpload} sx={{ mt: '1.0rem' }}>
              رفع الملف
            </Button>
          )}
        </Box>
      )}

      {/* Validation Error */}
      {validationError && (
        <Alert severity="error" sx={{ mt: '1.0rem' }}>
          {validationError}
        </Alert>
      )}

      {/* Upload Error */}
      {error && (
        <Alert severity="error" sx={{ mt: '1.0rem' }}>
          {error}
        </Alert>
      )}
    </Box>
  );
};

export default FileUploader;
