import React, { Fragment } from 'react';
import {
  TableRow,
  TableCell,
  Stack,
  Autocomplete,
  TextField,
  Chip,
  Tooltip,
  Typography,
  IconButton,
  alpha,
  createFilterOptions,
  Button,
  Box
} from '@mui/material';

const serviceFilter = createFilterOptions({
  stringify: (opt) => `${opt.serviceCode || opt.code || ''} ${opt.serviceName || opt.name || ''}`,
  ignoreAccents: true,
  ignoreCase: true,
  trim: true,
  matchFrom: 'any'
});
import {
  Block as RejectIcon,
  Delete as DeleteIcon,
  WarningAmber as WarningIcon,
  Add as AddIcon,
  MedicalServices as MedicalServicesIcon,
  MenuBook as DictionaryIcon
} from '@mui/icons-material';
import { isValidClaimQuantity } from '../claim-entry-validation';

const inlineSx = {
  '& .MuiInputBase-root': { fontSize: '0.85rem', fontWeight: 400 },
  '& input': { textAlign: 'center', py: 0.5 }
};

export const ClaimLineRow = ({
  line,
  idx,
  theme,
  serviceOptions,
  loadingServices,
  servicesError,
  servicesErrorMessage,
  onRetryServices,
  onServiceSearchChange,
  updateLine,
  handleServiceChange,
  removeLine,
  openRejectDialog,
  policyInfo,
  visibleColumns = {
    coverage: true,
    benefitLimit: true,
    remainingLimit: true,
    refused: true,
    companyShare: true,
    patientShare: true
  },
  triggerConfirm,
  onOpenCustomServiceDialog,
  onOpenClassificationReview
}) => {
  const priceRefused = parseFloat(line.priceRefused) || 0;
  const limitRefused = parseFloat(line.limitRefused) || 0;
  const refusedAmount = parseFloat(line.refusedAmount) || 0;
  const hasFinancialRefusal = refusedAmount > 0 || priceRefused > 0 || limitRefused > 0;
  const financialRefusalText =
    line.rejectionReason ||
    [
      priceRefused > 0 ? `خصم فارق السعر التعاقدي: ${priceRefused.toFixed(2)} د.ل` : null,
      limitRefused > 0 ? `تجاوز سقف المنفعة: ${limitRefused.toFixed(2)} د.ل` : null
    ]
      .filter(Boolean)
      .join(' — ') ||
    'تجاوز السعر التعاقدي و/أو سقف المنفعة';
  const categoryName =
    line.medicalCategoryName ||
    line.serviceCategoryName ||
    line.service?.medicalCategoryName ||
    line.service?.categoryName ||
    line.service?.medicalCategory?.nameAr ||
    line.service?.medicalCategory?.name ||
    line.service?.effectiveCategory?.nameAr ||
    line.service?.effectiveCategory?.name ||
    '';
  const categoryCode =
    line.medicalCategoryCode ||
    line.service?.medicalCategoryCode ||
    line.service?.categoryCode ||
    line.service?.medicalCategory?.code ||
    line.service?.effectiveCategory?.code ||
    '';
  const quantityInvalid = Boolean(line.service || line.serviceName) && !isValidClaimQuantity(line.quantity);

  return (
    <Fragment>
      <TableRow
        sx={{
          bgcolor: line.rejected
            ? alpha(theme.palette.error.main, 0.05)
            : line.notCovered
              ? alpha(theme.palette.error.main, 0.04)
              : line.manualRefusedAmount > 0
                ? alpha(theme.palette.warning.main, 0.04)
                : line.usageExceeded
                  ? alpha(theme.palette.warning.main, 0.02)
                  : 'transparent'
        }}
      >
        <TableCell align="center" sx={{ fontWeight: 600, color: 'text.secondary', width: '2.5rem' }}>
          {idx + 1}
        </TableCell>
        <TableCell align="right" sx={{ minWidth: '17.5rem' }}>
          <Stack spacing={0.5}>
            <Autocomplete
              size="small"
              options={serviceOptions}
              loading={loadingServices}
              value={line.service || null}
              onChange={(_, val) => handleServiceChange(idx, val)}
              onInputChange={(_, value, reason) => {
                if (reason === 'input' || reason === 'clear') onServiceSearchChange?.(value);
              }}
              filterOptions={serviceFilter}
              getOptionLabel={(o) => {
                const name = o.label || o.serviceName || '';
                if (o.contractPrice != null && o.maxContractPrice != null && o.maxContractPrice > o.contractPrice) {
                  return `${name} [${o.contractPrice} - ${o.maxContractPrice} د.ل]`;
                }
                return name;
              }}
              isOptionEqualToValue={(opt, val) =>
                (opt?.pricingItemId != null && opt.pricingItemId === val?.pricingItemId) ||
                (opt?.serviceCode != null && (opt.serviceCode === val?.serviceCode || opt.serviceCode === val?.medicalServiceCode))
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  variant="standard"
                  placeholder={loadingServices ? 'جاري التحميل...' : 'ابحث عن خدمة...'}
                  inputProps={{ ...params.inputProps, style: { textAlign: 'right' } }}
                />
              )}
              noOptionsText={
                <Stack spacing={1} alignItems="center" sx={{ py: 1 }}>
                  <Typography variant="body2">
                    {loadingServices
                      ? 'جاري تحميل خدمات العقد...'
                      : servicesError
                        ? servicesErrorMessage || 'تعذر تحميل خدمات العقد'
                        : 'لم يتم العثور على خدمات في العقد'}
                  </Typography>
                  {servicesError && onRetryServices && (
                    <Button size="small" onClick={() => onRetryServices()}>
                      إعادة المحاولة
                    </Button>
                  )}
                  {!loadingServices && !servicesError && onOpenCustomServiceDialog && (
                    <Button
                      size="small"
                      variant="contained"
                      color="primary"
                      startIcon={<MedicalServicesIcon sx={{ ml: 1, mr: 0 }} />}
                      onMouseDown={(e) => {
                        // Prevents Autocomplete blur before click event fires
                        e.preventDefault();
                      }}
                      onClick={(e) => {
                        e.stopPropagation();
                        onOpenCustomServiceDialog();
                      }}
                      sx={{ fontSize: '0.75rem', py: 0.5 }}
                    >
                      إضافة خدمة جديدة لعقد مقدم الخدمة
                    </Button>
                  )}
                </Stack>
              }
            />
            {onOpenCustomServiceDialog && (
              <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                <Button
                  size="small"
                  color="secondary"
                  startIcon={<AddIcon sx={{ ml: 0.5, mr: 0 }} />}
                  onClick={onOpenCustomServiceDialog}
                  sx={{ fontSize: '0.7rem', p: 0, minWidth: 0, height: 'auto', mt: 0.2 }}
                >
                  خدمة غير متوفرة؟ أضفها هنا
                </Button>
              </Box>
            )}
            {(categoryName || categoryCode) && (
              <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                <Chip
                  size="small"
                  variant="outlined"
                  color="primary"
                  label={`${categoryName || 'تصنيف طبي'}${categoryCode ? ` (${categoryCode})` : ''}`}
                  sx={{
                    maxWidth: '100%',
                    height: 22,
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    bgcolor: alpha(theme.palette.primary.main, 0.06),
                    '& .MuiChip-label': {
                      display: 'block',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis'
                    }
                  }}
                />
              </Box>
            )}
          </Stack>
        </TableCell>
        <TableCell align="center">
          <TextField
            variant="standard"
            type="number"
            value={line.quantity}
            onChange={(e) => {
              const v = e.target.value;
              if (v === '' || Number(v) >= 0) updateLine(idx, { quantity: v });
            }}
            error={quantityInvalid}
            helperText={quantityInvalid ? 'عدد صحيح > 0' : null}
            inputProps={{ min: 1, step: 1 }}
            sx={inlineSx}
          />
        </TableCell>
        <TableCell align="center">
          <Tooltip
            title={
              line.service?.maxContractPrice > line.service?.contractPrice
                ? line.unitPrice > line.service?.maxContractPrice
                  ? `السعر يتجاوز الحد الأقصى (${line.service.maxContractPrice})`
                  : line.unitPrice < line.service?.contractPrice && line.unitPrice > 0
                    ? `السعر أقل من الحد الأدنى (${line.service.contractPrice})`
                    : ''
                : line.contractPrice > 0 && line.unitPrice > line.contractPrice
                  ? `السعر يتجاوز العقد (${line.contractPrice})`
                  : ''
            }
            arrow
          >
            <TextField
              variant="standard"
              type="number"
              value={line.unitPrice}
              onChange={(e) => {
                const v = e.target.value;
                if (v === '' || Number(v) >= 0) updateLine(idx, { unitPrice: v });
              }}
              inputProps={{ min: 0 }}
              sx={{
                ...inlineSx,
                '& input': {
                  ...inlineSx['& input'],
                  color: (
                    line.service?.maxContractPrice > line.service?.contractPrice
                      ? line.unitPrice > line.service?.maxContractPrice ||
                        (line.unitPrice < line.service?.contractPrice && line.unitPrice > 0)
                      : line.contractPrice > 0 && line.unitPrice > line.contractPrice
                  )
                    ? 'error.main'
                    : 'inherit',
                  fontWeight: (
                    line.service?.maxContractPrice > line.service?.contractPrice
                      ? line.unitPrice > line.service?.maxContractPrice ||
                        (line.unitPrice < line.service?.contractPrice && line.unitPrice > 0)
                      : line.contractPrice > 0 && line.unitPrice > line.contractPrice
                  )
                    ? 900
                    : 'inherit'
                }
              }}
            />
          </Tooltip>
        </TableCell>
        {visibleColumns.coverage && (
          <TableCell align="center">
            <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 400, color: 'text.secondary' }}>
              {line.coveragePercent !== null ? `${line.coveragePercent}%` : 'بانتظار الحساب'}
            </Typography>
          </TableCell>
        )}
        {visibleColumns.benefitLimit && (
          <TableCell align="center">
            {Number(line.usageDetails?.amountLimit) > 0 || Number(line.usageDetails?.timesLimit) > 0 ? (
              <Stack spacing={0.35} alignItems="center">
                {Number(line.usageDetails?.timesLimit) > 0 && (
                  <Typography variant="caption" sx={{ fontSize: '0.75rem', fontWeight: 700, whiteSpace: 'nowrap' }}>
                    مرات: {Number(line.usageDetails.timesLimit)}
                  </Typography>
                )}
                {Number(line.usageDetails?.amountLimit) > 0 && (
                  <Typography variant="caption" sx={{ fontSize: '0.75rem', fontWeight: 700, whiteSpace: 'nowrap' }}>
                    د.ل: {Number(line.usageDetails.amountLimit).toFixed(2)}
                  </Typography>
                )}
              </Stack>
            ) : (
              <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>—</Typography>
            )}
          </TableCell>
        )}
        {visibleColumns.remainingLimit && (
          <TableCell align="center">
            {line.usageDetails ? (
              <Stack spacing={0.3} alignItems="center" justifyContent="center">
                {line.usageDetails.timesLimit > 0 &&
                  (() => {
                    // usedCount من الـ backend يتضمن الكمية الحالية بعد الإصلاح
                    const used = line.usageDetails.usedCount ?? 0;
                    const limit = line.usageDetails.timesLimit;
                    const remaining = Math.max(0, limit - used);
                    return (
                      <Typography
                        variant="caption"
                        sx={{
                          fontSize: '0.75rem',
                          color: remaining === 0 ? 'error.main' : 'primary.main',
                          fontWeight: 600,
                          whiteSpace: 'nowrap'
                        }}
                      >
                        مرات: {remaining}
                      </Typography>
                    );
                  })()}
                {line.usageDetails.amountLimit > 0 &&
                  (() => {
                    // remainingAmount محسوب من الـ backend مباشرة
                    const remaining = Math.max(
                      0,
                      line.usageDetails.remainingAmount != null
                        ? line.usageDetails.remainingAmount
                        : line.usageDetails.amountLimit - (line.usageDetails.usedAmount ?? 0)
                    );
                    return (
                      <Typography
                        variant="caption"
                        sx={{
                          fontSize: '0.75rem',
                          color: remaining <= 0 ? 'error.main' : 'primary.main',
                          fontWeight: 600,
                          whiteSpace: 'nowrap'
                        }}
                      >
                        د.ل: {remaining.toFixed(2)}
                      </Typography>
                    );
                  })()}
              </Stack>
            ) : line.service ? (
              <Typography variant="caption" sx={{ fontSize: '0.7rem', color: 'text.disabled' }}>
                —
              </Typography>
            ) : null}
          </TableCell>
        )}
        {visibleColumns.refused && (
          <TableCell align="center">
            {(() => {
              // refusedAmount يتضمّن: تجاوز السعر + تجاوز السقف + الرفض اليدوي الجزئي + الرفض الكلي
              const refusedVal = Math.max(refusedAmount, priceRefused + limitRefused);
              const isPartial = !line.rejected && (parseFloat(line.manualRefusedAmount) || 0) > 0;
              if (refusedVal <= 0) {
                return (
                  <Typography variant="body2" sx={{ fontSize: '0.85rem', color: 'text.disabled' }}>
                    —
                  </Typography>
                );
              }
              const tooltipTitle = line.rejected
                ? line.rejectionReason || 'الخدمة مرفوضة بالكامل'
                : isPartial
                  ? `رفض جزئي: ${refusedVal.toFixed(2)} د.ل — ${line.rejectionReason || ''}`
                  : financialRefusalText;
              return (
                <Tooltip title={tooltipTitle} arrow>
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: '0.85rem',
                      fontWeight: 700,
                      color: isPartial ? 'warning.dark' : 'error.main'
                    }}
                  >
                    {refusedVal.toFixed(2)}
                    {isPartial && (
                      <Typography component="span" sx={{ fontSize: '0.65rem', mr: 0.4 }}>
                        جزئي
                      </Typography>
                    )}
                  </Typography>
                </Tooltip>
              );
            })()}
          </TableCell>
        )}
        {visibleColumns.companyShare && (
          <TableCell align="center">
            <Typography variant="caption" sx={{ fontSize: '0.95rem', fontWeight: 700, color: 'success.main' }}>
              {line.byCompany?.toFixed(2)}
            </Typography>
          </TableCell>
        )}
        {visibleColumns.patientShare && (
          <TableCell align="center">
            <Typography variant="caption" sx={{ fontSize: '0.95rem', fontWeight: 700, color: 'warning.dark' }}>
              {line.byEmployee?.toFixed(2)}
            </Typography>
          </TableCell>
        )}
        <TableCell align="center">
          <Typography variant="body2" sx={{ fontSize: '1.0rem', fontWeight: 800, color: 'primary.main' }}>
            {line.total?.toFixed(2)}
          </Typography>
        </TableCell>
        <TableCell align="left">
          <Stack direction="row" spacing={0} justifyContent="flex-start" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
            {onOpenClassificationReview && (
              <Tooltip title="مراجعة/اعتماد تصنيف البند أو إرساله لقائمة مراجعة القاموس" arrow>
                <span>
                  <IconButton
                    size="small"
                    color="primary"
                    disabled={!line.serviceName && !line.service?.serviceName && !line.service?.name}
                    onClick={() => onOpenClassificationReview(idx)}
                  >
                    <DictionaryIcon sx={{ fontSize: '0.9375rem' }} />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            <Tooltip title={line.rejected ? 'إلغاء الرفض الكلي' : line.manualRefusedAmount > 0 ? 'إلغاء الرفض الجزئي' : 'رفض البند'} arrow>
              <IconButton
                size="small"
                color={line.rejected ? 'error' : line.manualRefusedAmount > 0 ? 'warning' : 'default'}
                onClick={() => {
                  if (line.rejected) {
                    triggerConfirm(
                      'إلغاء الرفض الكلي',
                      'هل أنت متأكد من إلغاء الرفض الكلي لهذا البند؟ سيتم إعادة احتساب التغطية والمبالغ.',
                      () =>
                        updateLine(idx, {
                          rejected: false,
                          rejectionReason: '',
                          byCompany: undefined,
                          refusedAmount: 0,
                          oldRejected: 0 // Reset status change tracker
                        })
                    );
                  } else if (line.manualRefusedAmount > 0) {
                    triggerConfirm('إلغاء الرفض الجزئي', 'هل تريد إلغاء مبلغ الرفض اليدوي (الجزئي) لهذا البند؟', () =>
                      updateLine(idx, {
                        manualRefusedAmount: 0,
                        rejectionReason: '',
                        byCompany: undefined,
                        refusedAmount: 0
                      })
                    );
                  } else {
                    openRejectDialog('line', idx);
                  }
                }}
              >
                <RejectIcon sx={{ fontSize: '0.9375rem' }} />
              </IconButton>
            </Tooltip>
            <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
              <DeleteIcon sx={{ fontSize: '0.9375rem' }} />
            </IconButton>
          </Stack>
        </TableCell>
      </TableRow>
      {line.rejected && (
        <TableRow sx={{ bgcolor: alpha(theme.palette.error.main, 0.02) }}>
          <TableCell colSpan={12} sx={{ py: 0.5 }}>
            <Typography variant="caption" color="error" fontWeight={500} sx={{ fontSize: '0.75rem', px: '1.0rem' }}>
              🚫 رفض كلي — {line.rejectionReason}
            </Typography>
          </TableCell>
        </TableRow>
      )}
      {!line.rejected && hasFinancialRefusal && (
        <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.03) }}>
          <TableCell colSpan={12} sx={{ py: 0.5 }}>
            <Typography variant="caption" color="warning.dark" fontWeight={500} sx={{ fontSize: '0.75rem', px: '1.0rem' }}>
              ⚠️ خصم/رفض جزئي: {Math.max(refusedAmount, priceRefused + limitRefused).toFixed(2)} د.ل — {financialRefusalText}
            </Typography>
          </TableCell>
        </TableRow>
      )}
      {line.usageExceeded && !line.rejected && (
        <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.05) }}>
          <TableCell colSpan={12} sx={{ py: 0.5 }}>
            <Typography
              variant="caption"
              color={line.usageExhausted ? 'error.main' : 'warning.dark'}
              fontWeight={600}
              sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}
            >
              {line.usageExhausted ? <RejectIcon sx={{ fontSize: '0.875rem' }} /> : <WarningIcon sx={{ fontSize: '0.875rem' }} />}
              {line.usageExhausted ? '⚠️ رصيد المنفعة استنفذ بالكامل: ' : '⚠️ تجاوز سقف المنفعة المحدد: '}
              {line.usageDetails?.timesLimit > 0 &&
                `(سيُّسجَّل ${(line.usageDetails.totalUsedCount || 0) + 1} من أصل ${line.usageDetails.timesLimit} مرّة/سنة)`}
              {line.usageDetails?.amountLimit > 0 &&
                (() => {
                  const prev = parseFloat(line.usageDetails.usedAmountBeforeLine || 0);
                  const curr = parseFloat(line.usageDetails.requestedAmountForLimit || 0);
                  const accepted = parseFloat(line.usageDetails.approvedAmountForLimit || 0);
                  const limit = parseFloat(line.usageDetails.amountLimit || 0);
                  const total = parseFloat((prev + curr).toFixed(2));
                  const basis =
                    line.usageDetails.consumptionBasis === 'ELIGIBLE_AMOUNT'
                      ? 'إجمالي الخدمة المؤهلة قبل التحمل'
                      : 'حصة الشركة بعد التحمل';
                  return ` (${basis}: مستخدم قبل السطر ${prev.toFixed(2)} + مطلوب ${curr.toFixed(2)} = ${total.toFixed(2)}؛ المقبول ${accepted.toFixed(2)} من حد ${limit.toFixed(2)} د.ل)`;
                })()}
            </Typography>
          </TableCell>
        </TableRow>
      )}
      {line.requiresPreApproval && !line.rejected && (
        <TableRow sx={{ bgcolor: alpha(theme.palette.info.main, 0.05) }}>
          <TableCell colSpan={12} sx={{ py: 0.5 }}>
            <Typography
              variant="caption"
              color="info.dark"
              fontWeight={600}
              sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}
            >
              🔒 هذه الخدمة تستلزم موافقة مسبقة (PA) — تأكد من إرفاق رقم الموافقة المسبقة
            </Typography>
          </TableCell>
        </TableRow>
      )}
      {line.notCovered && !line.coveragePending && !line.rejected && (
        <TableRow sx={{ bgcolor: alpha(theme.palette.error.main, 0.07) }}>
          <TableCell colSpan={12} sx={{ py: 0.5 }}>
            <Typography
              variant="caption"
              color="error.main"
              fontWeight={600}
              sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}
            >
              <RejectIcon sx={{ fontSize: '0.875rem' }} />
              {line.rejectionReason ||
                'هذه الخدمة غير مغطاة بالوثيقة في سياق المطالبة الحالي (تغطية 0%) — غيّر السياق أو اربط الخدمة بتصنيف مغطى لهذا السياق'}
            </Typography>
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
};
