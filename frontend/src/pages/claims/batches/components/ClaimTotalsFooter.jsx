import { Box, Button, Typography, alpha, Tooltip, TextField, Stack } from '@mui/material';
import RejectIcon from '@mui/icons-material/Block';
import WarnIcon from '@mui/icons-material/WarningAmber';

export const ClaimTotalsFooter = ({
  isClaimRejected,
  handleSave,
  saving,
  isDirty,
  coveragePending,
  financialDataUnavailable,
  hasUncoveredLines,
  setIsClaimRejected,
  setIsDirty,
  setRejectionInput,
  openRejectDialog,
  totals,
  beneficiaryPaidAmount = '',
  beneficiarySettlement,
  onBeneficiaryPaidAmountChange,
  theme,
  lines,
  t,
  visibleColumns,
  saveDisabledReason
}) => {
  // اكتشاف أن جميع البنود مرفوضة
  const activeLines = (lines || []).filter((l) => l.service || l.serviceName);
  const allLinesRejected = activeLines.length > 0 && activeLines.every((l) => l.rejected);
  const showRejected = isClaimRejected || allLinesRejected;
  const requiresClaimRejection = !showRejected && hasUncoveredLines;
  const netApproved = totals.total - totals.refused;
  const settlement = beneficiarySettlement || {
    paid: 0,
    appliedToBaseShare: 0,
    appliedToRefused: 0,
    remainingBeneficiaryShare: totals.employee || 0,
    providerRefusedBalance: totals.refused || 0,
    excessPayment: 0,
    finalBeneficiaryShare: totals.employee || 0
  };
  const showSettlementDetails = settlement.paid > 0;
  const effectiveRefused = showSettlementDetails ? settlement.providerRefusedBalance : totals.refused;
  const beneficiaryDisplayLabel = 'التزام المستفيد';
  const beneficiaryDisplayAmount = showSettlementDetails ? settlement.finalBeneficiaryShare : totals.employee;
  const settlementTooltip = showSettlementDetails
    ? `التزام المستفيد الأساسي: ${totals.employee.toFixed(2)} د.ل، مدفوع خارج التأمين: ${settlement.paid.toFixed(2)} د.ل، خُصم من المرفوض: ${settlement.appliedToRefused.toFixed(
        2
      )} د.ل، المتبقي على مقدم الخدمة: ${settlement.providerRefusedBalance.toFixed(2)} د.ل`
    : 'مبلغ إضافي دفعه المستفيد خارج التأمين؛ يضاف إلى التزامه ويخصم من المرفوض.';

  const blockingDisabled = saving || coveragePending || financialDataUnavailable || Boolean(saveDisabledReason);
  const draftDisabled = blockingDisabled || !isDirty;
  const submitDisabled = blockingDisabled;
  const blockingDisabledTitle =
    saveDisabledReason ||
    (saving
      ? 'جارٍ حفظ المطالبة'
      : coveragePending
        ? 'انتظر اكتمال حساب التغطية لكل البنود'
        : financialDataUnavailable
          ? 'انتظر نجاح التحقق من الوثيقة والعقد والسقف'
          : '');
  const draftDisabledTitle = !blockingDisabled && !isDirty ? 'لا توجد تغييرات للحفظ' : blockingDisabledTitle;

  return (
    <Box
      sx={{
        flexShrink: 0,
        px: '1.25rem',
        py: '0.55rem',
        borderTop: `2px solid ${showRejected ? theme.palette.error.light : theme.palette.divider}`,
        display: 'flex',
        gap: '1.0rem',
        alignItems: 'center',
        bgcolor: showRejected ? alpha(theme.palette.error.main, 0.04) : alpha(theme.palette.primary.main, 0.02)
      }}
    >
      <Tooltip title={blockingDisabled ? blockingDisabledTitle : ''} arrow disableHoverListener={!blockingDisabled}>
        <span>
          {showRejected || requiresClaimRejection ? (
            <Button
              variant="contained"
              color="error"
              onClick={() => {
                if (requiresClaimRejection) {
                  openRejectDialog('claim');
                  return;
                }
                handleSave(true, 'submit');
              }}
              disabled={submitDisabled}
              sx={{ px: '2.0rem', fontWeight: 600 }}
            >
              {saving ? t('claimEntry.saving') : requiresClaimRejection ? 'رفض وحفظ المطالبة' : 'حفظ الرفض'}
            </Button>
          ) : (
            <Stack direction="row" spacing={1}>
              <Tooltip title={draftDisabled ? draftDisabledTitle : ''} arrow disableHoverListener={!draftDisabled}>
                <span>
                  <Button variant="outlined" onClick={() => handleSave(false, 'draft')} disabled={draftDisabled} sx={{ fontWeight: 700 }}>
                    حفظ كمسودة
                  </Button>
                </span>
              </Tooltip>
              <Button variant="contained" color="primary" onClick={() => handleSave(true, 'submit')} disabled={submitDisabled} sx={{ px: '1.8rem', fontWeight: 700 }}>
                {saving ? t('claimEntry.saving') : 'إرسال'}
              </Button>
            </Stack>
          )}
        </span>
      </Tooltip>

      {!isClaimRejected && !allLinesRejected ? (
        <Button
          variant="outlined"
          color="error"
          startIcon={<RejectIcon />}
          onClick={() => openRejectDialog('claim')}
          sx={{ fontWeight: 500 }}
        >
          رفض المطالبة
        </Button>
      ) : isClaimRejected ? (
        <Button
          variant="text"
          onClick={() => {
            setIsClaimRejected(false);
            setIsDirty?.(true);
            if (typeof setRejectionInput === 'function') setRejectionInput('');
          }}
          sx={{ fontWeight: 500 }}
        >
          تغيير للقبول
        </Button>
      ) : null}

      {/* تحذير عند رفض جميع البنود تلقائياً */}
      {allLinesRejected && !isClaimRejected && (
        <Tooltip title="جميع البنود مرفوضة — ستُحفظ المطالبة تلقائياً كمرفوضة" arrow>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'error.main' }}>
            <WarnIcon sx={{ fontSize: '1rem' }} />
            <Typography variant="caption" color="error.main" fontWeight={600} sx={{ fontSize: '0.78rem' }}>
              جميع البنود مرفوضة
            </Typography>
          </Box>
        </Tooltip>
      )}

      {!showRejected && hasUncoveredLines && (
        <Tooltip title="لا يمكن اعتماد مطالبة تحتوي بنداً غير مغطى. غيّر السياق أو ارفض البند/المطالبة." arrow>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'error.main' }}>
            <WarnIcon sx={{ fontSize: '1rem' }} />
            <Typography variant="caption" color="error.main" fontWeight={700} sx={{ fontSize: '0.78rem' }}>
              يوجد بند غير مغطى — الحفظ معتمد ممنوع
            </Typography>
          </Box>
        </Tooltip>
      )}

      <Tooltip title={settlementTooltip} arrow>
        <TextField
          size="small"
          label="مدفوع المستفيد"
          value={beneficiaryPaidAmount}
          onChange={(event) => {
            const value = event.target.value.replace(/[^\d.]/g, '');
            const parts = value.split('.');
            onBeneficiaryPaidAmountChange?.(parts.length > 2 ? `${parts[0]}.${parts.slice(1).join('')}` : value);
          }}
          inputProps={{ inputMode: 'decimal', min: 0, style: { textAlign: 'center', fontWeight: 800 } }}
          sx={{
            width: 138,
            flexShrink: 0,
            '& .MuiInputBase-root': { height: 36 },
            '& .MuiInputLabel-root': { fontSize: '0.72rem' }
          }}
          error={false}
          helperText={
            settlement.excessPayment > 0
              ? `فائض خارج المرفوض ${settlement.excessPayment.toFixed(2)}`
              : showSettlementDetails
                ? `يخصم من المرفوض ${settlement.appliedToRefused.toFixed(2)}`
                : ' '
          }
          FormHelperTextProps={{ sx: { m: 0, mt: 0.1, textAlign: 'center', fontSize: '0.65rem', lineHeight: 1 } }}
        />
      </Tooltip>

      <Box sx={{ mr: 'auto', display: 'flex', gap: '1.2rem', alignItems: 'flex-start' }}>
        <Box sx={{ textAlign: 'center' }}>
          <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>
            الإجمالي
          </Typography>
          <Typography variant="subtitle2" fontWeight={800} color="text.primary" sx={{ fontSize: '1.15rem' }}>
            {totals.total.toFixed(2)}
          </Typography>
        </Box>

        {effectiveRefused > 0 && (
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" display="block" color="error.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>
              المرفوض
            </Typography>
            <Typography variant="subtitle2" fontWeight={800} color="error.main" sx={{ fontSize: '1.15rem' }}>
              {effectiveRefused.toFixed(2)}
            </Typography>
          </Box>
        )}

        <Box sx={{ textAlign: 'center' }}>
          <Typography variant="caption" display="block" color="primary.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>
            الصافي
          </Typography>
          <Typography
            variant="subtitle2"
            fontWeight={800}
            color={netApproved > 0 ? 'primary.main' : 'text.disabled'}
            sx={{ fontSize: '1.15rem' }}
          >
            {netApproved.toFixed(2)}
          </Typography>
        </Box>

        {visibleColumns.companyShare && (
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" display="block" color="success.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>
              التزام الشركة
            </Typography>
            <Typography variant="subtitle2" fontWeight={800} color="success.main" sx={{ fontSize: '1.15rem' }}>
              {totals.company.toFixed(2)}
            </Typography>
          </Box>
        )}
        {visibleColumns.patientShare && (
          <Tooltip
            title={
              showSettlementDetails
                ? `يشمل التزامه الأساسي ${totals.employee.toFixed(2)} د.ل + مدفوع خارج التأمين ${settlement.paid.toFixed(2)} د.ل`
                : ''
            }
            arrow
            disableHoverListener={!showSettlementDetails}
          >
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="caption" display="block" color="warning.dark" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>
                {beneficiaryDisplayLabel}
              </Typography>
              <Typography variant="subtitle2" fontWeight={800} color="warning.dark" sx={{ fontSize: '1.15rem' }}>
              {beneficiaryDisplayAmount.toFixed(2)}
            </Typography>
          </Box>
        </Tooltip>
        )}
        {showSettlementDetails && (
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.75rem', fontWeight: 700 }}>
              مدفوع خارج التأمين
            </Typography>
            <Typography variant="subtitle2" fontWeight={800} color="info.dark" sx={{ fontSize: '1.0rem' }}>
              {settlement.paid.toFixed(2)}
            </Typography>
          </Box>
        )}
        {showSettlementDetails && (
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.75rem', fontWeight: 700 }}>
              على مقدم الخدمة
            </Typography>
            <Typography
              variant="subtitle2"
              fontWeight={800}
              color={settlement.providerRefusedBalance > 0 ? 'error.dark' : 'success.main'}
              sx={{ fontSize: '1.0rem' }}
            >
              {settlement.providerRefusedBalance.toFixed(2)}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
};
