import React, { Fragment } from 'react';
import {
    TableRow, TableCell, Stack, Autocomplete, TextField, Chip,
    Tooltip, Typography, IconButton, alpha, createFilterOptions
} from '@mui/material';

const serviceFilter = createFilterOptions({
    stringify: (opt) => `${opt.serviceCode || opt.code || ''} ${opt.serviceName || opt.name || ''}`,
    ignoreAccents: true,
    ignoreCase: true,
    trim: true,
    matchFrom: 'any',
});
import {
    Block as RejectIcon,
    Delete as DeleteIcon,
    WarningAmber as WarningIcon
} from '@mui/icons-material';

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
    }
}) => {
    return (
        <Fragment>
            <TableRow sx={{
                bgcolor: line.rejected ? alpha(theme.palette.error.main, 0.05) :
                    line.notCovered ? alpha(theme.palette.error.main, 0.04) :
                        ((line.manualRefusedAmount > 0) ? alpha(theme.palette.warning.main, 0.04) :
                            (line.usageExceeded ? alpha(theme.palette.warning.main, 0.02) : 'transparent'))
            }}>
                <TableCell align="center" sx={{ fontWeight: 600, color: 'text.secondary', width: '2.5rem' }}>{idx + 1}</TableCell>
                <TableCell align="right" sx={{ minWidth: '17.5rem' }}>
                    <Stack spacing={0.5}>
                        <Autocomplete
                            size="small"
                            options={serviceOptions}
                            loading={loadingServices}
                            value={line.service || null}
                            onChange={(_, val) => handleServiceChange(idx, val)}
                            filterOptions={serviceFilter}
                            getOptionLabel={o => o.label || o.serviceName || ''}
                            isOptionEqualToValue={(opt, val) =>
                                (opt?.pricingItemId != null && opt.pricingItemId === val?.pricingItemId) ||
                                (opt?.serviceCode != null && (opt.serviceCode === val?.serviceCode || opt.serviceCode === val?.medicalServiceCode))
                            }
                            renderInput={(params) => (
                                <TextField {...params} variant="standard"
                                    placeholder={loadingServices ? "جاري التحميل..." : "ابحث عن خدمة..."}
                                    inputProps={{ ...params.inputProps, style: { textAlign: 'right' } }}
                                />
                            )}
                            noOptionsText={loadingServices ? "جاري تحميل خدمات العقد..." : "لم يتم العثور على خدمات في العقد"}
                        />

                    </Stack>
                </TableCell>
                <TableCell align="center">
                    <TextField variant="standard" type="number" value={line.quantity}
                        onChange={e => { const v = e.target.value; if (v === '' || Number(v) >= 0) updateLine(idx, { quantity: v }); }}
                        inputProps={{ min: 0 }}
                        sx={inlineSx} />
                </TableCell>
                <TableCell align="center">
                    <Tooltip title={line.contractPrice > 0 && line.unitPrice > line.contractPrice ? `السعر يتجاوز العقد (${line.contractPrice})` : ''} arrow>
                        <TextField variant="standard" type="number" value={line.unitPrice}
                            onChange={e => { const v = e.target.value; if (v === '' || Number(v) >= 0) updateLine(idx, { unitPrice: v }); }}
                            inputProps={{ min: 0 }}
                            sx={{
                                ...inlineSx,
                                '& input': {
                                    ...inlineSx['& input'],
                                    color: line.contractPrice > 0 && line.unitPrice > line.contractPrice ? 'error.main' : 'inherit',
                                    fontWeight: line.contractPrice > 0 && line.unitPrice > line.contractPrice ? 900 : 'inherit'
                                }
                            }}
                        />
                    </Tooltip>
                </TableCell>
                {visibleColumns.coverage && (
                    <TableCell align="center">
                        <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 400, color: 'text.secondary' }}>
                            {line.coveragePercent !== null ? `${line.coveragePercent}%` : `${policyInfo?.defaultCoveragePercent ?? 100}%`}
                        </Typography>
                    </TableCell>
                )}
                {visibleColumns.benefitLimit && (
                    <TableCell align="center">
                        {line.usageDetails && (
                            <Stack spacing={0.3} alignItems="center" justifyContent="center">
                                {line.usageDetails.timesLimit > 0 && (
                                    <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.timesExceeded || (line.usageDetails.usedCount ?? 0) > line.usageDetails.timesLimit ? 'error.main' : 'text.secondary', fontWeight: 600, whiteSpace: 'nowrap' }}>
                                        مرات: {line.usageDetails.usedCount ?? 0}/{line.usageDetails.timesLimit}
                                    </Typography>
                                )}
                                {line.usageDetails.amountLimit > 0 && (
                                    <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.amountExceeded || line.usageDetails.usedAmount > line.usageDetails.amountLimit ? 'error.main' : 'text.secondary', fontWeight: 600, whiteSpace: 'nowrap' }}>
                                        د.ل: {(line.usageDetails.usedAmount ?? 0).toFixed(2)}/{line.usageDetails.amountLimit}
                                    </Typography>
                                )}
                            </Stack>
                        )}
                    </TableCell>
                )}
                {visibleColumns.remainingLimit && (
                    <TableCell align="center">
                        {line.usageDetails ? (
                            <Stack spacing={0.3} alignItems="center" justifyContent="center">
                                {line.usageDetails.timesLimit > 0 && (() => {
                                    // usedCount من الـ backend يتضمن الكمية الحالية بعد الإصلاح
                                    const used = line.usageDetails.usedCount ?? 0;
                                    const limit = line.usageDetails.timesLimit;
                                    const remaining = Math.max(0, limit - used);
                                    return (
                                        <Typography variant="caption" sx={{
                                            fontSize: '0.75rem',
                                            color: remaining === 0 ? 'error.main' : 'primary.main',
                                            fontWeight: 600, whiteSpace: 'nowrap'
                                        }}>
                                            مرات: {remaining}
                                        </Typography>
                                    );
                                })()}
                                {line.usageDetails.amountLimit > 0 && (() => {
                                    // remainingAmount محسوب من الـ backend مباشرة
                                    const remaining = Math.max(0, line.usageDetails.remainingAmount != null
                                        ? line.usageDetails.remainingAmount
                                        : line.usageDetails.amountLimit - (line.usageDetails.usedAmount ?? 0));
                                    return (
                                        <Typography variant="caption" sx={{
                                            fontSize: '0.75rem',
                                            color: remaining <= 0 ? 'error.main' : 'primary.main',
                                            fontWeight: 600, whiteSpace: 'nowrap'
                                        }}>
                                            د.ل: {remaining.toFixed(2)}
                                        </Typography>
                                    );
                                })()}
                            </Stack>
                        ) : line.service ? (
                            <Typography variant="caption" sx={{ fontSize: '0.7rem', color: 'text.disabled' }}>—</Typography>
                        ) : null}
                    </TableCell>
                )}
                {visibleColumns.refused && (
                    <TableCell align="center">
                        {(() => {
                            // refusedAmount يتضمّن: تجاوز السعر + تجاوز السقف + الرفض اليدوي الجزئي + الرفض الكلي
                            const refusedVal = parseFloat(line.refusedAmount) || 0;
                            const isPartial = !line.rejected && (parseFloat(line.manualRefusedAmount) || 0) > 0;
                            if (refusedVal <= 0) {
                                return (
                                    <Typography variant="body2" sx={{ fontSize: '0.85rem', color: 'text.disabled' }}>
                                        —
                                    </Typography>
                                );
                            }
                            const tooltipTitle = line.rejected
                                ? (line.rejectionReason || 'الخدمة مرفوضة بالكامل')
                                : isPartial
                                    ? `رفض جزئي: ${refusedVal.toFixed(2)} د.ل — ${line.rejectionReason || ''}`
                                    : (line.rejectionReason || `تجاوز سعر العقد (${line.contractPrice > 0 ? line.contractPrice : '—'})`);
                            return (
                                <Tooltip title={tooltipTitle} arrow>
                                    <Typography variant="body2" sx={{
                                        fontSize: '0.85rem', fontWeight: 700,
                                        color: isPartial ? 'warning.dark' : 'error.main'
                                    }}>
                                        {refusedVal.toFixed(2)}
                                        {isPartial && <Typography component="span" sx={{ fontSize: '0.65rem', mr: 0.4 }}>جزئي</Typography>}
                                    </Typography>
                                </Tooltip>
                            );
                        })()}
                    </TableCell>
                )}
                {visibleColumns.companyShare && (
                    <TableCell align="center">
                        <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 600, color: 'success.main' }}>
                            {line.byCompany?.toFixed(2)}
                        </Typography>
                    </TableCell>
                )}
                {visibleColumns.patientShare && (
                    <TableCell align="center">
                        <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 600, color: 'warning.dark' }}>
                            {line.byEmployee?.toFixed(2)}
                        </Typography>
                    </TableCell>
                )}
                <TableCell align="center">
                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 600, color: 'primary.main' }}>
                        {line.total?.toFixed(2)}
                    </Typography>
                </TableCell>
                <TableCell align="left">
                    <Stack direction="row" spacing={0} justifyContent="flex-start" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
                        <Tooltip title={
                            line.rejected ? 'إلغاء الرفض الكلي' :
                                (line.manualRefusedAmount > 0) ? 'إلغاء الرفض الجزئي' :
                                    'رفض البند'
                        } arrow>
                            <IconButton size="small"
                                color={line.rejected ? 'error' : (line.manualRefusedAmount > 0 ? 'warning' : 'default')}
                                onClick={() => {
                                    if (line.rejected) {
                                        updateLine(idx, { rejected: false, rejectionReason: '' });
                                    } else if (line.manualRefusedAmount > 0) {
                                        updateLine(idx, { manualRefusedAmount: 0, rejectionReason: '' });
                                    } else {
                                        openRejectDialog('line', idx);
                                    }
                                }}>
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
            {!line.rejected && (parseFloat(line.manualRefusedAmount) || 0) > 0 && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.03) }}>
                    <TableCell colSpan={12} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color="warning.dark" fontWeight={500} sx={{ fontSize: '0.75rem', px: '1.0rem' }}>
                            ⚠️ رفض جزئي: {parseFloat(line.manualRefusedAmount).toFixed(2)} د.ل من حصة الشركة — {line.rejectionReason}
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
            {line.usageExceeded && !line.rejected && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.05) }}>
                    <TableCell colSpan={12} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color={line.usageExhausted ? "error.main" : "warning.dark"} fontWeight={600} sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}>
                            {line.usageExhausted ? <RejectIcon sx={{ fontSize: '0.875rem' }} /> : <WarningIcon sx={{ fontSize: '0.875rem' }} />}
                            {line.usageExhausted ? "⚠️ رصيد المنفعة استنفذ بالكامل: " : "⚠️ تجاوز سقف المنفعة المحدد: "}
                            {line.usageDetails?.timesLimit > 0 && `(سيُّسجَّل ${(line.usageDetails.totalUsedCount || 0) + 1} من أصل ${line.usageDetails.timesLimit} مرّة/سنة)`}
                            {line.usageDetails?.amountLimit > 0 && (() => {
                                const prev = parseFloat(line.usageDetails.totalUsedAmount || 0);
                                const curr = parseFloat(line.usageDetails.currentRequestedAmount || 0);
                                const limit = parseFloat(line.usageDetails.amountLimit || 0);
                                const total = parseFloat((prev + curr).toFixed(2));
                                return ` (مستخدم مسبقاً: ${prev.toFixed(2)} + المطلوب حالياً: ${curr.toFixed(2)} = ${total} د.ل يتجاوز الحد ${limit.toFixed(2)} د.ل)`;
                            })()}
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
            {line.requiresPreApproval && !line.rejected && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.info.main, 0.05) }}>
                    <TableCell colSpan={12} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color="info.dark" fontWeight={600} sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}>
                            🔒 هذه الخدمة تستلزم موافقة مسبقة (PA) — تأكد من إرفاق رقم الموافقة المسبقة
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
            {line.notCovered && !line.rejected && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.error.main, 0.07) }}>
                    <TableCell colSpan={12} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color="error.main" fontWeight={600} sx={{ fontSize: '0.75rem', px: '1.0rem', display: 'flex', alignItems: 'center', gap: 1 }}>
                            <RejectIcon sx={{ fontSize: '0.875rem' }} />
                            هذه الخدمة غير مغطاة بالوثيقة (تغطية 0%) — يتحمّل المريض كامل المبلغ أو يجب رفض البند
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
        </Fragment>
    );
};





