import React, { Fragment } from 'react';
import {
    TableRow, TableCell, Stack, Autocomplete, TextField, Chip,
    Tooltip, Typography, IconButton, alpha
} from '@mui/material';
import {
    Block as RejectIcon,
    Delete as DeleteIcon,
    WarningAmber as WarningIcon
} from '@mui/icons-material';

const inlineSx = {
    '& .MuiInputBase-root': { fontSize: '0.85rem', fontWeight: 700 },
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
    policyInfo
}) => {
    return (
        <Fragment>
            <TableRow sx={{ 
                bgcolor: line.rejected ? alpha(theme.palette.error.main, 0.05) : 
                        (line.usageExceeded ? alpha(theme.palette.warning.main, 0.02) : 'transparent')
            }}>
                <TableCell align="center" sx={{ fontWeight: 900, color: 'text.secondary', width: 40 }}>{idx + 1}</TableCell>
                <TableCell align="right" sx={{ minWidth: 280 }}>
                    <Stack spacing={0.5}>
                        <Autocomplete
                            size="small"
                            options={serviceOptions}
                            loading={loadingServices}
                            value={line.service || null}
                            onChange={(_, val) => handleServiceChange(idx, val)} // Use complex handler for price/coverage
                            getOptionLabel={o => o.label || o.serviceName || ''}
                            renderInput={(params) => (
                                <TextField {...params} variant="standard" 
                                    placeholder={loadingServices ? "جاري التحميل..." : "ابحث عن خدمة..."}
                                    inputProps={{ ...params.inputProps, style: { textAlign: 'right' } }}
                                />
                            )}
                            noOptionsText={loadingServices ? "جاري تحميل خدمات العقد..." : "لم يتم العثور على خدمات في العقد"}
                        />
                        {line.service && !line.service.medicalServiceId && line.service.pricingItemId && (
                            <Chip label="عقد مباشر" size="small" color="info" variant="outlined" sx={{ height: 16, fontSize: '0.65rem', fontWeight: 700 }} />
                        )}
                    </Stack>
                </TableCell>
                <TableCell align="center">
                    <TextField variant="standard" type="number" value={line.quantity}
                        onChange={e => updateLine(idx, { quantity: e.target.value })} sx={inlineSx} />
                </TableCell>
                <TableCell align="center">
                    <Tooltip title={line.contractPrice > 0 && line.unitPrice > line.contractPrice ? `السعر يتجاوز العقد (${line.contractPrice})` : ''} arrow>
                        <TextField variant="standard" type="number" value={line.unitPrice}
                            onChange={e => updateLine(idx, { unitPrice: e.target.value })}
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
                <TableCell align="center">
                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 700, color: 'text.secondary' }}>
                        {line.coveragePercent !== null ? `${line.coveragePercent}%` : `${policyInfo?.defaultCoveragePercent ?? 100}%`}
                    </Typography>
                </TableCell>
                <TableCell align="center">
                    {line.usageDetails && (
                        <Stack spacing={0.3} alignItems="center" justifyContent="center">
                            {line.usageDetails.timesLimit > 0 && (
                                <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.timesExceeded || line.usageDetails.totalUsedCount > line.usageDetails.timesLimit ? 'error.main' : 'text.secondary', fontWeight: 900, whiteSpace: 'nowrap' }}>
                                    مرات: {line.usageDetails.totalUsedCount}/{line.usageDetails.timesLimit}
                                </Typography>
                            )}
                            {line.usageDetails.amountLimit > 0 && (
                                <Typography variant="caption" sx={{ fontSize: '0.75rem', color: line.usageDetails.amountExceeded || line.usageDetails.totalUsedAmount > line.usageDetails.amountLimit ? 'error.main' : 'text.secondary', fontWeight: 900, whiteSpace: 'nowrap' }}>
                                    د.ل: {(line.usageDetails.totalUsedAmount ?? 0).toFixed(2)}/{line.usageDetails.amountLimit}
                                </Typography>
                            )}
                        </Stack>
                    )}
                </TableCell>
                <TableCell align="center">
                    {line.usageDetails && (
                        <Stack spacing={0.3} alignItems="center" justifyContent="center">
                            {line.usageDetails.timesLimit > 0 && (() => {
                                const remaining = Math.max(0, line.usageDetails.timesLimit - line.usageDetails.totalUsedCount);
                                return (
                                    <Typography variant="caption" sx={{
                                        fontSize: '0.75rem',
                                        color: remaining === 0 ? 'error.main' : 'primary.main',
                                        fontWeight: 900, whiteSpace: 'nowrap'
                                    }}>
                                        مرات: {remaining}
                                    </Typography>
                                );
                            })()}
                            {line.usageDetails.amountLimit > 0 && (() => {
                                const remaining = line.usageDetails.remainingAmount != null
                                    ? line.usageDetails.remainingAmount
                                    : Math.max(0, line.usageDetails.amountLimit - (line.usageDetails.totalUsedAmount ?? 0));
                                return (
                                    <Typography variant="caption" sx={{
                                        fontSize: '0.75rem',
                                        color: remaining <= 0 ? 'error.main' : 'primary.main',
                                        fontWeight: 900, whiteSpace: 'nowrap'
                                    }}>
                                        د.ل: {remaining.toFixed(2)}
                                    </Typography>
                                );
                            })()}
                        </Stack>
                    )}
                </TableCell>
                <TableCell align="center">
                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 800, color: 'error.main' }}>
                        {(line.rejected ? line.total : line.refusedAmount)?.toFixed(2)}
                    </Typography>
                </TableCell>
                <TableCell align="center">
                    <Stack spacing={0} alignItems="center">
                        <Typography variant="caption" sx={{ fontSize: '0.8rem', fontWeight: 900, color: 'success.main', lineHeight: 1.2 }}>
                            {line.byCompany?.toFixed(2)}
                        </Typography>
                        <Typography variant="caption" sx={{ fontSize: '0.75rem', fontWeight: 900, color: 'warning.dark', lineHeight: 1.2 }}>
                            {line.byEmployee?.toFixed(2)}
                        </Typography>
                    </Stack>
                </TableCell>
                <TableCell align="center">
                    <Typography variant="body2" sx={{ fontSize: '0.85rem', fontWeight: 900, color: 'primary.main' }}>
                        {line.total?.toFixed(2)}
                    </Typography>
                </TableCell>
                <TableCell align="left">
                    <Stack direction="row" spacing={0} justifyContent="flex-start" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
                        <IconButton size="small" color={line.rejected ? "error" : "default"}
                            onClick={() => line.rejected ? updateLine(idx, { rejected: false }) : openRejectDialog('line', idx)}>
                            <RejectIcon sx={{ fontSize: 15 }} />
                        </IconButton>
                        <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
                            <DeleteIcon sx={{ fontSize: 15 }} />
                        </IconButton>
                    </Stack>
                </TableCell>
            </TableRow>
            {line.rejected && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.error.main, 0.02) }}>
                    <TableCell colSpan={11} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color="error" fontWeight={800} sx={{ fontSize: '0.75rem', px: 2 }}>
                            سبب الرفض: {line.rejectionReason}
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
            {line.usageExceeded && !line.rejected && (
                <TableRow sx={{ bgcolor: alpha(theme.palette.warning.main, 0.05) }}>
                    <TableCell colSpan={11} sx={{ py: 0.5 }}>
                        <Typography variant="caption" color={line.usageExhausted ? "error.main" : "warning.dark"} fontWeight={900} sx={{ fontSize: '0.75rem', px: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                            {line.usageExhausted ? <RejectIcon sx={{ fontSize: 14 }} /> : <WarningIcon sx={{ fontSize: 14 }} />}
                            {line.usageExhausted ? "⚠️ رصيد المنفعة استنفذ بالكامل: " : "⚠️ تجاوز سقف المنفعة المحدد: "}
                            {line.usageDetails?.timesLimit > 0 && `(تم استهلاك ${line.usageDetails.usedCount} من ${line.usageDetails.timesLimit} مرّة)`}
                            {line.usageDetails?.amountLimit > 0 && ` (تم استهلاك ${line.usageDetails.usedAmount} من ${line.usageDetails.amountLimit} ريال)`}
                        </Typography>
                    </TableCell>
                </TableRow>
            )}
        </Fragment>
    );
};
