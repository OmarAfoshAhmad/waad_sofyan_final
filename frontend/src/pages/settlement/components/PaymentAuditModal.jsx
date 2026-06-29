import React from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  IconButton,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import paymentsService from 'services/api/payments.service';

const ACTION_LABELS = {
  CREATE: 'إضافة',
  UPDATE: 'تعديل',
  DELETE: 'حذف'
};

const ACTION_COLORS = {
  CREATE: 'success',
  UPDATE: 'warning',
  DELETE: 'error'
};

const PaymentAuditModal = ({ open, onClose, paymentId }) => {
  const { data: logs, isLoading } = useQuery({
    queryKey: ['payment-audit-logs', paymentId],
    queryFn: () => paymentsService.getPaymentAuditLogs(paymentId),
    enabled: open && !!paymentId
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">سجل التعديلات للدفعة #{paymentId}</Typography>
        <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent dividers>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress /></Box>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead sx={{ bgcolor: 'background.default' }}>
                <TableRow>
                  <TableCell>الوقت والتاريخ</TableCell>
                  <TableCell>المستخدم</TableCell>
                  <TableCell>نوع العملية</TableCell>
                  <TableCell>المبلغ القديم</TableCell>
                  <TableCell>المبلغ الجديد</TableCell>
                  <TableCell>السبب</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {logs?.length > 0 ? (
                  logs.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{dayjs(row.timestamp).format('YYYY-MM-DD hh:mm A')}</TableCell>
                      <TableCell>{row.userId}</TableCell>
                      <TableCell>
                        <Chip
                          label={ACTION_LABELS[row.actionType] || row.actionType}
                          size="small"
                          color={ACTION_COLORS[row.actionType] || 'default'}
                        />
                      </TableCell>
                      <TableCell>{row.oldAmount !== null ? formatCurrency(row.oldAmount) : '-'}</TableCell>
                      <TableCell>{row.newAmount !== null ? formatCurrency(row.newAmount) : '-'}</TableCell>
                      <TableCell>{row.reason || '-'}</TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={6} align="center">لا يوجد سجل تعديلات لهذه الدفعة</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="inherit">إغلاق</Button>
      </DialogActions>
    </Dialog>
  );
};

export default PaymentAuditModal;
