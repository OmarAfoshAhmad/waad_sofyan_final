import { useMemo, useCallback, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// MUI Components
import {
  Box,
  Button,
  IconButton,
  Tooltip,
  Typography,
  Alert,
  Stack,
  TextField,
  InputAdornment,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Divider,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Paper,
  Grid,
  CircularProgress
} from '@mui/material';

// MUI Icons
import InboxIcon from '@mui/icons-material/Inbox';
import VisibilityIcon from '@mui/icons-material/Visibility';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import AttachmentIcon from '@mui/icons-material/Attachment';
import PersonIcon from '@mui/icons-material/Person';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import EmailIcon from '@mui/icons-material/Email';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import DownloadIcon from '@mui/icons-material/Download';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { UnifiedMedicalTable } from 'components/common';
import TableErrorBoundary from 'components/TableErrorBoundary';
import PermissionGuard from 'components/PermissionGuard';

// Services
import { emailPreAuthService } from 'services/api';
import useAuth from 'hooks/useAuth';

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const EmailPreAuthInbox = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  // Table State
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalItems, setTotalItems] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');

  // Dialog State
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [approving, setApproving] = useState(false);
  const [reidentifying, setReidentifying] = useState(false);
  const [approvalData, setApprovalData] = useState({
    memberId: '',
    serviceId: '',
    notes: ''
  });

  // ========================================
  // DATA FETCHING
  // ========================================

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        page: page, // Backend 0-indexed
        size: rowsPerPage,
        processed: false
      };

      const result = await emailPreAuthService.getAll(params);

      // Handle different response formats
      let items = [];
      let total = 0;

      if (result?.content) {
        items = result.content;
        total = result.totalElements || result.content.length;
      } else {
        items = result?.items || [];
        total = result?.total || items.length;
      }

      setData(items);
      setTotalItems(total);
    } catch (err) {
      console.error('Failed to fetch email requests:', err);
      setError('فشل في تحميل طلبات البريد الواردة');
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage]);

  const handleFetchAndRefresh = async () => {
    try {
      setLoading(true);
      setError(null);
      await emailPreAuthService.fetchFromEmailServer();
      await fetchData();
    } catch (err) {
      console.error('Fetch failed:', err);
      setError('فشل جلب رسائل البريد الجديدة من الخادم');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // ========================================
  // EVENT HANDLERS
  // ========================================

  const handleOpenDetails = async (id) => {
    try {
      setDetailsLoading(true);
      setDetailsOpen(true);
      const details = await emailPreAuthService.getById(id);
      setSelectedRequest(details);
    } catch (err) {
      console.error('Failed to fetch details:', err);
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleCloseDetails = () => {
    setDetailsOpen(false);
    setSelectedRequest(null);
  };

  const handleApproveEmail = async () => {
    if (!selectedRequest) return;
    if (!approvalData.memberId || !approvalData.serviceId) {
      alert('يرجى اختيار المؤمن عليه والخدمة الطبية');
      return;
    }

    try {
      setApproving(true);
      await emailPreAuthService.approve(
        selectedRequest.id, 
        approvalData.memberId, 
        approvalData.serviceId, 
        approvalData.notes
      );
      handleCloseDetails();
      fetchData();
    } catch (err) {
      console.error('Approval failed:', err);
      alert('فشل في الموافقة: ' + (err.message || 'خطأ غير معروف'));
    } finally {
      setApproving(false);
    }
  };

  const handleReidentify = async (id) => {
    try {
      setReidentifying(true);
      await emailPreAuthService.reidentify(id);
      if (detailsOpen && selectedRequest?.id === id) {
        // Refresh details if open
        const details = await emailPreAuthService.getById(id);
        setSelectedRequest(details);
      }
      fetchData();
    } catch (err) {
      console.error('Re-identification failed:', err);
      alert('فشل إعادة التعريف: ' + (err.message || 'خطأ غير معروف'));
    } finally {
      setReidentifying(false);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('هل أنت متأكد من حذف هذا الطلب؟')) {
      try {
        await emailPreAuthService.remove(id);
        fetchData();
      } catch (err) {
        console.error('Delete failed:', err);
        setError('فشل حذف الطلب');
      }
    }
  };

  const handleConvertToPreAuth = (request) => {
    // Navigate to visit flow pre-filling member and provider if available
    let url = '/provider/visits';
    const params = new URLSearchParams();
    if (request.memberId) params.append('memberId', request.memberId);
    if (request.providerId) params.append('providerId', request.providerId);
    params.append('emailRequestId', request.id);
    
    navigate(`${url}?${params.toString()}`);
  };

  const handleDownloadAttachment = (attachment) => {
    // Construct download URL
    const url = `${import.meta.env.VITE_API_BASE_URL}/api/files/download/${attachment.fileId}`;
    window.open(url, '_blank');
  };

  // ========================================
  // COLUMN DEFINITIONS
  // ========================================

  const columns = useMemo(
    () => [
      {
        id: 'receivedAt',
        label: 'تاريخ الاستلام',
        minWidth: 150,
        sortable: true
      },
      {
        id: 'sender',
        label: 'المرسل',
        minWidth: 200,
        icon: <EmailIcon fontSize="small" />
      },
      {
        id: 'subject',
        label: 'الموضوع',
        minWidth: 250
      },
      {
        id: 'provider',
        label: 'مقدم الخدمة',
        minWidth: 150,
        icon: <LocalHospitalIcon fontSize="small" />
      },
      {
        id: 'member',
        label: 'المؤمن عليه',
        minWidth: 150,
        icon: <PersonIcon fontSize="small" />
      },
      {
        id: 'attachments',
        label: 'المرفقات',
        minWidth: 100,
        align: 'center'
      },
      {
        id: 'actions',
        label: 'الإجراءات',
        minWidth: 120,
        align: 'center'
      }
    ],
    []
  );

  // ========================================
  // CELL RENDERER
  // ========================================

  const renderCell = useCallback(
    (request, column) => {
      if (!request) return null;

      switch (column.id) {
        case 'receivedAt':
          return (
            <Typography variant="body2">
              {new Date(request.receivedAt).toLocaleString('en-US')}
            </Typography>
          );

        case 'sender':
          return (
            <Box>
              <Typography variant="body2" fontWeight={600}>{request.senderEmail}</Typography>
              {request.senderName && (
                <Typography variant="caption" color="text.secondary">{request.senderName}</Typography>
              )}
            </Box>
          );

        case 'subject':
          return (
            <Typography variant="body2" noWrap sx={{ maxWidth: 300 }}>
              {request.subject || '(بدون عنوان)'}
            </Typography>
          );

        case 'provider':
          return request.providerId ? (
            <Chip 
              icon={<CheckCircleIcon size="small" />} 
              label={request.providerName || 'مقدم معروف'} 
              color="success" 
              size="small" 
              variant="outlined" 
            />
          ) : (
            <Chip label="غير معروف" color="default" size="small" variant="outlined" />
          );

        case 'member':
          return request.memberId ? (
            <Chip 
              icon={<CheckCircleIcon size="small" />} 
              label={request.memberFullName || 'عضو معروف'} 
              color="info" 
              size="small" 
              variant="outlined" 
            />
          ) : (
            <Chip label="غير محدد" color="default" size="small" variant="outlined" />
          );

        case 'attachments':
          const count = request.attachmentsCount || 0;
          return count > 0 ? (
            <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="center">
              <AttachmentIcon fontSize="small" color="primary" />
              <Typography variant="body2" color="primary.main" fontWeight={600}>{count}</Typography>
            </Stack>
          ) : '-';

        case 'actions':
          return (
            <Stack direction="row" spacing={0.5} justifyContent="center">
              <Tooltip title="عرض التفاصيل">
                <IconButton size="small" color="primary" onClick={() => handleOpenDetails(request.id)}>
                  <VisibilityIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="بدء المعالجة">
                <IconButton size="small" color="success" onClick={() => handleConvertToPreAuth(request)}>
                  <PlayArrowIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="حذف">
                <IconButton size="small" color="error" onClick={() => handleDelete(request.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="إعادة محاولة التعرف">
                <IconButton 
                  size="small" 
                  color="info" 
                  onClick={() => handleReidentify(request.id)}
                  disabled={reidentifying}
                >
                  <AutoFixHighIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          );

        default:
          return null;
      }
    },
    [handleConvertToPreAuth]
  );

  return (
    <Box>
      <ModernPageHeader
        title="بريد الموافقات المسبقة (Automation)"
        subtitle="إدارة الطلبات المستلمة عبر البريد الإلكتروني آلياً"
        icon={InboxIcon}
        breadcrumbs={[{ label: 'الرئيسية', path: '/' }, { label: 'بريد الموافقات' }]}
        actions={
          <Button variant="contained" startIcon={<RefreshIcon />} onClick={handleFetchAndRefresh} disabled={loading}>
            جلب رسائل جديدة
          </Button>
        }
      />

      <MainCard title="صندوق الطلبات الجديدة (غير المعالجة)">
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <TableErrorBoundary>
          <UnifiedMedicalTable
            columns={columns}
            data={data}
            loading={loading}
            renderCell={renderCell}
            totalItems={totalItems}
            page={page}
            rowsPerPage={rowsPerPage}
            onPageChange={(e, p) => setPage(p)}
            onRowsPerPageChange={(e) => setRowsPerPage(parseInt(e.target.value, 10))}
            emptyStateConfig={{
              icon: InboxIcon,
              title: 'الصندوق فارغ',
              description: 'لا توجد طلبات بريد جديدة حالياً'
            }}
          />
        </TableErrorBoundary>
      </MainCard>

      {/* Details Dialog */}
      <Dialog 
        open={detailsOpen} 
        onClose={handleCloseDetails} 
        maxWidth="md" 
        fullWidth
        scroll="paper"
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <EmailIcon color="primary" />
            <Typography variant="h5">تفاصيل طلب البريد</Typography>
          </Stack>
          {selectedRequest && (
            <Chip 
              label={`معرف: ${selectedRequest.id}`} 
              size="small" 
              variant="outlined" 
            />
          )}
        </DialogTitle>
        <DialogContent dividers>
          {detailsLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}>
              <CircularProgress />
            </Box>
          ) : selectedRequest ? (
            <Grid container spacing={3}>
              {/* Header Info */}
              <Grid item xs={12}>
                <Paper variant="outlined" sx={{ p: 2, bgcolor: 'grey.50' }}>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="text.secondary">من:</Typography>
                      <Typography variant="body1" fontWeight={600}>{selectedRequest.senderName || 'غير محدد'}</Typography>
                      <Typography variant="body2" color="primary">{selectedRequest.senderEmail}</Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="text.secondary">التاريخ:</Typography>
                      <Typography variant="body1">{new Date(selectedRequest.receivedAt).toLocaleString('en-US')}</Typography>
                    </Grid>
                    <Grid item xs={12}>
                      <Typography variant="caption" color="text.secondary">الموضوع:</Typography>
                      <Typography variant="body1" fontWeight={700}>{selectedRequest.subject || '(بدون عنوان)'}</Typography>
                    </Grid>
                  </Grid>
                </Paper>
              </Grid>

              {/* Identification Badges */}
              <Grid item xs={12}>
                <Stack direction="row" spacing={2}>
                  <Paper variant="outlined" sx={{ p: 1, flex: 1, display: 'flex', alignItems: 'center', borderColor: selectedRequest.providerId ? 'success.light' : 'divider' }}>
                    <LocalHospitalIcon sx={{ mr: 1, color: selectedRequest.providerId ? 'success.main' : 'grey.400' }} />
                    <Box>
                      <Typography variant="caption" display="block">مقدم الخدمة:</Typography>
                      <Typography variant="body2" fontWeight={600}>{selectedRequest.providerName || 'لم يتم التعرف عليه'}</Typography>
                    </Box>
                  </Paper>
                  <Paper variant="outlined" sx={{ p: 1, flex: 1, display: 'flex', alignItems: 'center', borderColor: selectedRequest.memberId || approvalData.memberId ? 'info.light' : 'divider' }}>
                    <PersonIcon sx={{ mr: 1, color: selectedRequest.memberId || approvalData.memberId ? 'info.main' : 'grey.400' }} />
                    <Box>
                      <Typography variant="caption" display="block">المؤمن عليه:</Typography>
                      <Typography variant="body2" fontWeight={600}>{selectedRequest.memberFullName || (approvalData.memberId ? 'تم الاختيار' : 'لم يتم التعرف عليه')}</Typography>
                    </Box>
                  </Paper>
                </Stack>
              </Grid>

              {/* Quick Approval Section */}
              <Grid item xs={12}>
                <Divider sx={{ my: 1 }}><Chip label="إجراءات المراجعة والموافقة" size="small" /></Divider>
                <Paper variant="outlined" sx={{ p: 2, mt: 1, bgcolor: '#f0f7ff' }}>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="معرف المؤمن عليه (ID)"
                        size="small"
                        value={approvalData.memberId}
                        onChange={(e) => setApprovalData({ ...approvalData, memberId: e.target.value })}
                        helperText="أدخل رقم هوية المشترك (Member ID)"
                        defaultValue={selectedRequest.memberId}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="معرف الخدمة (Service ID)"
                        size="small"
                        value={approvalData.serviceId}
                        onChange={(e) => setApprovalData({ ...approvalData, serviceId: e.target.value })}
                        helperText="أدخل رقم الخدمة المطلوبة"
                        defaultValue={selectedRequest.detectedServiceId}
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        fullWidth
                        label="ملاحظات المراجعة (تظهر في الملف الرسمي)"
                        multiline
                        rows={2}
                        value={approvalData.notes}
                        onChange={(e) => setApprovalData({ ...approvalData, notes: e.target.value })}
                        placeholder="أدخل أي ملاحظات إضافية بخصوص الموافقة..."
                      />
                    </Grid>
                  </Grid>
                </Paper>
              </Grid>

              {/* Email Content */}
              <Grid item xs={12}>
                <Typography variant="subtitle2" gutterBottom>نص الرسالة:</Typography>
                <Paper 
                  variant="outlined" 
                  sx={{ 
                    p: 2, 
                    minHeight: 200, 
                    maxHeight: 400, 
                    overflowY: 'auto',
                    direction: 'rtl',
                    bgcolor: '#fff'
                  }}
                >
                  {selectedRequest.bodyHtml ? (
                    <div dangerouslySetInnerHTML={{ __html: selectedRequest.bodyHtml }} />
                  ) : (
                    <Typography style={{ whiteSpace: 'pre-line' }}>{selectedRequest.bodyText}</Typography>
                  )}
                </Paper>
              </Grid>

              {/* Attachments */}
              {selectedRequest.attachments && selectedRequest.attachments.length > 0 && (
                <Grid item xs={12}>
                  <Typography variant="subtitle2" gutterBottom>المرفقات ({selectedRequest.attachments.length}):</Typography>
                  <List dense sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 1 }}>
                    {selectedRequest.attachments.map((att) => (
                      <ListItem 
                        key={att.id}
                        secondaryAction={
                          <IconButton edge="end" color="primary" onClick={() => handleDownloadAttachment(att)}>
                            <DownloadIcon />
                          </IconButton>
                        }
                      >
                        <ListItemIcon>
                          <AttachmentIcon />
                        </ListItemIcon>
                        <ListItemText 
                          primary={att.fileName} 
                          secondary={`${(att.fileSize / 1024).toFixed(1)} KB | ${att.fileType}`} 
                        />
                      </ListItem>
                    ))}
                  </List>
                </Grid>
              )}
            </Grid>
          ) : (
            <Typography>لم يتم العثور على بيانات</Typography>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button variant="outlined" onClick={handleCloseDetails}>إغلاق</Button>
          {selectedRequest && (
            <Stack direction="row" spacing={1}>
              <Button 
                variant="contained" 
                color="info" 
                startIcon={<PlayArrowIcon />}
                onClick={() => handleConvertToPreAuth(selectedRequest)}
              >
                تحويل لزيارة
              </Button>
              <Button 
                variant="contained" 
                color="success" 
                startIcon={<CheckCircleIcon />}
                onClick={handleApproveEmail}
                disabled={approving}
              >
                {approving ? 'جاري الموافقة...' : 'موافقة مباشرة وإرسال الرد'}
              </Button>
              <Button 
                variant="outlined" 
                color="info" 
                startIcon={<AutoFixHighIcon />}
                onClick={() => handleReidentify(selectedRequest.id)}
                disabled={reidentifying}
              >
                {reidentifying ? 'جاري التعريف...' : 'إعادة التعرف تلقائياً'}
              </Button>
            </Stack>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EmailPreAuthInbox;
