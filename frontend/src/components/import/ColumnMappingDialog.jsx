import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Select,
  MenuItem,
  FormControl,
  Paper,
  Typography,
  Alert,
  Box,
  Chip,
  Tooltip,
  IconButton
} from '@mui/material';
import { CheckCircle as CheckIcon, Warning as WarningIcon, Info as InfoIcon, Close as CloseIcon } from '@mui/icons-material';

/**
 * ColumnMappingDialog - Dialog for customizing Excel column mappings
 *
 * Allows users to review and adjust auto-detected column mappings before import.
 * Highlights mandatory fields and provides confidence scores.
 *
 * @param {boolean} open - Dialog visibility
 * @param {function} onClose - Close handler
 * @param {function} onConfirm - Confirm handler (receives final mappings)
 * @param {object} detectionData - ExcelColumnDetectionDto from backend
 */
const ColumnMappingDialog = ({ open, onClose, onConfirm, detectionData }) => {
  const [mappings, setMappings] = useState({});
  const [errors, setErrors] = useState([]);

  // System field definitions with Arabic labels
  const SYSTEM_FIELDS = {
    fullName: { label: 'الاسم الكامل', mandatory: true },
    employer: { label: 'جهة العمل', mandatory: true },
    cardNumber: { label: 'رقم البطاقة', mandatory: false },
    phone: { label: 'الهاتف', mandatory: false },
    email: { label: 'البريد الإلكتروني', mandatory: false },
    employeeNumber: { label: 'الرقم الوظيفي', mandatory: false },
    nationality: { label: 'الجنسية', mandatory: false },
    gender: { label: 'الجنس', mandatory: false },
    birthDate: { label: 'تاريخ الميلاد', mandatory: false },
    address: { label: 'العنوان', mandatory: false },
    grade: { label: 'الدرجة/المستوى', mandatory: false },
    manager: { label: 'المدير', mandatory: false },
    costCenter: { label: 'مركز التكلفة', mandatory: false },
    startDate: { label: 'تاريخ البدء', mandatory: false },
    endDate: { label: 'تاريخ الانتهاء', mandatory: false },
    benefitClass: { label: 'فئة المزايا', mandatory: false },
    notes: { label: 'ملاحظات', mandatory: false }
  };

  // Initialize mappings from detection data
  useEffect(() => {
    if (detectionData?.suggestedMappings) {
      // Convert backend format (excelCol → systemField) to our format (systemField → excelCol)
      const initialMappings = {};
      Object.entries(detectionData.suggestedMappings).forEach(([excelCol, systemField]) => {
        initialMappings[systemField] = excelCol;
      });
      setMappings(initialMappings);
    }
  }, [detectionData]);

  // Validate mappings
  useEffect(() => {
    const newErrors = [];

    // Check mandatory fields
    Object.entries(SYSTEM_FIELDS).forEach(([field, config]) => {
      if (config.mandatory && !mappings[field]) {
        newErrors.push(`الحقل "${config.label}" إلزامي ويجب تعيينه`);
      }
    });

    // Check for duplicate mappings
    const usedColumns = Object.values(mappings).filter((col) => col);
    const duplicates = usedColumns.filter((col, index) => usedColumns.indexOf(col) !== index);
    if (duplicates.length > 0) {
      newErrors.push(`توجد أعمدة مكررة: ${duplicates.join(', ')}`);
    }

    setErrors(newErrors);
  }, [mappings]);

  const handleMappingChange = (systemField, excelColumn) => {
    setMappings((prev) => ({
      ...prev,
      [systemField]: excelColumn || null
    }));
  };

  const handleConfirm = () => {
    if (errors.length > 0) {
      return; // Don't allow confirmation with errors
    }

    // Convert back to backend format (excelCol → systemField)
    const finalMappings = {};
    Object.entries(mappings).forEach(([systemField, excelCol]) => {
      if (excelCol) {
        finalMappings[excelCol] = systemField;
      }
    });

    onConfirm(finalMappings);
  };

  const getConfidenceScore = (systemField) => {
    return detectionData?.confidenceScores?.[systemField] || 0;
  };

  const getConfidenceColor = (score) => {
    if (score >= 0.9) return 'success';
    if (score >= 0.7) return 'warning';
    return 'error';
  };

  const getConfidenceIcon = (score) => {
    if (score >= 0.9) return <CheckIcon fontSize="small" />;
    if (score >= 0.7) return <WarningIcon fontSize="small" />;
    return <InfoIcon fontSize="small" />;
  };

  const getPreviewValues = (excelColumn) => {
    if (!excelColumn || !detectionData?.previewData) return [];

    return detectionData.previewData
      .slice(0, 3)
      .map((row) => row[excelColumn])
      .filter((val) => val);
  };

  if (!detectionData) return null;

  const availableColumns = detectionData.detectedColumns || [];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth dir="rtl">
      <DialogTitle>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">مطابقة أعمدة Excel</Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {/* Warnings */}
        {detectionData.warnings && detectionData.warnings.length > 0 && (
          <Alert severity="warning" sx={{ mb: '1.0rem' }}>
            {detectionData.warnings.map((warning, idx) => (
              <Typography key={idx} variant="body2">
                • {warning}
              </Typography>
            ))}
          </Alert>
        )}

        {/* Validation Errors */}
        {errors.length > 0 && (
          <Alert severity="error" sx={{ mb: '1.0rem' }}>
            {errors.map((error, idx) => (
              <Typography key={idx} variant="body2">
                • {error}
              </Typography>
            ))}
          </Alert>
        )}

        {/* Info */}
        <Alert severity="info" sx={{ mb: '1.0rem' }}>
          <Typography variant="body2">
            تم اكتشاف {detectionData.totalColumns} عمود.
            {detectionData.autoAcceptedCount > 0 && ` ${detectionData.autoAcceptedCount} عمود تم تعيينه تلقائياً.`}
            {detectionData.manualReviewCount > 0 && ` ${detectionData.manualReviewCount} عمود يحتاج مراجعة.`}
          </Typography>
        </Alert>

        {/* Mapping Table */}
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>
                  <strong>الحقل</strong>
                </TableCell>
                <TableCell>
                  <strong>عمود Excel</strong>
                </TableCell>
                <TableCell>
                  <strong>الثقة</strong>
                </TableCell>
                <TableCell>
                  <strong>معاينة البيانات</strong>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {Object.entries(SYSTEM_FIELDS).map(([field, config]) => {
                const selectedColumn = mappings[field];
                const confidence = getConfidenceScore(field);
                const previewValues = getPreviewValues(selectedColumn);

                return (
                  <TableRow
                    key={field}
                    sx={{
                      bgcolor: config.mandatory ? 'rgba(25, 118, 210, 0.05)' : 'inherit'
                    }}
                  >
                    {/* System Field */}
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="body2">{config.label}</Typography>
                        {config.mandatory && <Chip label="إلزامي" size="small" color="primary" variant="outlined" />}
                      </Box>
                    </TableCell>

                    {/* Excel Column Selector */}
                    <TableCell>
                      <FormControl fullWidth size="small">
                        <Select value={selectedColumn || ''} onChange={(e) => handleMappingChange(field, e.target.value)} displayEmpty>
                          <MenuItem value="">
                            <em>-- غير محدد --</em>
                          </MenuItem>
                          {availableColumns.map((col) => (
                            <MenuItem key={col} value={col}>
                              {col}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </TableCell>

                    {/* Confidence Score */}
                    <TableCell>
                      {selectedColumn && (
                        <Tooltip title={`درجة الثقة: ${(confidence * 100).toFixed(0)}%`}>
                          <Chip
                            icon={getConfidenceIcon(confidence)}
                            label={`${(confidence * 100).toFixed(0)}%`}
                            size="small"
                            color={getConfidenceColor(confidence)}
                            variant="outlined"
                          />
                        </Tooltip>
                      )}
                    </TableCell>

                    {/* Preview Data */}
                    <TableCell>
                      {previewValues.length > 0 && (
                        <Typography variant="caption" color="text.secondary">
                          {previewValues.slice(0, 2).join(' • ')}
                          {previewValues.length > 2 && '...'}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Stats */}
        <Box mt={2} display="flex" gap={2}>
          <Chip
            icon={<CheckIcon />}
            label={`${Object.values(mappings).filter((v) => v).length} عمود معيّن`}
            color="success"
            variant="outlined"
          />
          <Chip icon={<InfoIcon />} label={`${Object.keys(SYSTEM_FIELDS).length} حقل متاح`} color="info" variant="outlined" />
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} color="inherit">
          إلغاء
        </Button>
        <Button onClick={handleConfirm} variant="contained" color="primary" disabled={errors.length > 0}>
          تطبيق وعرض المعاينة
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ColumnMappingDialog;
