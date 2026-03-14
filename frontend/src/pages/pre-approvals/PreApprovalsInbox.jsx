import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  IconButton,
  Tooltip,
  Alert,
  Card,
  CardContent,
  Typography,
  Grid,
  Stack,
  Divider,
  Table,
  TableBody,
  TableRow,
  TableCell,
  CircularProgress
} from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Visibility as ViewIcon,
  Refresh as RefreshIcon,
  Assignment as PreApprovalIcon,
  MedicalServices as MedicalIcon,
  PlayArrow as StartReviewIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { DataGrid } from '@mui/x-data-grid';
import { preApprovalsService } from 'services/api';

/**
 * Pre-Approvals Inbox - صندوق الموافقات المسبقة
 *
 * يعرض طلبات الموافقة المسبقة المعلقة (SUBMITTED/UNDER_REVIEW) ويتيح الموافقة أو الرفض
 */
const PreApprovalsInbox = () => {
  const navigate = useNavigate();

  // State
  const [preApprovals, setPreApprovals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalRows, setTotalRows] = useState(0);

  // Dialog states
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [selectedPreApproval, setSelectedPreApproval] = useState(null);

  // Form states
  const [approvedAmount, setApprovedAmount] = useState('');
  const [approvalNotes, setApprovalNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');

  // Error/Success states
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Fetch pending pre-approvals
  const fetchPreApprovals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await preApprovalsService.getPending({
        page: page + 1,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'asc' // FIFO - الأقدم أولاً
      });
      setPreApprovals(response.items || []);
      setTotalRows(response.total || 0);
    } catch (err) {
      console.error('Error fetching pre-approvals:', err);
      setError(err.userMessage || err.response?.data?.message || 'فشل في تحميل طلبات الموافقة المسبقة');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchPreApprovals();
  }, [fetchPreApprovals]);

  // Open approve dialog
  const handleOpenApprove = (preApproval) => {
    setSelectedPreApproval(preApproval);
    setApprovedAmount(preApproval.requestedAmount?.toString() || '');
    setApprovalNotes('');
    setApproveDialogOpen(true);
  };

  // Open reject dialog
  const handleOpenReject = (preApproval) => {
    setSelectedPreApproval(preApproval);
    setRejectionReason('');
    setRejectDialogOpen(true);
  };

  // Start Review - transition from SUBMITTED to UNDER_REVIEW
  const handleStartReview = async (preApproval) => {
    try {
      setActionLoading(true);
      setError(null);
      await preApprovalsService.startReview(preApproval.id);
      setSuccess('تم بدء مراجعة الطلب');
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في بدء المراجعة');
    } finally {
      setActionLoading(false);
    }
  };

  // Approve pre-approval
  const handleApprove = async () => {
    if (!selectedPreApproval) return;

    try {
      setActionLoading(true);
      setError(null);

      // Backend calculates approvedAmount automatically - DO NOT send it
      // Only send approval notes
      await preApprovalsService.approve(selectedPreApproval.id, {
        approvalNotes: approvalNotes || '' // Backend expects 'approvalNotes', not 'notes'
      });

      setApproveDialogOpen(false);
      setSelectedPreApproval(null);
      setSuccess('جاري معالجة الموافقة...');

      // Phase 2: Poll for final status
      const pollInterval = setInterval(async () => {
        try {
          const updated = await preApprovalsService.getById(selectedPreApproval.id);

          if (updated.status === 'APPROVED') {
            clearInterval(pollInterval);
            setActionLoading(false);
            setSuccess('تمت الموافقة على الطلب بنجاح');
            fetchPreApprovals();
          } else if (updated.status === 'REJECTED') {
            clearInterval(pollInterval);
            setActionLoading(false);
            setError('تم رفض الطلب: ' + (updated.rejectionReason || 'خطأ في المعالجة'));
            fetchPreApprovals();
          }
          // If still APPROVAL_IN_PROGRESS, continue polling
        } catch (pollError) {
          console.error('Polling error:', pollError);
          clearInterval(pollInterval);
          setActionLoading(false);
          setError('خطأ في التحقق من حالة الموافقة');
        }
      }, 3000); // Poll every 3 seconds

      // Timeout after 2 minutes
      setTimeout(() => {
        clearInterval(pollInterval);
        if (actionLoading) {
          setActionLoading(false);
          setSuccess('انتهت مهلة المعالجة. يرجى تحديث الصفحة.');
          fetchPreApprovals();
        }
      }, 120000);
    } catch (err) {
      console.error('Approve error:', err);
      setError(err.userMessage || err.response?.data?.message || 'فشل في الموافقة على الطلب');
      setActionLoading(false);
    }
  };

  // Reject pre-approval
  const handleReject = async () => {
    if (!selectedPreApproval || !rejectionReason.trim()) {
      setError('يجب إدخال سبب الرفض');
      return;
    }

    try {
      setActionLoading(true);
      setError(null);
      await preApprovalsService.reject(selectedPreApproval.id, {
        rejectionReason: rejectionReason.trim()
      });

      setSuccess('تم رفض الطلب');
      setRejectDialogOpen(false);
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في رفض الطلب');
    } finally {
      setActionLoading(false);
    }
  };

  // Status chip (using exact Backend enum values) - CANONICAL 2026-01-26
  // PreAuth workflow: PENDING → UNDER_REVIEW → APPROVED/REJECTED
  const getStatusChip = (status) => {
    const configs = {
      PENDING: { color: 'warning', label: 'معلق' },
      UNDER_REVIEW: { color: 'info', label: 'قيد المراجعة' },
      APPROVED: { color: 'success', label: 'موافق عليه' },
      REJECTED: { color: 'error', label: 'مرفوض' },
      EXPIRED: { color: 'default', label: 'منتهي' },
      CANCELLED: { color: 'default', label: 'ملغي' },
      USED: { color: 'info', label: 'مستخدم' }
    };
    const config = configs[status] || configs.PENDING;
    return <Chip size="small" color={config.color} label={config.label} />;
  };

  // Priority badge (using exact Backend enum values)
  const getUrgencyBadge = (priority) => {
    if (priority === 'EMERGENCY') {
      return <Chip size="small" color="error" label="طارئ" variant="filled" />;
    }
    if (priority === 'URGENT') {
      return <Chip size="small" color="warning" label="عاجل" variant="outlined" />;
    }
    if (priority === 'ROUTINE') {
      return <Chip size="small" color="default" label="عادي" variant="outlined" />;
    }
    return null;
  };

  // DataGrid columns (CANONICAL - follows Backend DTO exactly)
  const columns = [
    {
      field: 'id',
      headerName: '#',
      width: '6.25rem',
      valueGetter: (value, row) => row.referenceNumber || `-`
    },
    {
      field: 'memberName',
      headerName: 'اسم المؤمن عليه',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (value, row) => row.memberName || '-'
    },
    {
      field: 'providerName',
      headerName: 'مقدم الخدمة',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (value, row) => row.providerName || '-'
    },
    {
      field: 'serviceName',
      headerName: 'الخدمة',
      width: '9.375rem',
      valueGetter: (value, row) => row.serviceName || '-'
    },
    {
      field: 'priority',
      headerName: 'الأولوية',
      width: '6.25rem',
      renderCell: (params) => getUrgencyBadge(params.row.priority)
    },
    {
      field: 'requestDate',
      headerName: 'تاريخ الطلب',
      width: '8.125rem',
      valueGetter: (value, row) => {
        return row.requestDate ? new Date(row.requestDate).toLocaleDateString('en-US') : '-';
      }
    },
    {
      field: 'expiryDate',
      headerName: 'تاريخ الانتهاء',
      width: '8.125rem',
      valueGetter: (value, row) => {
        const date = row?.expiryDate || row?.expiresAt;
        return date ? new Date(date).toLocaleDateString('en-US') : '-';
      }
    },
    {
      field: 'status',
      headerName: 'الحالة',
      width: '7.5rem',
      renderCell: (params) => getStatusChip(params.value)
    },
    {
      field: 'actions',
      headerName: 'الإجراءات',
      width: '12.5rem',
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1}>
          <Tooltip title="عرض التفاصيل">
            <IconButton size="small" color="primary" onClick={() => navigate(`/pre-approvals/${params.row.id}`)} disabled={actionLoading}>
              <ViewIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          {/* PENDING → Start Review (transition to UNDER_REVIEW)
              CANONICAL 2026-01-26: PreAuth workflow starts at PENDING, not SUBMITTED
              PENDING means newly created and awaiting initial review */}
          {params.row.status === 'PENDING' && (
            
              <Tooltip title="بدء المراجعة">
                <span>
                  <IconButton size="small" color="info" onClick={() => handleStartReview(params.row)} disabled={actionLoading}>
                    <StartReviewIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              
          )}

          {/* PENDING/UNDER_REVIEW → Approve/Reject
              CANONICAL: Both states allow approval/rejection actions */}
          {(params.row.status === 'PENDING' || params.row.status === 'UNDER_REVIEW') && (
            <>
              <Tooltip title="موافقة">
                <span>
                  <IconButton size="small" color="success" onClick={() => handleOpenApprove(params.row)} disabled={actionLoading}>
                    <ApproveIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="رفض">
                <span>
                  <IconButton size="small" color="error" onClick={() => handleOpenReject(params.row)} disabled={actionLoading}>
                    <RejectIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            </>
          )}
        </Stack>
      )
    }
  ];

  return (
    <>
      <ModernPageHeader
        title="صندوق الموافقات المسبقة"
        subtitle="طلبات الموافقة المسبقة المعلقة"
        icon={PreApprovalIcon}
        actions={
          <Button startIcon={<RefreshIcon />} onClick={fetchPreApprovals} disabled={loading}>
            تحديث
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: '1.0rem' }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      <MainCard>
        <Box sx={{ minHeight: '25.0rem', width: '100%' }}>
          <DataGrid
            autoHeight
            rows={preApprovals}
            columns={columns}
            loading={loading}
            paginationMode="server"
            rowCount={totalRows}
            paginationModel={{ page, pageSize }}
            onPaginationModelChange={(model) => {
              setPage(model.page);
              setPageSize(model.pageSize);
            }}
            pageSizeOptions={[10, 20, 50]}
            disableSelectionOnClick
            localeText={{
              noRowsLabel: 'لا توجد طلبات موافقة مسبقة معلقة',
              MuiTablePagination: {
                labelRowsPerPage: 'عدد الصفوف:'
              }
            }}
            sx={{
              '& .MuiDataGrid-row': {
                '&:hover': {
                  backgroundColor: 'action.hover'
                }
              }
            }}
          />
        </Box>
      </MainCard>

      {/* Approve Dialog */}
      <Dialog open={approveDialogOpen} onClose={() => !actionLoading && setApproveDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <ApproveIcon color="success" />
            <span>الموافقة على الطلب #{selectedPreApproval?.id}</span>
          </Stack>
        </DialogTitle>
        <DialogContent>
          <Card variant="outlined" sx={{ mb: '1.5rem', mt: '1.0rem' }}>
            <CardContent>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                تفاصيل الطلب
              </Typography>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>المؤمن عليه</TableCell>
                    <TableCell>{selectedPreApproval?.memberFullNameArabic}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>مقدم الخدمة</TableCell>
                    <TableCell>{selectedPreApproval?.providerName}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 500 }}>نوع الخدمة</TableCell>
                    <TableCell>{selectedPreApproval?.serviceType || selectedPreApproval?.procedureName || '-'}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Divider sx={{ my: '1.0rem' }} />


          <TextField
            fullWidth
            label="ملاحظات (اختياري)"
            value={approvalNotes}
            onChange={(e) => setApprovalNotes(e.target.value)}
            multiline
            rows={2}
            disabled={actionLoading}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApproveDialogOpen(false)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={handleApprove}
            disabled={actionLoading}
            startIcon={actionLoading ? <CircularProgress size={20} color="inherit" /> : <ApproveIcon />}
          >
            {actionLoading ? 'جارِ الموافقة...' : 'موافقة'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => !actionLoading && setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <RejectIcon color="error" />
            <span>رفض الطلب #{selectedPreApproval?.id}</span>
          </Stack>
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: '1.0rem', mt: 1 }}>
            يرجى إدخال سبب واضح للرفض. هذا السبب سيظهر للمستشفى/العيادة.
          </Alert>

          <TextField
            fullWidth
            required
            label="سبب الرفض"
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            multiline
            rows={3}
            error={!rejectionReason.trim()}
            helperText="مطلوب - اشرح سبب الرفض بوضوح"
            disabled={actionLoading}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)} disabled={actionLoading}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReject}
            disabled={!rejectionReason.trim() || actionLoading}
            startIcon={actionLoading ? <CircularProgress size={20} color="inherit" /> : <RejectIcon />}
          >
            {actionLoading ? 'جارِ الرفض...' : 'تأكيد الرفض'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default PreApprovalsInbox;
