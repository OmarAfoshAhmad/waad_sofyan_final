import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';

// material-ui
import {
    Box,
    Stack,
    Typography,
    TextField,
    Button,
    Chip,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
    InputAdornment,
    Tooltip,
    IconButton,
    LinearProgress,
    CardContent,
    Checkbox,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Alert
} from '@mui/material';
import { LoadingButton } from '@mui/lab';

// project imports
import MainCard from 'components/MainCard';
import auditService from 'services/api/audit.service';
import { useTableState } from 'hooks/useTableState';
import { useSnackbar } from 'notistack';

// assets
import {
    Search as SearchIcon,
    FilterAlt as FilterAltIcon,
    Download as DownloadIcon,
    Refresh as RefreshIcon,
    Info as InfoIcon,
    History as HistoryIcon,
    Delete as DeleteIcon,
    Lock as LockIcon
} from '@mui/icons-material';

// ==============================|| MEDICAL AUDIT LOGS PAGE ||============================== //

const MedicalAuditLogs = () => {
    const { enqueueSnackbar } = useSnackbar();
    const tableState = useTableState({ initialPageSize: 20 });
    const [claimId, setClaimId] = useState('');
    const [correlationId, setCorrelationId] = useState('');
    const [selectedIds, setSelectedIds] = useState([]);

    // Deletion Password Dialog State
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [deletePassword, setDeletePassword] = useState('');

    const { data: logData, isPending: isLoading, refetch, isFetching } = useQuery({
        queryKey: ['medical-audit-logs', tableState.page, tableState.pageSize, claimId, correlationId],
        queryFn: () => auditService.search({
            page: tableState.page + 1,
            size: tableState.pageSize,
            claimId: claimId || undefined,
            correlationId: correlationId || undefined
        })
    });

    const deleteMutation = useMutation({
        mutationFn: (data) => auditService.deleteBulk(data),
        onSuccess: () => {
            enqueueSnackbar('تم حذف السجلات المحددة بنجاح', { variant: 'success' });
            setSelectedIds([]);
            setDeleteDialogOpen(false);
            setDeletePassword('');
            refetch();
        },
        onError: (err) => {
            enqueueSnackbar(err?.response?.data?.messageAr || err?.message || 'حدث خطأ أثناء الحذف', { variant: 'error' });
        }
    });

    const handleSelectAll = (event) => {
        if (event.target.checked) {
            const allIds = logs.map((log) => log.id);
            setSelectedIds(allIds);
        } else {
            setSelectedIds([]);
        }
    };

    const handleSelectRow = (id) => {
        const selectedIndex = selectedIds.indexOf(id);
        let newSelected = [];

        if (selectedIndex === -1) {
            newSelected = [...selectedIds, id];
        } else {
            newSelected = selectedIds.filter((sid) => sid !== id);
        }

        setSelectedIds(newSelected);
    };

    const handleExport = async () => {
        try {
            const blob = await auditService.exportXlsx({ claimId, correlationId });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `medical_audit_logs_${new Date().toISOString().split('T')[0]}.xlsx`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Export failed', err);
        }
    };

    const confirmDelete = () => {
        if (!deletePassword) {
            enqueueSnackbar('يجب إدخال كلمة المرور لتأكيد الحذف', { variant: 'warning' });
            return;
        }
        deleteMutation.mutate({ ids: selectedIds, password: deletePassword });
    };

    const getActionColor = (action) => {
        if (action.includes('VOID') || action.includes('DELETE')) return 'error';
        if (action.includes('APPROVE')) return 'success';
        if (action.includes('CREATE')) return 'primary';
        if (action.includes('REJECT')) return 'warning';
        return 'default';
    };

    const logs = logData?.items || [];
    const totalCount = logData?.total || 0;

    return (
        <Stack spacing={3}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="h4" sx={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 1 }}>
                    <HistoryIcon color="primary" /> سجل التدقيق الطبي (Audit Trail)
                </Typography>
                <Stack direction="row" spacing={1}>
                    {selectedIds.length > 0 && (
                        <Button
                            variant="contained"
                            color="error"
                            startIcon={<DeleteIcon />}
                            onClick={() => setDeleteDialogOpen(true)}
                        >
                            حذف ({selectedIds.length})
                        </Button>
                    )}
                    <Button
                        variant="outlined"
                        startIcon={<RefreshIcon />}
                        onClick={() => refetch()}
                        disabled={isFetching}
                    >
                        تحديث
                    </Button>
                    <Button
                        variant="contained"
                        color="success"
                        startIcon={<DownloadIcon />}
                        onClick={handleExport}
                    >
                        تصدير Excel
                    </Button>
                </Stack>
            </Box>

            <MainCard>
                <CardContent sx={{ p: 2 }}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <TextField
                            label="رقم المطالبة (ID)"
                            size="small"
                            value={claimId}
                            onChange={(e) => setClaimId(e.target.value)}
                            sx={{ width: 200 }}
                        />
                        <TextField
                            label="معرف الارتباط (Correlation ID)"
                            size="small"
                            value={correlationId}
                            onChange={(e) => setCorrelationId(e.target.value)}
                            sx={{ flexGrow: 1 }}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <SearchIcon color="disabled" />
                                    </InputAdornment>
                                )
                            }}
                        />
                        <Button
                            variant="contained"
                            startIcon={<FilterAltIcon />}
                            onClick={() => tableState.setPage(0)}
                        >
                            تصفية
                        </Button>
                    </Stack>
                </CardContent>
            </MainCard>

            <TableContainer component={Paper} sx={{ borderRadius: 2, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
                {isFetching && <LinearProgress sx={{ height: 2 }} />}
                <Table sx={{ minWidth: 650, direction: 'rtl' }}>
                    <TableHead sx={{ bgcolor: 'grey.50' }}>
                        <TableRow>
                            <TableCell padding="checkbox">
                                <Checkbox
                                    indeterminate={selectedIds.length > 0 && selectedIds.length < logs.length}
                                    checked={logs.length > 0 && selectedIds.length === logs.length}
                                    onChange={handleSelectAll}
                                />
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>التاريخ والوقت</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>الإجراء</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>الكيان</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>المعرف</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>المستخدم</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>الدور</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 'bold' }}>السبب</TableCell>
                            <TableCell align="center" sx={{ fontWeight: 'bold' }}>التفاصيل</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={10} align="center" sx={{ py: 5 }}>
                                    جاري تحميل سجلات التدقيق...
                                </TableCell>
                            </TableRow>
                        ) : logs.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={10} align="center" sx={{ py: 10 }}>
                                    <Typography color="text.secondary">لا توجد سجلات تدقيق مطابقة للبحث</Typography>
                                </TableCell>
                            </TableRow>
                        ) : (
                            logs.map((log) => {
                                const isItemSelected = selectedIds.indexOf(log.id) !== -1;
                                return (
                                    <TableRow
                                        key={log.id}
                                        hover
                                        selected={isItemSelected}
                                        onClick={() => handleSelectRow(log.id)}
                                        sx={{ cursor: 'pointer' }}
                                    >
                                        <TableCell padding="checkbox">
                                            <Checkbox checked={isItemSelected} />
                                        </TableCell>
                                        <TableCell align="right" dir="ltr" sx={{ fontSize: '0.8rem' }}>
                                            {new Date(log.timestamp).toLocaleString('ar-LY')}
                                        </TableCell>
                                        <TableCell align="right">
                                            <Chip
                                                label={log.action}
                                                size="small"
                                                color={getActionColor(log.action)}
                                                variant="tonal"
                                                sx={{ fontWeight: 'bold' }}
                                            />
                                        </TableCell>
                                        <TableCell align="right">{log.entityType}</TableCell>
                                        <TableCell align="right" sx={{ fontWeight: 'bold' }}>{log.entityId}</TableCell>
                                        <TableCell align="right">{log.userId === 0 ? 'النظام' : `مستخدم #${log.userId}`}</TableCell>
                                        <TableCell align="right">
                                            <Typography variant="caption" sx={{ bgcolor: 'grey.200', px: 1, borderRadius: 1 }}>
                                                {log.role}
                                            </Typography>
                                        </TableCell>
                                        <TableCell align="right" sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {log.reason || '—'}
                                        </TableCell>
                                        <TableCell align="center">
                                            <Tooltip title="عرض التفاصيل التقنية">
                                                <IconButton
                                                    size="small"
                                                    color="info"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        console.log(log);
                                                    }}
                                                >
                                                    <InfoIcon fontSize="small" />
                                                </IconButton>
                                            </Tooltip>
                                        </TableCell>
                                    </TableRow>
                                );
                            })
                        )}
                    </TableBody>
                </Table>
                <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
                    <Typography variant="caption" color="text.secondary">
                        إجمالي السجلات: {totalCount}
                    </Typography>
                </Box>
            </TableContainer>

            {/* Password Confirmation Dialog */}
            <Dialog
                open={deleteDialogOpen}
                onClose={() => setDeleteDialogOpen(false)}
                maxWidth="xs"
                fullWidth
            >
                <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'error.main' }}>
                    <DeleteIcon /> تأكيد حذف سجلات التدقيق
                </DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ mt: 1 }}>
                        <Alert severity="warning">
                            سيتم حذف {selectedIds.length} سجلات نهائياً من النظام. هذا الإجراء غير قابل للتراجع.
                        </Alert>
                        <Typography variant="body2" color="text.secondary">
                            يرجى إدخال كلمة مرورك للمتابعة:
                        </Typography>
                        <TextField
                            fullWidth
                            type="password"
                            label="كلمة المرور"
                            value={deletePassword}
                            onChange={(e) => setDeletePassword(e.target.value)}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <LockIcon fontSize="small" />
                                    </InputAdornment>
                                )
                            }}
                            autoFocus
                        />
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ p: 2, pt: 0 }}>
                    <Button onClick={() => setDeleteDialogOpen(false)} color="inherit">
                        إلغاء
                    </Button>
                    <LoadingButton
                        onClick={confirmDelete}
                        variant="contained"
                        color="error"
                        loading={deleteMutation.isPending}
                    >
                        حذف نهائي
                    </LoadingButton>
                </DialogActions>
            </Dialog>
        </Stack>
    );
};

export default MedicalAuditLogs;
