import { useMemo, useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';

import PaymentsIcon from '@mui/icons-material/Payments';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import RefreshIcon from '@mui/icons-material/Refresh';

import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import PermissionGuard from 'components/PermissionGuard';
import { UnifiedMedicalTable } from 'components/common';

import { providerPaymentsService } from 'services/api/settlement.service';
import { openSnackbar } from 'api/snackbar';

const formatCurrency = (value) => {
  if (value === null || value === undefined) return '0 د.ل';
  const num = typeof value === 'string' ? parseFloat(value) : Number(value);
  if (!Number.isFinite(num)) return '0 د.ل';
  return `${num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} د.ل`;
};

const formatDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('ar-LY');
};

const PaymentCenter = () => {
  const queryClient = useQueryClient();

  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 20 });
  const [selectedBatch, setSelectedBatch] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [paymentReference, setPaymentReference] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('BANK_TRANSFER');
  const [notes, setNotes] = useState('');

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['payment-center-confirmed-batches', paginationModel.page, paginationModel.pageSize],
    queryFn: () =>
      providerPaymentsService.getConfirmedBatches({
        page: paginationModel.page,
        size: paginationModel.pageSize
      }),
    staleTime: 0
  });

  const confirmedBatches = useMemo(() => {
    const rows = Array.isArray(data) ? data : data?.batches || data?.content || data?.items || [];
    return rows.map((row, index) => ({
      id: row.batchId || row.id || `batch-${index}`,
      batchNumber: row.batchNumber,
      providerName: row.providerName,
      totalNetAmount: Number(row.totalNetAmount || 0),
      confirmedAt: row.confirmedAt || row.createdAt,
      claimCount: Number(row.claimCount || 0)
    }));
  }, [data]);

  const recordPaymentMutation = useMutation({
    mutationFn: ({ batchId, payload }) => providerPaymentsService.createPayment(batchId, payload),
    onSuccess: () => {
      setDialogOpen(false);
      setSelectedBatch(null);
      setPaymentReference('');
      setPaymentMethod('BANK_TRANSFER');
      setNotes('');
      queryClient.invalidateQueries(['payment-center-confirmed-batches']);
      queryClient.invalidateQueries(['settlement-batches']);
      // Also invalidate provider account cache so ProviderAccountView shows updated balance/transactions.
      queryClient.invalidateQueries(['provider-account']);
      openSnackbar({ message: 'تم تسجيل الدفع بنجاح', variant: 'success' });
    },
    onError: (mutationError) => {
      openSnackbar({
        message: mutationError.message || 'فشل تسجيل الدفع',
        variant: 'error'
      });
    }
  });

  const openRecordDialog = useCallback((batch) => {
    setSelectedBatch(batch);
    setPaymentReference('');
    setPaymentMethod('BANK_TRANSFER');
    setNotes('');
    setDialogOpen(true);
  }, []);

  const handleOpenConfirm = useCallback(() => {
    if (!selectedBatch) return;
    const reference = String(paymentReference || '').trim();
    if (!reference) {
      openSnackbar({ message: 'مرجع الدفع مطلوب', variant: 'warning' });
      return;
    }
    setConfirmOpen(true);
  }, [selectedBatch, paymentReference]);

  const handleConfirmPayment = useCallback(() => {
    if (!selectedBatch) return;
    setConfirmOpen(false);
    setDialogOpen(false);
    recordPaymentMutation.mutate({
      batchId: selectedBatch.id,
      payload: {
        amount: selectedBatch.totalNetAmount,
        paymentReference: String(paymentReference || '').trim(),
        paymentMethod,
        notes
      }
    });
  }, [selectedBatch, paymentReference, paymentMethod, notes, recordPaymentMutation]);

  const columns = useMemo(
    () => [
      { id: 'batchNumber', label: 'رقم الدفعة', minWidth: 160 },
      { id: 'providerName', label: 'مقدم الخدمة', minWidth: 220 },
      { id: 'claimCount', label: 'عدد المطالبات', minWidth: 130, align: 'center' },
      { id: 'totalNetAmount', label: 'الإجمالي', minWidth: 150, align: 'right' },
      { id: 'confirmedAt', label: 'تاريخ التأكيد', minWidth: 180 },
      { id: 'actions', label: 'الإجراء', minWidth: 170, align: 'center' }
    ],
    []
  );

  const renderCell = useCallback(
    (row, column) => {
      switch (column.id) {
        case 'batchNumber':
          return <Typography fontWeight={600}>{row.batchNumber || '-'}</Typography>;
        case 'providerName':
          return <Typography>{row.providerName || '-'}</Typography>;
        case 'claimCount':
          return <Chip size="small" label={row.claimCount || 0} color="info" variant="outlined" />;
        case 'totalNetAmount':
          return (
            <Typography color="success.main" fontWeight={700}>
              {formatCurrency(row.totalNetAmount)}
            </Typography>
          );
        case 'confirmedAt':
          return <Typography variant="body2">{formatDateTime(row.confirmedAt)}</Typography>;
        case 'actions':
          return (
            <Button variant="contained" color="success" size="small" onClick={() => openRecordDialog(row)}>
              تسجيل الدفع
            </Button>
          );
        default:
          return null;
      }
    },
    [openRecordDialog]
  );

  const breadcrumbs = [
    { label: 'الرئيسية', path: '/' },
    { label: 'التسويات', path: '/settlement' },
    { label: 'مركز الدفع' }
  ];

  const actions = (
    <Stack direction="row" spacing={1}>
      <Tooltip title="تحديث">
        <IconButton onClick={refetch} color="primary">
          <RefreshIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  );

  return (
    <PermissionGuard resource="settlements" action="pay" fallback={<Alert severity="error">ليس لديك صلاحية الدفع</Alert>}>
      <Box>
        <UnifiedPageHeader
          title="مركز الدفع"
          subtitle="تسجيل الدفع من مكان واحد للدفعات المؤكدة"
          icon={PaymentsIcon}
          breadcrumbs={breadcrumbs}
          actions={actions}
        />

        {isError && <Alert severity="error" sx={{ mb: 2 }}>{error?.message || 'فشل تحميل الدفعات المؤكدة'}</Alert>}

        <MainCard title="الدفعات المؤكدة الجاهزة للدفع" secondary={<ReceiptLongIcon color="primary" />}>
          <UnifiedMedicalTable
            columns={columns}
            data={confirmedBatches}
            loading={isLoading}
            error={isError ? error : null}
            onErrorClose={() => {}}
            renderCell={renderCell}
            totalItems={data?.totalElements || confirmedBatches.length}
            page={paginationModel.page}
            rowsPerPage={paginationModel.pageSize}
            onPageChange={(event, newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
            onRowsPerPageChange={(event) =>
              setPaginationModel({
                page: 0,
                pageSize: parseInt(event.target.value, 10)
              })
            }
            emptyStateConfig={{
              icon: PaymentsIcon,
              title: 'لا توجد دفعات مؤكدة',
              description: 'لا توجد دفعات بانتظار تسجيل الدفع'
            }}
          />
        </MainCard>

        <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle>تسجيل دفع الدفعة</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Typography><strong>رقم الدفعة:</strong> {selectedBatch?.batchNumber || '-'}</Typography>
              <Typography><strong>مقدم الخدمة:</strong> {selectedBatch?.providerName || '-'}</Typography>
              <Typography color="success.main" fontWeight={700}>
                <strong>المبلغ:</strong> {formatCurrency(selectedBatch?.totalNetAmount || 0)}
              </Typography>

              <TextField
                label="مرجع الدفع"
                value={paymentReference}
                onChange={(e) => setPaymentReference(e.target.value)}
                required
                fullWidth
              />
              <TextField
                label="طريقة الدفع"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value)}
                fullWidth
              />
              <TextField
                label="ملاحظات"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                multiline
                rows={3}
                fullWidth
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={recordPaymentMutation.isPending}>إلغاء</Button>
            <Button
              variant="contained"
              color="success"
              onClick={handleOpenConfirm}
              disabled={recordPaymentMutation.isPending || !String(paymentReference || '').trim()}
            >
              التالي: تأكيد الدفع
            </Button>
          </DialogActions>
        </Dialog>

        {/* Irreversible action confirmation */}
        <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} maxWidth="xs" fullWidth>
          <DialogTitle sx={{ color: 'error.main' }}>تأكيد نهائي — لا يمكن التراجع</DialogTitle>
          <DialogContent>
            <Stack spacing={1} sx={{ mt: 1 }}>
              <Alert severity="warning">هذا الإجراء نهائي ولا يمكن إلغاؤه بعد التأكيد.</Alert>
              <Typography><strong>الدفعة:</strong> {selectedBatch?.batchNumber}</Typography>
              <Typography><strong>المرفق:</strong> {selectedBatch?.providerName}</Typography>
              <Typography><strong>المبلغ:</strong> {formatCurrency(selectedBatch?.totalNetAmount)}</Typography>
              <Typography><strong>مرجع الدفع:</strong> {String(paymentReference || '').trim()}</Typography>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setConfirmOpen(false)}>إلغاء</Button>
            <Button variant="contained" color="error" onClick={handleConfirmPayment}>
              نعم، تأكيد الدفع نهائياً
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </PermissionGuard>
  );
};

export default PaymentCenter;
