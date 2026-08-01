import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Grid,
  FormControlLabel,
  Checkbox,
  TextField,
  MenuItem,
  Stack,
  Typography
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { CONTRACT_STATUS_CONFIG, PRICING_MODEL_CONFIG } from 'services/api/provider-contracts.service';
import dayjs from 'dayjs';

const BulkEditContractsDialog = ({ open, onClose, onConfirm, selectedCount }) => {
  const [formData, setFormData] = useState({
    status: 'ACTIVE',
    pricingModel: 'DISCOUNT',
    discountPercent: 10,
    discountBeforeRejection: false,
    startDate: null,
    endDate: null,
    reason: ''
  });

  const [toggles, setToggles] = useState({
    updateStatus: false,
    updatePricingModel: false,
    updateDiscountPercent: false,
    updateDiscountTiming: false,
    updateStartDate: false,
    updateEndDate: false
  });

  const handleToggle = (field) => {
    setToggles((prev) => ({ ...prev, [field]: !prev[field] }));
  };

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleConfirm = () => {
    onConfirm({
      ...formData,
      ...toggles,
      startDate: formData.startDate ? formData.startDate.format('YYYY-MM-DD') : null,
      endDate: formData.endDate ? formData.endDate.format('YYYY-MM-DD') : null
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>تعديل جماعي للعقود ({selectedCount} عقود محددة)</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          قم بتفعيل صندوق الاختيار بجانب الحقل الذي ترغب في تعديله وتطبيقه على جميع العقود المحددة.
        </Typography>

        <Grid container spacing={3}>
          {/* Status */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updateStatus} onChange={() => handleToggle('updateStatus')} />}
                label=""
              />
              <TextField
                select
                fullWidth
                label="الحالة"
                value={formData.status}
                onChange={(e) => handleChange('status', e.target.value)}
                disabled={!toggles.updateStatus}
              >
                {Object.entries(CONTRACT_STATUS_CONFIG).map(([key, config]) => (
                  <MenuItem key={key} value={key}>
                    {config.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
          </Grid>

          {toggles.updateStatus && (formData.status === 'SUSPENDED' || formData.status === 'TERMINATED') && (
            <Grid item xs={12}>
              <TextField
                fullWidth
                label={formData.status === 'SUSPENDED' ? 'سبب التعليق' : 'سبب الإيقاف'}
                value={formData.reason}
                onChange={(e) => handleChange('reason', e.target.value)}
                required
                multiline
                minRows={2}
              />
            </Grid>
          )}

          {/* Pricing Model */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updatePricingModel} onChange={() => handleToggle('updatePricingModel')} />}
                label=""
              />
              <TextField
                select
                fullWidth
                label="نموذج التسعير"
                value={formData.pricingModel}
                onChange={(e) => handleChange('pricingModel', e.target.value)}
                disabled={!toggles.updatePricingModel}
              >
                {Object.entries(PRICING_MODEL_CONFIG).map(([key, config]) => (
                  <MenuItem key={key} value={key}>
                    {config.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
          </Grid>

          {/* Discount Percent */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updateDiscountPercent} onChange={() => handleToggle('updateDiscountPercent')} />}
                label=""
              />
              <TextField
                fullWidth
                type="number"
                label="نسبة الخصم (%)"
                value={formData.discountPercent}
                onChange={(e) => handleChange('discountPercent', e.target.value)}
                disabled={!toggles.updateDiscountPercent}
                InputProps={{ inputProps: { min: 0, max: 100 } }}
              />
            </Stack>
          </Grid>

          {/* Discount Timing */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updateDiscountTiming} onChange={() => handleToggle('updateDiscountTiming')} />}
                label=""
              />
              <TextField
                select
                fullWidth
                label="آلية الخصم"
                value={formData.discountBeforeRejection}
                onChange={(e) => handleChange('discountBeforeRejection', e.target.value === 'true')}
                disabled={!toggles.updateDiscountTiming}
              >
                <MenuItem value="false">بعد المرفوض</MenuItem>
                <MenuItem value="true">قبل المرفوض</MenuItem>
              </TextField>
            </Stack>
          </Grid>

          {/* Start Date */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updateStartDate} onChange={() => handleToggle('updateStartDate')} />}
                label=""
              />
              <DatePicker
                label="تاريخ البدء"
                value={formData.startDate}
                onChange={(date) => handleChange('startDate', date)}
                disabled={!toggles.updateStartDate}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Stack>
          </Grid>

          {/* End Date */}
          <Grid item xs={12}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControlLabel
                control={<Checkbox checked={toggles.updateEndDate} onChange={() => handleToggle('updateEndDate')} />}
                label=""
              />
              <DatePicker
                label="تاريخ الانتهاء"
                value={formData.endDate}
                onChange={(date) => handleChange('endDate', date)}
                disabled={!toggles.updateEndDate}
                slotProps={{ textField: { fullWidth: true } }}
              />
            </Stack>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="inherit">
          إلغاء
        </Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          color="primary"
          disabled={
            !Object.values(toggles).some(Boolean) ||
            (toggles.updateStatus && (formData.status === 'SUSPENDED' || formData.status === 'TERMINATED') && !formData.reason?.trim())
          }
        >
          تطبيق التعديلات
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default BulkEditContractsDialog;
