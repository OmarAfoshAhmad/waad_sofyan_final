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
import { reviewerPreAuthService } from 'services/api';
import { useReviewer } from 'contexts/ReviewerContext';

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

  // SSE Context
  const { inboxRefreshTrigger } = useReviewer();

  // Dialog states
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [selectedPreApproval, setSelectedPreApproval] = useState(null);

  // Form states
  const [rejectionReason, setRejectionReason] = useState('');

  // Error/Success states
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  // Fetch pending pre-approvals
  const fetchPreApprovals = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      // Using new reviewer service
      const response = await reviewerPreAuthService.getInbox({
        page: page + 1,
        size: pageSize
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
  }, [fetchPreApprovals, inboxRefreshTrigger]); // Refetch on SSE event
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
      await reviewerPreAuthService.startReview(preApproval.id);
      setSuccess('تم بدء مراجعة الطلب');
      fetchPreApprovals();
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || 'فشل في بدء المراجعة');
    } finally {
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
      await reviewerPreAuthService.rejectAll(selectedPreApproval.id, rejectionReason.trim());

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
      valueGetter: (params) => params.row?.referenceNumber || `-`
    },
    {
      field: 'memberName',
      headerName: 'اسم المؤمن عليه',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (params) => params.row?.memberName || '-'
    },
    {
      field: 'providerName',
      headerName: 'مقدم الخدمة',
      flex: 1,
      minWidth: '9.375rem',
      valueGetter: (params) => params.row?.providerName || '-'
    },
    {
      field: 'serviceName',
      headerName: 'الخدمة',
      width: '9.375rem',
      valueGetter: (params) => params.row?.serviceName || '-'
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
      valueGetter: (params) => {
        return params.row?.requestDate ? new Date(params.row.requestDate).toLocaleDateString('en-US') : '-';
      }
    },
    {
      field: 'expiryDate',
      headerName: 'تاريخ الانتهاء',
      width: '8.125rem',
      valueGetter: (params) => {
        const date = params.row?.expiryDate || params.row?.expiresAt;
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

          {/* PENDING/UNDER_REVIEW → Reject (Approve is now line-level only) */}
          {(params.row.status === 'PENDING' || params.row.status === 'UNDER_REVIEW') && (
            <>
              <Tooltip title="رفض كلي">
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
