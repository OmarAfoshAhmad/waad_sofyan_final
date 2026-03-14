import React from 'react';
import { Box, Button, Typography, alpha } from '@mui/material';
import { Block as RejectIcon } from '@mui/icons-material';

export const ClaimTotalsFooter = ({
    isClaimRejected,
    handleSave,
    saving,
    isDirty,
    setIsClaimRejected,
    openRejectDialog,
    totals,
    theme,
    t
}) => {
    return (
        <Box sx={{ 
            flexShrink: 0, px: '1.25rem', py: '0.75rem', 
            borderTop: `1px solid ${theme.palette.divider}`, 
            display: 'flex', gap: '1.0rem', alignItems: 'center', 
            bgcolor: alpha(theme.palette.primary.main, 0.02) 
        }}>
            <Button variant="contained" color={isClaimRejected ? "error" : "primary"}
                onClick={handleSave} disabled={saving || !isDirty} sx={{ px: '2.0rem', fontWeight: 600 }}>
                {saving ? t('claimEntry.saving') : (isClaimRejected ? "حفظ (مرفوض)" : t('claimEntry.saveAndAdd'))}
            </Button>

            {!isClaimRejected ? (
                <Button variant="outlined" color="error" startIcon={<RejectIcon />}
                    onClick={() => openRejectDialog('claim')} sx={{ fontWeight: 500 }}>
                    رفض المطالبة
                </Button>
            ) : (
                <Button variant="text" onClick={() => setIsClaimRejected(false)} sx={{ fontWeight: 500 }}>
                    تغيير للقبول
                </Button>
            )}

            <Box sx={{ mr: 'auto', display: 'flex', gap: '2.0rem' }}>
                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>حصة الشركة</Typography>
                    <Typography variant="subtitle2" fontWeight={600} color="success.main" sx={{ fontSize: '0.9rem' }}>{totals.company.toFixed(2)}</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>حصة المشترك</Typography>
                    <Typography variant="subtitle2" fontWeight={600} color="warning.dark" sx={{ fontSize: '0.9rem' }}>{totals.employee.toFixed(2)}</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>المرفوضات</Typography>
                    <Typography variant="subtitle2" fontWeight={600} color="error.main" sx={{ fontSize: '0.9rem' }}>{totals.refused.toFixed(2)}</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                    <Typography variant="caption" display="block" color="text.secondary" sx={{ fontSize: '0.8rem' }}>الإجمالي</Typography>
                    <Typography variant="h6" fontWeight={600} color="primary.main" sx={{ fontSize: '1.1rem' }}>{totals.total.toFixed(2)}</Typography>
                </Box>
            </Box>
        </Box>
    );
};




