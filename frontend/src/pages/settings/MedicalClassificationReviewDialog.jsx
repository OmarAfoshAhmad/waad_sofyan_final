import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Grid,
  TextField,
  Typography,
  Chip,
  Box,
  Divider,
  MenuItem,
  FormControlLabel,
  Checkbox
} from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

export default function MedicalClassificationReviewDialog({ open, onClose, item, onSave }) {
  const [formData, setFormData] = useState({});

  useEffect(() => {
    if (item) {
      setFormData({
        medicalSpecialty: item.medicalSpecialty || '',
        bodySystem: item.bodySystem || '',
        procedureType: item.procedureType || '',
        medicalMeaningAr: item.medicalMeaningAr || '',
        suggestedInsuranceCategoryCode: item.insuranceCategoryCode || '',
        requiresReview: item.requiresReview || false,
        reason: item.explanationAr || '',
        saveAsRule: false
      });
    }
  }, [item]);

  const handleChange = (e) => {
    const { name, value, checked, type } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSave = () => {
    onSave({
      ...item,
      ...formData,
    });
    onClose();
  };

  if (!item) return null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        مراجعة التصنيف الطبي الدلالي
      </DialogTitle>
      <DialogContent dividers>
        <Box mb={2}>
          <Typography variant="subtitle2" color="textSecondary">اسم الخدمة الأصلي</Typography>
          <Typography variant="h6">{item.serviceName}</Typography>
        </Box>
        <Divider sx={{ mb: 2 }} />

        {item.warnings && item.warnings.length > 0 && (
          <Box mb={2} p={2} bgcolor="#fff4e5" borderRadius={1} border="1px solid #ff9800">
            <Typography variant="subtitle2" color="#ff9800" sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
              <WarningAmberIcon sx={{ mr: 1 }} fontSize="small" /> تحذيرات المراجعة
            </Typography>
            {item.warnings.map((w, i) => (
              <Typography key={i} variant="body2" color="textSecondary">- {w}</Typography>
            ))}
          </Box>
        )}

        <Grid container spacing={2}>
          <Grid item xs={12}>
            <TextField
              fullWidth
              label="المعنى الطبي"
              name="medicalMeaningAr"
              value={formData.medicalMeaningAr || ''}
              onChange={handleChange}
              multiline
              rows={2}
            />
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField
              select
              fullWidth
              label="التخصص الطبي"
              name="medicalSpecialty"
              value={formData.medicalSpecialty || ''}
              onChange={handleChange}
            >
              <MenuItem value="GENERAL_SURGERY">جراحة عامة</MenuItem>
              <MenuItem value="DERMATOLOGY">جلدية</MenuItem>
              <MenuItem value="ORTHOPEDICS">عظام</MenuItem>
              <MenuItem value="RADIOLOGY">أشعة</MenuItem>
              <MenuItem value="DENTISTRY">أسنان</MenuItem>
              <MenuItem value="OPHTHALMOLOGY">عيون</MenuItem>
              <MenuItem value="OB_GYN">نساء وولادة</MenuItem>
              <MenuItem value="UNKNOWN">غير محدد</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField
              select
              fullWidth
              label="نظام الجسم"
              name="bodySystem"
              value={formData.bodySystem || ''}
              onChange={handleChange}
            >
              <MenuItem value="SKIN_SOFT_TISSUE">الجلد والأنسجة</MenuItem>
              <MenuItem value="MUSCULOSKELETAL">العظام والعضلات</MenuItem>
              <MenuItem value="GENERAL">عام</MenuItem>
              <MenuItem value="DENTAL_ORAL">الفم والأسنان</MenuItem>
              <MenuItem value="EYE">العين</MenuItem>
              <MenuItem value="UNKNOWN">غير محدد</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} sm={4}>
            <TextField
              select
              fullWidth
              label="نوع الإجراء"
              name="procedureType"
              value={formData.procedureType || ''}
              onChange={handleChange}
            >
              <MenuItem value="CONSULTATION">استشارة</MenuItem>
              <MenuItem value="MINOR_SURGERY">جراحة صغرى</MenuItem>
              <MenuItem value="MAJOR_SURGERY">جراحة كبرى</MenuItem>
              <MenuItem value="IMAGING">أشعة وتصوير</MenuItem>
              <MenuItem value="DENTAL_ROUTINE">أسنان روتيني</MenuItem>
              <MenuItem value="DENTAL_ADVANCED">أسنان متقدم</MenuItem>
              <MenuItem value="UNKNOWN">غير محدد</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="التصنيف التأميني المقترح"
              name="suggestedInsuranceCategoryCode"
              value={formData.suggestedInsuranceCategoryCode || ''}
              onChange={handleChange}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
             <Box display="flex" alignItems="center" height="100%">
               <FormControlLabel
                 control={
                   <Checkbox
                     checked={formData.requiresReview}
                     onChange={handleChange}
                     name="requiresReview"
                     color="warning"
                   />
                 }
                 label="يتطلب مراجعة بشرية"
               />
             </Box>
          </Grid>

          <Grid item xs={12}>
             <TextField
               fullWidth
               label="سبب التصنيف / الشرح"
               name="reason"
               value={formData.reason || ''}
               onChange={handleChange}
               multiline
               rows={2}
             />
          </Grid>

          <Grid item xs={12}>
            <Divider sx={{ my: 1 }} />
            <FormControlLabel
              control={
                <Checkbox
                  checked={formData.saveAsRule || false}
                  onChange={handleChange}
                  name="saveAsRule"
                  color="primary"
                />
              }
              label="حفظ هذا التعديل كقاعدة دلالية مستقبلية (Medical Semantic Rule)"
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="inherit">إلغاء</Button>
        <Button onClick={handleSave} variant="contained" color="primary">حفظ وتحديث</Button>
      </DialogActions>
    </Dialog>
  );
}
