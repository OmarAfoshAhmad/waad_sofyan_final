import React from 'react';
import {
    Box, Paper, Stack, Typography, Button, Divider, CircularProgress,
    IconButton, Tooltip, Pagination, alpha
} from '@mui/material';
import {
    History as HistoryIcon,
    Add as AddIcon,
    Delete as DeleteIcon,
    Receipt as ReceiptIcon
} from '@mui/icons-material';

export const BatchHistorySidebar = ({
    loadingBatch,
    batchContent,
    editingClaimId,
    setEditingClaimId,
    handleDeleteClaim,
    resetForm,
    batchData,
    page,
    setPage,
    monthLabel,
    year,
    theme,
    navigate,
    detailUrl,
    t
}) => {
    return (
        <Box sx={{ width: 280, flexShrink: 0, display: 'flex', flexDirection: 'column', order: -1 }}>
            <Paper variant="outlined" sx={{
                flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                borderRadius: 2.5, p: 1.5,
                boxShadow: '0 2px 8px rgba(0,0,0,0.04)'
            }}>
                <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mb: 1.5 }}>
                    <HistoryIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    <Typography variant="subtitle2" fontWeight={900} sx={{ fontSize: '0.9rem', flex: 1 }}>
                        {t('claimEntry.batchHistory')}
                    </Typography>
                    <Button size="small" variant="contained" color="primary" startIcon={<AddIcon sx={{ fontSize: 13 }} />}
                        onClick={() => { resetForm(); setEditingClaimId(null); }}
                        sx={{ height: 24, fontSize: '0.75rem', fontWeight: 800, borderRadius: 1.2 }}>
                        جديد
                    </Button>
                </Stack>
                <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.65rem', mb: 1, display: 'block' }}>
                    {monthLabel} {year}
                </Typography>
                <Divider sx={{ mb: 1 }} />

                {loadingBatch ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                        <CircularProgress size={20} thickness={4} />
                    </Box>
                ) : (
                    <Stack spacing={0.5} sx={{ flex: 1, overflowY: 'auto' }}>
                        {batchContent.map(c => (
                            <Paper key={c.id} variant="outlined"
                                onClick={() => setEditingClaimId(c.id)}
                                sx={{
                                    p: 0.75, borderRadius: 1.5, cursor: 'pointer', flexShrink: 0,
                                    transition: 'all 0.15s',
                                    bgcolor: editingClaimId === c.id ? alpha(theme.palette.primary.main, 0.08) : 'transparent',
                                    borderColor: editingClaimId === c.id ? 'primary.main' : 'divider',
                                    '&:hover': {
                                        bgcolor: alpha(theme.palette.primary.main, 0.05),
                                        borderColor: 'primary.light',
                                        transform: 'translateX(2px)'
                                    }
                                }}>
                                <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={0.5}>
                                    <Typography variant="caption" fontWeight={800}
                                        sx={{ fontSize: '0.75rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {c.memberName}
                                    </Typography>
                                    <Stack direction="row" alignItems="center" spacing={0.25}>
                                        <Typography variant="caption" fontWeight={900} sx={{
                                            color: 'success.dark', bgcolor: alpha('#2e7d32', 0.09),
                                            px: 0.7, py: 0.3, borderRadius: 1, fontSize: '0.85rem'
                                        }}>
                                            {(c.approvedAmount || 0).toFixed(2)}
                                        </Typography>
                                        {(c.refusedAmount > 0) && (
                                            <Typography variant="caption" fontWeight={900} sx={{
                                                color: '#d32f2f', bgcolor: alpha('#d32f2f', 0.09),
                                                px: 0.7, py: 0.3, borderRadius: 1, fontSize: '0.85rem'
                                            }}>
                                                {(c.refusedAmount || 0).toFixed(2)}
                                            </Typography>
                                        )}
                                        <Tooltip title="إلغاء المطالبة">
                                            <IconButton size="small" color="error"
                                                onClick={(e) => handleDeleteClaim(c.id, e)}
                                                sx={{ p: 0.25, opacity: 0.5, '&:hover': { opacity: 1 } }}>
                                                <DeleteIcon sx={{ fontSize: 11 }} />
                                            </IconButton>
                                        </Tooltip>
                                    </Stack>
                                </Stack>
                                <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.3 }}>
                                    <Typography variant="caption" color="text.disabled"
                                        sx={{ fontFamily: 'monospace', fontSize: '0.6rem' }}>
                                        #{c.id}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.6rem' }}>
                                        {c.serviceDate}
                                    </Typography>
                                </Stack>
                            </Paper>
                        ))}
                        {batchData?.totalPages > 1 && (
                            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1, py: 1, borderTop: '1px solid #eee', mb: 1 }}>
                                <Pagination
                                    count={batchData.totalPages}
                                    page={page + 1}
                                    onChange={(e, v) => setPage(v - 1)}
                                    size="small"
                                    siblingCount={0}
                                    boundaryCount={1}
                                    shape="rounded"
                                    color="primary"
                                />
                            </Box>
                        )}
                        {!batchContent.length && (
                            <Box sx={{ textAlign: 'center', py: 3, opacity: 0.3 }}>
                                <ReceiptIcon sx={{ fontSize: 28, mb: 0.5 }} />
                                <Typography variant="caption" display="block" fontWeight={700} sx={{ fontSize: '0.75rem' }}>
                                    {t('claimEntry.noHistoryYet')}
                                </Typography>
                            </Box>
                        )}
                    </Stack>
                )}

                <Divider sx={{ mt: 1, mb: 1 }} />
                <Button fullWidth variant="text" size="small" color="primary"
                    onClick={() => navigate(detailUrl)} sx={{ fontWeight: 800, fontSize: '0.75rem' }}>
                    {t('claimEntry.viewAllBatch')}
                </Button>
            </Paper>
        </Box>
    );
};
