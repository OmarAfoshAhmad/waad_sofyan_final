import { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Button,
  Alert,
  CircularProgress,
  Paper,
  Tabs,
  Tab
} from '@mui/material';
import { CheckCircle, Visibility, Edit as EditIcon } from '@mui/icons-material';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField
} from '@mui/material';
import preApprovalsService from 'services/api/pre-approvals.service';
import MainCard from 'components/MainCard';
import { useSnackbar } from 'notistack';
import { useNavigate } from 'react-router-dom';
import useSystemConfig from 'hooks/useSystemConfig';

/**
 * Provider Pre-Authorization Inbox
 *
 * Shows APPROVED and ACKNOWLEDGED pre-authorizations for the logged-in provider.
 * Provider can acknowledge approvals by clicking "تم الاطلاع" button.
 *
 * Business Flow:
 * 1. Pre-auth APPROVED by reviewer
 * 2. Provider sees it in inbox (APPROVED tab)
 * 3. Provider clicks "تم الاطلاع" → status changes to ACKNOWLEDGED
 * 4. Pre-auth moves to ACKNOWLEDGED tab (read-only)
 */
const ProviderPreAuthInbox = () => {
  const { enqueueSnackbar } = useSnackbar();
  const navigate = useNavigate();
  const { flags } = useSystemConfig();

  const [loading, setLoading] = useState(true);
  const [approvedItems, setApprovedItems] = useState([]);
  const [acknowledgedItems, setAcknowledgedItems] = useState([]);
  const [pendingItems, setPendingItems] = useState([]);
  const [infoRequestedItems, setInfoRequestedItems] = useState([]);
  const [processingIds, setProcessingIds] = useState(new Set());
  const [currentTab, setCurrentTab] = useState(0);

  useEffect(() => {
    loadPreAuthorizations();
  }, []);

  const loadPreAuthorizations = async () => {
    setLoading(true);
    try {
      // ✅ Single parallel call: جلب جميع الحالات في آنٍ واحد بدل 4 طلبات متتالية
      const { approved, acknowledged, pending, infoRequested } = await preApprovalsService.getAllInboxStatuses();

      setApprovedItems(approved);
      setAcknowledgedItems(acknowledged);
      setPendingItems(pending);
      setInfoRequestedItems(infoRequested);
    } catch (error) {
      console.error('Failed to load pre-authorizations:', error);
      enqueueSnackbar('فشل تحميل الموافقات المسبقة', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleAcknowledge = async (preAuthId) => {
    setProcessingIds((prev) => new Set(prev).add(preAuthId));

    try {
      await preApprovalsService.acknowledge(preAuthId);

      enqueueSnackbar('تم الاطلاع على الموافقة بنجاح', { variant: 'success' });

      // Reload data to update both tabs
      await loadPreAuthorizations();
    } catch (error) {
      console.error('Failed to acknowledge pre-authorization:', error);
      enqueueSnackbar('فشل تأكيد الاطلاع', { variant: 'error' });
    } finally {
      setProcessingIds((prev) => {
        const newSet = new Set(prev);
        newSet.delete(preAuthId);
        return newSet;
      });
    }
  };

  const [resubmitOpen, setResubmitOpen] = useState(false);
  const [selectedResubmitItem, setSelectedResubmitItem] = useState(null);
  const [resubmitNotes, setResubmitNotes] = useState('');

  const openResubmitDialog = (item) => {
    setSelectedResubmitItem(item);
    setResubmitNotes('');
    setResubmitOpen(true);
  };

  const closeResubmitDialog = () => {
    setResubmitOpen(false);
    setSelectedResubmitItem(null);
  };

  const handleResubmit = async () => {
    if (!selectedResubmitItem) return;
    setProcessingIds((prev) => new Set(prev).add(selectedResubmitItem.id));
    
    try {
      await preApprovalsService.updateData(selectedResubmitItem.id, {
        clinicalJustification: resubmitNotes
      });
      await preApprovalsService.submit(selectedResubmitItem.id);
      
      enqueueSnackbar('تمت إعادة التقديم بنجاح', { variant: 'success' });
      closeResubmitDialog();
      await loadPreAuthorizations();
    } catch (error) {
      console.error('Failed to resubmit:', error);
      enqueueSnackbar('فشل إعادة التقديم', { variant: 'error' });
    } finally {
      setProcessingIds((prev) => {
        const newSet = new Set(prev);
        newSet.delete(selectedResubmitItem?.id);
        return newSet;
      });
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('ar-SA', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  };

  const formatCurrency = (amount) => {
    if (!amount) return '-';
    return `${parseFloat(amount).toFixed(2)} د.ل`;
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'APPROVED':
        return 'success';
      case 'ACKNOWLEDGED':
        return 'info';
      case 'PENDING':
      case 'UNDER_REVIEW':
        return 'warning';
      case 'INFO_REQUESTED':
        return 'secondary';
      case 'REJECTED':
        return 'error';
      default:
        return 'default';
    }
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'APPROVED':
        return 'موافق عليه';
      case 'ACKNOWLEDGED':
        return 'تم الاطلاع';
      case 'PENDING':
        return 'معلق';
      case 'UNDER_REVIEW':
        return 'قيد المراجعة';
      case 'INFO_REQUESTED':
        return 'بانتظار معلومات';
      case 'REJECTED':
        return 'مرفوض';
      default:
        return status;
    }
  };

  const renderTable = (items, showAcknowledgeButton) => (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>رقم المرجع</TableCell>
            <TableCell>اسم العضو</TableCell>
            <TableCell>الخدمة الطبية</TableCell>
            <TableCell align="right">المبلغ الموافق عليه</TableCell>
            <TableCell>تاريخ الموافقة</TableCell>
            <TableCell>تاريخ الانتهاء</TableCell>
            <TableCell>الحالة</TableCell>
            {showAcknowledgeButton && <TableCell align="center">الإجراء</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={showAcknowledgeButton ? 8 : 7} align="center">
                <Typography variant="body2" color="textSecondary" sx={{ py: '1.5rem' }}>
                  لا توجد موافقات مسبقة
                </Typography>
              </TableCell>
            </TableRow>
          ) : (
            items.map((item) => (
              <TableRow key={item.id} hover>
                <TableCell>
                  <Typography variant="body2" fontWeight="medium">
                    {item.referenceNumber}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{item.memberName || item.memberCivilId}</Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{item.serviceName}</Typography>
                  <Typography variant="caption" color="textSecondary">
                    {item.serviceCode}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography variant="body2" fontWeight="medium" color="success.main">
                    {formatCurrency(item.approvedAmount)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2">{formatDate(item.approvedAt)}</Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" color={new Date(item.expiryDate) < new Date() ? 'error' : 'textPrimary'}>
                    {formatDate(item.expiryDate)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip label={getStatusLabel(item.status)} color={getStatusColor(item.status)} size="small" />
                </TableCell>
                {(showAcknowledgeButton || item.status === 'INFO_REQUESTED' || item.status === 'NEEDS_CORRECTION') && (
                  <TableCell align="center">
                    {showAcknowledgeButton && (
                      <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        startIcon={processingIds.has(item.id) ? <CircularProgress size={16} /> : <CheckCircle />}
                        onClick={() => handleAcknowledge(item.id)}
                        disabled={processingIds.has(item.id)}
                      >
                        تم الاطلاع
                      </Button>
                    )}
                    {(item.status === 'INFO_REQUESTED' || item.status === 'NEEDS_CORRECTION') && (
                      <Button
                        variant="outlined"
                        color="secondary"
                        size="small"
                        startIcon={<EditIcon />}
                        onClick={() => openResubmitDialog(item)}
                        disabled={processingIds.has(item.id)}
                      >
                        إعادة التقديم
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <MainCard
      title="صندوق الموافقات المسبقة"
      secondary={
        flags.DIRECT_PREAUTH_SUBMISSION_ENABLED && (
          <Button variant="contained" color="primary" onClick={() => navigate('/visits')} startIcon={<span>➕</span>}>
            طلب موافقة جديد (عن طريق الزيارات)
          </Button>
        )
      }
    >
      <Box>
        <Alert severity="info" sx={{ mb: '1.5rem' }}>
          <Typography variant="body2">
            هنا تظهر الموافقات المسبقة التي تمت الموافقة عليها من قبل المراجع. يرجى الضغط على "تم الاطلاع" لتأكيد استلام الموافقة.
          </Typography>
        </Alert>

        <Tabs
          value={currentTab}
          onChange={(e, newValue) => setCurrentTab(newValue)}
          sx={{ mb: '1.0rem', borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={1}>
                <span>قيد الانتظار</span>
                <Chip label={pendingItems.length} size="small" color="warning" />
              </Box>
            }
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={1}>
                <span>مطلوب معلومات</span>
                <Chip label={infoRequestedItems.length} size="small" color="secondary" />
              </Box>
            }
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={1}>
                <span>موافق عليه</span>
                <Chip label={approvedItems.length} size="small" color="success" />
              </Box>
            }
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={1}>
                <span>تم الاطلاع</span>
                <Chip label={acknowledgedItems.length} size="small" color="info" />
              </Box>
            }
          />
        </Tabs>

        {currentTab === 0 && <Box>{renderTable(pendingItems, false)}</Box>}
        {currentTab === 1 && <Box>{renderTable(infoRequestedItems, false)}</Box>}
        {currentTab === 2 && <Box>{renderTable(approvedItems, true)}</Box>}
        {currentTab === 3 && <Box>{renderTable(acknowledgedItems, false)}</Box>}
      </Box>

      {/* Resubmit Dialog */}
      <Dialog open={resubmitOpen} onClose={closeResubmitDialog} maxWidth="sm" fullWidth>
        <DialogTitle>إعادة تقديم الطلب (Resubmit)</DialogTitle>
        <DialogContent dividers>
          {selectedResubmitItem && selectedResubmitItem.notes && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <Typography variant="caption" fontWeight="bold">ملاحظة المراجع السابقة:</Typography>
              <Typography variant="body2">{selectedResubmitItem.notes}</Typography>
            </Alert>
          )}
          <Typography variant="body2" sx={{ mb: 2 }}>
            الرجاء إدخال الملاحظات الطبية أو التوضيحات المطلوبة للرد على المراجع:
          </Typography>
          <TextField
            fullWidth
            multiline
            rows={4}
            variant="outlined"
            placeholder="اكتب الملاحظات الطبية هنا..."
            value={resubmitNotes}
            onChange={(e) => setResubmitNotes(e.target.value)}
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={closeResubmitDialog} color="inherit" disabled={processingIds.has(selectedResubmitItem?.id)}>
            إلغاء
          </Button>
          <Button
            onClick={handleResubmit}
            color="primary"
            variant="contained"
            disabled={!resubmitNotes.trim() || processingIds.has(selectedResubmitItem?.id)}
            startIcon={processingIds.has(selectedResubmitItem?.id) ? <CircularProgress size={16} color="inherit" /> : <EditIcon />}
          >
            إعادة إرسال الطلب
          </Button>
        </DialogActions>
      </Dialog>
    </MainCard>
  );
};

export default ProviderPreAuthInbox;
