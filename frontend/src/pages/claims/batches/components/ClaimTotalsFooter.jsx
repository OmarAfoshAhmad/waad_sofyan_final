import React from 'react';
import { Box, Button, Typography, alpha, Tooltip } from '@mui/material';
import { Block as RejectIcon, WarningAmber as WarnIcon } from '@mui/icons-material';

export const ClaimTotalsFooter = ({
    isClaimRejected,
    handleSave,
    saving,
    isDirty,
    setIsClaimRejected,
    setIsDirty,
    setRejectionInput,
    openRejectDialog,
    totals,
    theme,
    lines,
    t,
    visibleColumns
}) => {
    // اكتشاف أن جميع البنود مرفوضة
    const activeLines = (lines || []).filter(l => l.service || l.serviceName);
    const allLinesRejected = activeLines.length > 0 && activeLines.every(l => l.rejected);
    const showRejected = isClaimRejected || allLinesRejected;
    const netApproved = totals.total - totals.refused;

    return (
        <Box sx={{
            flexShrink: 0, px: '1.25rem', py: '0.75rem',
            borderTop: `2px solid ${showRejected ? theme.palette.error.light : theme.palette.divider}`,
            display: 'flex', gap: '1.0rem', alignItems: 'center',
            bgcolor: showRejected ? alpha(theme.palette.error.main, 0.04) : alpha(theme.palette.primary.main, 0.02)
        }}>
            <Button variant="contained" color={showRejected ? "error" : "primary"}
                onClick={handleSave} disabled={saving || !isDirty} sx={{ px: '2.0rem', fontWeight: 600 }}>
                {saving ? t('claimEntry.saving') : (showRejected ? "حفظ (مرفوضة)" : t('claimEntry.saveAndAdd'))}
            </Button>

            {!isClaimRejected && !allLinesRejected ? (
                <Button variant="outlined" color="error" startIcon={<RejectIcon />}
                    onClick={() => openRejectDialog('claim')} sx={{ fontWeight: 500 }}>
                    رفض المطالبة
                </Button>
            ) : isClaimRejected ? (
                <Button variant="text" onClick={() => { setIsClaimRejected(false); setIsDirty?.(true); if (typeof setRejectionInput === 'function') setRejectionInput(''); }} sx={{ fontWeight: 500 }}>
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

            <Box sx={{ mr: 'auto', display: 'flex', gap: '2.0rem', alignItems: 'flex-start' }}>
                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>الإجمالي</Typography>
                    <Typography variant="subtitle2" fontWeight={800} color="text.primary" sx={{ fontSize: '1.15rem' }}>{totals.total.toFixed(2)}</Typography>
                </Box>

                {totals.refused > 0 && (
                    <Box sx={{ textAlign: 'center' }}>
                        <Typography variant="caption" display="block" color="error.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>المرفوض</Typography>
                        <Typography variant="subtitle2" fontWeight={800} color="error.main" sx={{ fontSize: '1.15rem' }}>{totals.refused.toFixed(2)}</Typography>
                    </Box>
                )}

                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="primary.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>الصافي</Typography>
                    <Typography variant="subtitle2" fontWeight={800} color={netApproved > 0 ? 'primary.main' : 'text.disabled'} sx={{ fontSize: '1.15rem' }}>{netApproved.toFixed(2)}</Typography>
                </Box>

                {visibleColumns.companyShare && (
                    <Box sx={{ textAlign: 'center' }}>
                        <Typography variant="caption" display="block" color="success.main" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>التزام الشركة</Typography>
                        <Typography variant="subtitle2" fontWeight={800} color="success.main" sx={{ fontSize: '1.15rem' }}>{totals.company.toFixed(2)}</Typography>
                    </Box>
                )}
                {visibleColumns.patientShare && (
                    <Box sx={{ textAlign: 'center' }}>
                        <Typography variant="caption" display="block" color="warning.dark" sx={{ fontSize: '0.8rem', fontWeight: 700 }}>التزام المشترك</Typography>
                        <Typography variant="subtitle2" fontWeight={800} color="warning.dark" sx={{ fontSize: '1.15rem' }}>{totals.employee.toFixed(2)}</Typography>
                    </Box>
                )}
            </Box>
        </Box>
    );
};




