/**
 * PreAuth Audit Trail Page
 * Displays complete audit history for a specific PreAuthorization
 *
 * Features:
 * - Timeline view of all actions
 * - Filter by action type, user, date range
 * - Search functionality
 * - Export to Excel
 */

import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  TextField,
  Typography,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Alert,
  Paper,
  Divider,
  IconButton,
  Tooltip
} from '@mui/material';
import {
  ArrowBack,
  FilterList as FilterIcon,
  Search as SearchIcon,
  FileDownload as ExportIcon,
  Refresh as RefreshIcon,
  Timeline as TimelineIcon
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import AuditTimeline from 'components/audit/AuditTimeline';
import { usePreAuthAudit, usePreAuthAuditSearch } from 'hooks/usePreAuthAudit';
import { usePreApprovalDetails } from 'hooks/usePreApprovals';
import { exportToExcel } from 'utils/exportUtils';

// Action types for filter
const ACTION_TYPES = [
  { value: '', label: 'جميع الإجراءات' },
  { value: 'CREATE', label: 'إنشاء' },
  { value: 'UPDATE', label: 'تحديث' },
  { value: 'APPROVE', label: 'موافقة' },
  { value: 'REJECT', label: 'رفض' },
  { value: 'CANCEL', label: 'إلغاء' },
  { value: 'DELETE', label: 'حذف' },
  { value: 'STATUS_CHANGE', label: 'تغيير الحالة' }
];

const PreAuthAuditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  // Get PreAuth details for header
  const { preApproval, loading: preAuthLoading } = usePreApprovalDetails(id);

  // Filters state
  const [filters, setFilters] = useState({
    action: '',
    user: '',
    startDate: null,
    endDate: null
  });

  const [showFilters, setShowFilters] = useState(false);

  // Audit data with filters
  const {
    data: audits,
    loading: auditsLoading,
    error: auditsError,
    hasMore,
    loadMore,
    refresh
  } = usePreAuthAudit({
    preAuthId: id,
    action: filters.action || undefined
  });

  // Search functionality
  const { query: searchQuery, setQuery: setSearchQuery, data: searchResults, loading: searchLoading } = usePreAuthAuditSearch();

  // Determine which data to display
  const displayData = searchQuery && searchQuery.trim().length >= 2 ? searchResults : audits;

  const isLoading = auditsLoading || searchLoading;

  // Filter by date range (client-side)
  const filteredData = displayData.filter((audit) => {
    if (filters.startDate && dayjs(audit.changeDate).isBefore(filters.startDate, 'day')) {
      return false;
    }
    if (filters.endDate && dayjs(audit.changeDate).isAfter(filters.endDate, 'day')) {
      return false;
    }
    if (filters.user && !audit.changedBy.toLowerCase().includes(filters.user.toLowerCase())) {
      return false;
    }
    return true;
  });

  // Handle filter change
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
  };

  // Clear all filters
  const handleClearFilters = () => {
    setFilters({
      action: '',
      user: '',
      startDate: null,
      endDate: null
    });
    setSearchQuery('');
  };

  // Export to Excel
  const handleExport = async () => {
    const exportData = filteredData.map((audit, index) => ({
      index: index + 1,
      reference: audit.referenceNumber || '-',
      action: ACTION_TYPES.find((t) => t.value === audit.action)?.label || audit.action,
      user: audit.changedBy || '-',
      date: dayjs(audit.changeDate).format('DD/MM/YYYY HH:mm'),
      field: audit.fieldName || '-',
      oldValue: audit.oldValue || '-',
      newValue: audit.newValue || '-',
      notes: audit.notes || '-',
      ip: audit.ipAddress || '-'
    }));

    const filename = `PreAuth_${id}_Audit_${dayjs().format('YYYYMMDD')}.xlsx`;

    await exportToExcel(exportData, filename.replace('.xlsx', ''), {
      reportTitle: 'سجل تدقيق الموافقة المسبقة',
      columns: [
        { key: 'index', header: '#', width: '0.375rem', type: 'number' },
        { key: 'reference', header: 'الرقم المرجعي', width: '1.375rem', type: 'string' },
        { key: 'action', header: 'الإجراء', width: '1.0rem', type: 'string' },
        { key: 'user', header: 'المستخدم', width: '1.25rem', type: 'string' },
        { key: 'date', header: 'التاريخ', width: '1.25rem', type: 'string' },
        { key: 'field', header: 'الحقل', width: '1.125rem', type: 'string' },
        { key: 'oldValue', header: 'القيمة القديمة', width: '1.5rem', type: 'string' },
        { key: 'newValue', header: 'القيمة الجديدة', width: '1.5rem', type: 'string' },
        { key: 'notes', header: 'الملاحظات', width: '2.125rem', type: 'string' },
        { key: 'ip', header: 'IP', width: '1.0rem', type: 'string' }
      ]
    });
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box>
        {/* Page Header */}
        <ModernPageHeader
          title="سجل التدقيق"
          subtitle={preApproval ? `الموافقة المسبقة: ${preApproval.referenceNumber || `#${id}`}` : 'جارِ التحميل...'}
          icon={TimelineIcon}
          breadcrumbs={[
            { label: 'الرئيسية', path: '/' },
            { label: 'الموافقات المسبقة', path: '/pre-approvals' },
            { label: preApproval?.referenceNumber || `#${id}`, path: `/pre-approvals/view/${id}` },
            { label: 'سجل التدقيق' }
          ]}
          actions={
            <Stack direction="row" spacing={1}>
              <Tooltip title="تحديث">
                <IconButton onClick={refresh} color="primary">
                  <RefreshIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="تصدير إلى Excel">
                <IconButton onClick={handleExport} color="success">
                  <ExportIcon />
                </IconButton>
              </Tooltip>
              <Button variant="outlined" startIcon={<ArrowBack />} onClick={() => navigate(`/pre-approvals/view/${id}`)}>
                رجوع
              </Button>
            </Stack>
          }
        />

        {/* Filters Section */}
        <Card sx={{ mb: '1.5rem' }}>
          <CardContent>
            {/* Search and Filter Toggle */}
            <Stack direction="row" spacing={2} sx={{ mb: showFilters ? 3 : 0 }}>
              <TextField
                fullWidth
                placeholder="البحث في سجل التدقيق..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                InputProps={{
                  startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />
                }}
              />
              <Button
                variant={showFilters ? 'contained' : 'outlined'}
                startIcon={<FilterIcon />}
                onClick={() => setShowFilters(!showFilters)}
                sx={{ minWidth: '7.5rem' }}
              >
                فلاتر
              </Button>
            </Stack>

            {/* Filter Fields */}
            {showFilters && (
              <>
                <Divider sx={{ my: '1.0rem' }} />
                <Grid container spacing={2}>
                  {/* Action Type Filter */}
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <FormControl fullWidth>
                      <InputLabel>نوع الإجراء</InputLabel>
                      <Select value={filters.action} onChange={(e) => handleFilterChange('action', e.target.value)} label="نوع الإجراء">
                        {ACTION_TYPES.map((type) => (
                          <MenuItem key={type.value} value={type.value}>
                            {type.label}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>

                  {/* User Filter */}
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <TextField
                      fullWidth
                      label="المستخدم"
                      value={filters.user}
                      onChange={(e) => handleFilterChange('user', e.target.value)}
                      placeholder="البحث بالمستخدم..."
                    />
                  </Grid>

                  {/* Start Date */}
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <DatePicker
                      label="من تاريخ"
                      value={filters.startDate}
                      onChange={(value) => handleFilterChange('startDate', value)}
                      slotProps={{
                        textField: {
                          fullWidth: true
                        }
                      }}
                    />
                  </Grid>

                  {/* End Date */}
                  <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                    <DatePicker
                      label="إلى تاريخ"
                      value={filters.endDate}
                      onChange={(value) => handleFilterChange('endDate', value)}
                      slotProps={{
                        textField: {
                          fullWidth: true
                        }
                      }}
                    />
                  </Grid>
                </Grid>

                {/* Active Filters Summary */}
                {(filters.action || filters.user || filters.startDate || filters.endDate || searchQuery) && (
                  <Stack direction="row" spacing={1} sx={{ mt: '1.0rem' }} flexWrap="wrap">
                    <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 2.5 }}>
                      الفلاتر النشطة:
                    </Typography>
                    {filters.action && (
                      <Chip
                        label={`الإجراء: ${ACTION_TYPES.find((t) => t.value === filters.action)?.label}`}
                        onDelete={() => handleFilterChange('action', '')}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    )}
                    {filters.user && (
                      <Chip
                        label={`المستخدم: ${filters.user}`}
                        onDelete={() => handleFilterChange('user', '')}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    )}
                    {filters.startDate && (
                      <Chip
                        label={`من: ${dayjs(filters.startDate).format('DD/MM/YYYY')}`}
                        onDelete={() => handleFilterChange('startDate', null)}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    )}
                    {filters.endDate && (
                      <Chip
                        label={`إلى: ${dayjs(filters.endDate).format('DD/MM/YYYY')}`}
                        onDelete={() => handleFilterChange('endDate', null)}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    )}
                    {searchQuery && (
                      <Chip
                        label={`البحث: ${searchQuery}`}
                        onDelete={() => setSearchQuery('')}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    )}
                    <Button size="small" onClick={handleClearFilters} sx={{ ml: 1 }}>
                      مسح الكل
                    </Button>
                  </Stack>
                )}
              </>
            )}
          </CardContent>
        </Card>

        {/* Results Count */}
        {!isLoading && (
          <Paper sx={{ p: '1.0rem', mb: '1.0rem', bgcolor: 'primary.lighter' }}>
            <Typography variant="body2" color="primary.dark">
              عرض <strong>{filteredData.length}</strong> سجل تدقيق
              {searchQuery && ` (نتائج البحث عن "${searchQuery}")`}
            </Typography>
          </Paper>
        )}

        {/* Error Alert */}
        {auditsError && (
          <Alert severity="error" sx={{ mb: '1.5rem' }}>
            {auditsError}
          </Alert>
        )}

        {/* Timeline */}
        <MainCard>
          <AuditTimeline audits={filteredData} loading={isLoading} onLoadMore={loadMore} hasMore={hasMore && !searchQuery} />
        </MainCard>
      </Box>
    </LocalizationProvider>
  );
};

export default PreAuthAuditPage;



