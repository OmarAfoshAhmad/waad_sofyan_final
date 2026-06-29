import React, { useState } from 'react';
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
  CircularProgress,
  Tooltip
} from '@mui/material';
import { Close as CloseIcon, Edit as EditIcon, Delete as DeleteIcon, History as HistoryIcon, Add as AddIcon } from '@mui/icons-material';
import { formatCurrency } from 'utils/currency-formatter';
import paymentsService from 'services/api/payments.service';
import PaymentFormModal from './PaymentFormModal';
import PaymentAuditModal from './PaymentAuditModal';

const PaymentDetailsModal = ({ open, onClose, summary, onPaymentChanged }) => {
  const [formOpen, setFormOpen] = useState(false);
  const [auditOpen, setAuditOpen] = useState(false);
  const [selectedPayment, setSelectedPayment] = useState(null);

  const { data: records, isLoading, refetch } = useQuery({
    queryKey: ['payment-records', summary?.employerId, summary?.providerId, summary?.targetYear, summary?.targetMonth],
    queryFn: () => paymentsService.getPaymentRecords(summary?.employerId, summary?.providerId, summary?.targetYear, summary?.targetMonth),
    enabled: open && !!summary
  });

  const handleAdd = () => {
    setSelectedPayment(null);
    setFormOpen(true);
  };

  const handleEdit = (payment) => {
    setSelectedPayment(payment);
    setFormOpen(true);
  };

  const handleDelete = async (payment) => {
    const reason = window.prompt("يرجى إدخال سبب إلغاء الدفعة:");
    if (reason && reason.trim()) {
      try {
        await paymentsService.deletePayment(payment.id, reason);
        refetch();
        if (onPaymentChanged) onPaymentChanged();
      } catch (err) {
        alert(err?.response?.data?.message || 'حدث خطأ أثناء الإلغاء');
      }
    } else if (reason !== null) {
      alert("السبب إلزامي لإلغاء الدفعة!");
    }
  };

  const handleViewAudit = (payment) => {
    setSelectedPayment(payment);
    setAuditOpen(true);
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">تفاصيل دفعات {summary?.employerName} ({summary?.targetMonth}/{summary?.targetYear})</Typography>
          <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Box sx={{ mb: 2, display: 'flex', gap: 2, justifyContent: 'space-between' }}>
            <Box>
              <Typography variant="body2" color="textSecondary">إجمالي المطالبات:</Typography>
              <Typography variant="subtitle1" fontWeight="bold">{formatCurrency(summary?.totalAmount)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="textSecondary">إجمالي المدفوع:</Typography>
              <Typography variant="subtitle1" fontWeight="bold" color="success.main">{formatCurrency(summary?.paidAmount)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="textSecondary">المتبقي:</Typography>
              <Typography variant="subtitle1" fontWeight="bold" color="error.main">{formatCurrency(summary?.remainingAmount)}</Typography>
            </Box>
            <Button variant="contained" startIcon={<AddIcon />} onClick={handleAdd} size="small">
              إضافة دفعة جديدة
            </Button>
          </Box>

          {isLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}><CircularProgress /></Box>
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead sx={{ bgcolor: 'background.default' }}>
                  <TableRow>
                    <TableCell>تاريخ الدفع</TableCell>
                    <TableCell>المبلغ</TableCell>
                    <TableCell>طريقة الدفع</TableCell>
                    <TableCell>رقم المرجع</TableCell>
                    <TableCell>ملاحظات</TableCell>
                    <TableCell align="center">إجراءات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {records?.length > 0 ? (
                    records.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{dayjs(row.paymentDate).format('YYYY-MM-DD')}</TableCell>
                        <TableCell><Typography fontWeight="bold" color="primary">{formatCurrency(row.amount)}</Typography></TableCell>
                        <TableCell><Chip label={row.paymentMethodLabel} size="small" /></TableCell>
                        <TableCell>{row.referenceNumber || '-'}</TableCell>
                        <TableCell>{row.notes || '-'}</TableCell>
                        <TableCell align="center">
                          <Tooltip title="تعديل">
                            <IconButton size="small" color="primary" onClick={() => handleEdit(row)}><EditIcon fontSize="small" /></IconButton>
                          </Tooltip>
                          <Tooltip title="سجل التعديلات">
                            <IconButton size="small" color="info" onClick={() => handleViewAudit(row)}><HistoryIcon fontSize="small" /></IconButton>
                          </Tooltip>
                          <Tooltip title="إلغاء">
                            <IconButton size="small" color="error" onClick={() => handleDelete(row)}><DeleteIcon fontSize="small" /></IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={6} align="center">لا توجد دفعات مسجلة لهذا الشهر</TableCell>
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

      {formOpen && (
        <PaymentFormModal
          open={formOpen}
          onClose={() => setFormOpen(false)}
          payment={selectedPayment}
          summary={summary}
          onSuccess={() => {
            refetch();
            if (onPaymentChanged) onPaymentChanged();
          }}
        />
      )}

      {auditOpen && selectedPayment && (
        <PaymentAuditModal
          open={auditOpen}
          onClose={() => setAuditOpen(false)}
          paymentId={selectedPayment.id}
        />
      )}
    </>
  );
};

export default PaymentDetailsModal;
