import React, { useState, useMemo } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Checkbox,
  Select,
  MenuItem,
  FormControlLabel,
  Alert,
  Chip
} from '@mui/material';
import { useSnackbar } from 'notistack';
import { confirmPriceListImport } from 'services/api/provider-contracts.service';
import { useMutation, useQueryClient } from '@tanstack/react-query';

export default function PricingImportReviewDialog({
  open,
  onClose,
  contractId,
  previewData,
  onSuccess
}) {
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();

  const [tabIndex, setTabIndex] = useState(0);
  const [modifications, setModifications] = useState({});
  const [skipZeroPrice, setSkipZeroPrice] = useState(false);

  // Separate items by confidence
  const items = previewData?.items || [];
  
  const highConfItems = useMemo(() => items.filter(i => i.confidenceLevel === 'HIGH' && !i.isPriceZero), [items]);
  const manualItems = useMemo(() => items.filter(i => i.requiresReview || i.confidenceLevel !== 'HIGH'), [items]);
  const zeroPriceItems = useMemo(() => items.filter(i => i.isPriceZero), [items]);

  const confirmMutation = useMutation({
    mutationFn: (data) => confirmPriceListImport(contractId, data),
    onSuccess: (response) => {
      enqueueSnackbar(response?.data?.message || 'تم الاعتماد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries(['provider-contract-pricing', contractId]);
      onSuccess();
      onClose();
    },
    onError: (error) => {
      enqueueSnackbar(error?.response?.data?.message || 'حدث خطأ أثناء الاعتماد', { variant: 'error' });
    }
  });

  const handleConfirm = () => {
    const payload = {
      importSessionId: previewData.importSessionId,
      skipZeroPriceItems: skipZeroPrice,
      modifications: Object.values(modifications)
    };
    confirmMutation.mutate(payload);
  };

  const handleModChange = (rowId, field, value) => {
    setModifications(prev => ({
      ...prev,
      [rowId]: {
        ...prev[rowId],
        rowId,
        [field]: value
      }
    }));
  };

  if (!previewData) return null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        <Typography variant="h4">مراجعة استيراد قائمة الأسعار</Typography>
        <Box sx={{ mt: 1, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Chip label={`الكل: ${previewData.totalItems}`} color="primary" />
          <Chip label={`ثقة عالية: ${previewData.highConfidenceCount}`} color="success" />
          <Chip label={`تحتاج مراجعة: ${previewData.manualReviewCount}`} color="warning" />
          <Chip label={`سعر صفر: ${previewData.zeroPriceCount}`} color="error" />
        </Box>
      </DialogTitle>
      
      <DialogContent dividers sx={{ p: 0, display: 'flex', flexDirection: 'column', height: '60vh' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
          <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)}>
            <Tab label={`جاهز للترحيل (${highConfItems.length})`} />
            <Tab label={`يحتاج مراجعة (${manualItems.length})`} />
            <Tab label={`سعر صفر (${zeroPriceItems.length})`} />
          </Tabs>
        </Box>
        
        <Box sx={{ p: 2, overflowY: 'auto', flex: 1 }}>
          {tabIndex === 0 && (
            <>
              <Alert severity="success" sx={{ mb: 2 }}>هذه الخدمات مطابقة بنسبة عالية للقواعد وسيتم ترحيلها تلقائياً بالبيانات المعروضة.</Alert>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>الخدمة في الملف</TableCell>
                      <TableCell>التصنيف التأميني</TableCell>
                      <TableCell>التصنيف/التخصص الأصلي</TableCell>
                      <TableCell>النوع</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {highConfItems.map((row) => (
                      <TableRow key={row.rowId}>
                        <TableCell>{row.serviceName}</TableCell>
                        <TableCell>
                          <Chip size="small" label={row.proposedCategoryCode} color="success" />
                        </TableCell>
                        <TableCell>{row.importedSubCategory || row.importedMainCategory || '-'}</TableCell>
                        <TableCell>{row.encounterType}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}
          
          {tabIndex === 1 && (
            <>
              <Alert severity="warning" sx={{ mb: 2 }}>يرجى مراجعة وتأكيد أو تعديل التصنيفات التالية.</Alert>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>الخدمة في الملف</TableCell>
                      <TableCell>سبب المراجعة</TableCell>
                      <TableCell>التصنيف المقترح (تعديل)</TableCell>
                      <TableCell>قاعدة جديدة؟</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {manualItems.map((row) => {
                      const mod = modifications[row.rowId] || {};
                      return (
                        <TableRow key={row.rowId}>
                          <TableCell>
                            <Typography variant="body2">{row.serviceName}</Typography>
                            <Typography variant="caption" color="textSecondary">{row.importedSubCategory || row.importedMainCategory}</Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="error">{row.reviewReason || 'مستوى ثقة منخفض'}</Typography>
                          </TableCell>
                          <TableCell>
                            {/* In a real scenario, this would be an autocomplete for MedicalCategory. 
                                For simplicity, we show the proposed code, or allow overriding if we loaded categories. */}
                            <Chip size="small" label={row.proposedCategoryCode || 'غير مصنف'} color={row.proposedCategoryCode ? "primary" : "default"} />
                            {/* We can add a button here to select a different category later */}
                          </TableCell>
                          <TableCell>
                            <FormControlLabel
                              control={<Checkbox size="small" checked={!!mod.saveAsRule} onChange={(e) => handleModChange(row.rowId, 'saveAsRule', e.target.checked)} />}
                              label={<Typography variant="caption">حفظ كقاعدة</Typography>}
                            />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}

          {tabIndex === 2 && (
            <>
              <Alert severity="error" sx={{ mb: 2 }}>يوجد {zeroPriceItems.length} خدمة بسعر صفر. هل تريد تخطيها وعدم استيرادها؟</Alert>
              <FormControlLabel
                control={<Checkbox checked={skipZeroPrice} onChange={(e) => setSkipZeroPrice(e.target.checked)} />}
                label="تخطي جميع الخدمات التي سعرها صفر"
              />
              <TableContainer component={Paper} variant="outlined" sx={{ mt: 2 }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>الخدمة في الملف</TableCell>
                      <TableCell>التصنيف/التخصص</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {zeroPriceItems.map((row) => (
                      <TableRow key={row.rowId}>
                        <TableCell>{row.serviceName}</TableCell>
                        <TableCell>{row.importedSubCategory || row.importedMainCategory || '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}
        </Box>
      </DialogContent>
      
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={confirmMutation.isLoading}>إلغاء</Button>
        <Button 
          variant="contained" 
          color="primary" 
          onClick={handleConfirm}
          disabled={confirmMutation.isLoading}
        >
          اعتماد الترحيل
        </Button>
      </DialogActions>
    </Dialog>
  );
}
