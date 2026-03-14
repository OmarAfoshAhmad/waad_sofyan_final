import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useReactToPrint } from 'react-to-print';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Typography,
  Grid,
  TextField,
  MenuItem,
  Stack,
  Chip,
  Alert,
  IconButton,
  Tooltip,
  Skeleton,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  Collapse,
  Divider,
  FormControl,
  InputLabel,
  Select,
  OutlinedInput
} from '@mui/material';
import {
  // PictureAsPdf as PdfIcon, // PDF export disabled - Excel is the official format
  TableChart as ExcelIcon,
  Refresh as RefreshIcon,
  Print as PrintIcon,
  LocalHospital as ProviderIcon,
  FilterList as FilterIcon,
  ClearAll as ClearAllIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  CheckCircle as ApprovedIcon,
  Cancel as RejectedIcon,
  HourglassEmpty as PendingIcon,
  Warning as PartialIcon,
  Receipt as ClaimIcon,
  MedicalServices as ServiceIcon
} from '@mui/icons-material';
import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { reportsService } from 'services/api';
// PDF export disabled - Excel is the official reporting format
import { exportToExcel } from 'utils/exportUtils';
import { useAuth } from 'contexts/AuthContext';

/**
 * Provider Settlement Reports - تقارير تسوية مقدمي الخدمة
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║              PROVIDER SETTLEMENT REPORTS - CANONICAL IMPLEMENTATION          ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Purpose: Detailed settlement reports for healthcare providers                ║
 * ║ Data: Claims + ClaimLines + Members + MedicalServices + PreAuths             ║
 * ║ Calculation: ALL amounts from Backend (NO client-side math)                  ║
 * ║ Matches: Paper reports (Gross, Net, Rejected, Patient Share)                 ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 *
 * Columns (matching paper reports):
 * - رقم المطالبة (Claim Number)
 * - رقم الموافقة (Pre-Auth Number)
 * - اسم المريض (Patient Name)
 * - رقم التأمين (Insurance Number)
 * - الخدمة الطبية (Medical Service)
 * - تاريخ الخدمة (Service Date)
 * - المبلغ الإجمالي Gross
 * - المبلغ المعتمد Net
 * - المبلغ المرفوض
 * - سبب الرفض
 * - حصة المؤمن عليه
 *
 * Permissions:
 * - ADMIN/FINANCE: Can view any provider
 * - PROVIDER: Can only view their own provider (no filter selector)
 */
const ProviderSettlementReport = () => {
  const { companyName, primaryColor } = useCompanySettings();
  const { user } = useAuth();
  const printRef = useRef(null);

  // State
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reportData, setReportData] = useState(null);

  // Expanded claims (for collapsible rows)
  const [expandedClaims, setExpandedClaims] = useState({});

  // Filters
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [selectedStatuses, setSelectedStatuses] = useState([]);
  const [claimNumberFilter, setClaimNumberFilter] = useState('');
  const [preAuthNumberFilter, setPreAuthNumberFilter] = useState('');

  const STATUS_OPTIONS = [
    { value: 'SUBMITTED', label: 'مقدم' },
    { value: 'UNDER_REVIEW', label: 'قيد المراجعة' },
    { value: 'NEEDS_CORRECTION', label: 'يحتاج تصحيح' },
    { value: 'APPROVAL_IN_PROGRESS', label: 'جاري معالجة الموافقة' },
    { value: 'APPROVED', label: 'معتمدة' },
    { value: 'BATCHED', label: 'ضمن دفعة تسوية' },
    { value: 'SETTLED', label: 'مسددة/مدفوعة' },
    { value: 'REJECTED', label: 'مرفوضة' }
  ];

  const statusLabelByValue = useMemo(
    () => STATUS_OPTIONS.reduce((acc, item) => ({ ...acc, [item.value]: item.label }), {}),
    []
  );

  const getStatusLabel = useCallback((statusValue) => statusLabelByValue[statusValue] || statusValue, [statusLabelByValue]);

  const setQuickDateRange = useCallback((preset) => {
    const now = new Date();
    if (preset === 'THIS_MONTH') {
      const from = new Date(now.getFullYear(), now.getMonth(), 1);
      setDateFrom(from.toISOString().split('T')[0]);
      setDateTo(now.toISOString().split('T')[0]);
      return;
    }

    if (preset === 'PREVIOUS_MONTH') {
      const from = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const to = new Date(now.getFullYear(), now.getMonth(), 0);
      setDateFrom(from.toISOString().split('T')[0]);
      setDateTo(to.toISOString().split('T')[0]);
      return;
    }

    if (preset === 'THIS_YEAR') {
      setDateFrom(`${now.getFullYear()}-01-01`);
      setDateTo(now.toISOString().split('T')[0]);
      return;
    }

    setDateFrom('');
    setDateTo('');
  }, []);

  // Check if user is admin or provider
  const isAdmin = useMemo(() => {
    const adminRoles = ['SUPER_ADMIN', 'ACCOUNTANT', 'MEDICAL_REVIEWER'];
    return adminRoles.includes(user?.role);
  }, [user?.role]);

  // Fetch available providers
  const { data: providersData, isLoading: providersLoading } = useQuery({
    queryKey: ['providers-for-settlement-report'],
    queryFn: async () => {
      const response = await reportsService.getProvidersForSettlementReport();
      return response || [];
    },
    staleTime: 5 * 60 * 1000
  });

  const providers = useMemo(() => providersData || [], [providersData]);

  // Auto-select first provider if only one available (for provider users)
  useEffect(() => {
    if (providers.length === 1 && !selectedProviderId) {
      setSelectedProviderId(providers[0].id);
    }
  }, [providers, selectedProviderId]);

  // Fetch report data
  const fetchReport = useCallback(async () => {
    if (!selectedProviderId) {
      setError('يرجى اختيار مقدم الخدمة');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const params = {
        providerId: selectedProviderId,
        fromDate: dateFrom || undefined,
        toDate: dateTo || undefined,
        statuses: selectedStatuses.length > 0 ? selectedStatuses : undefined,
        claimNumber: claimNumberFilter || undefined,
        preAuthNumber: preAuthNumberFilter || undefined
      };

      const data = await reportsService.getProviderSettlementReport(params);
      setReportData(data);

      // Expand all claims by default
      const expanded = {};
      (data?.claims || []).forEach((claim) => {
        expanded[claim.claimId] = true;
      });
      setExpandedClaims(expanded);
    } catch (err) {
      console.error('Error fetching provider settlement report:', err);
      setError(err.userMessage || err.message || 'فشل في تحميل التقرير');
    } finally {
      setLoading(false);
    }
  }, [selectedProviderId, dateFrom, dateTo, selectedStatuses, claimNumberFilter, preAuthNumberFilter]);

  // Auto-fetch when provider changes
  useEffect(() => {
    if (selectedProviderId) {
      fetchReport();
    }
  }, [selectedProviderId]); // Only on provider change, not all filters

  // Print handler
  const handlePrint = useReactToPrint({
    contentRef: printRef,
    documentTitle: `تقرير_تسوية_${reportData?.providerName || 'مقدم_الخدمة'}_${new Date().toISOString().split('T')[0]}`,
    pageStyle: `
      @page {
        size: A4 portrait;
        margin: 10mm;
      }
      @media print {
        body { 
          direction: rtl;
          font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif !important;
          -webkit-print-color-adjust: exact !important; 
          print-color-adjust: exact !important; 
        }
        .no-print { display: none !important; }
        
        /* Strict B&W for report */
        .print-only { display: block !important; }
        .print-header { text-align: center; margin-bottom: 20px; }
        .print-title { font-size: 18px; font-weight: bold; }
        
        table { 
          width: '6.25rem'%;
          border-collapse: collapse; 
          font-size: 9px;
          border: 1px solid #000 !important;
        }
        th, td { 
          border: 1px solid #000 !important;
          padding: 4px !important;
          color: #000 !important;
        }
        th { background: #eee !important; -webkit-print-color-adjust: exact; }
        
        .summary-box {
          border: 2px solid #000;
          padding: 10px;
          margin-bottom: 20px;
        }
        
        .kpi-row {
          display: flex;
          justify-content: space-around;
          margin-bottom: 15px;
        }
        .kpi-item {
          text-align: center;
          border: 1px solid #000;
          padding: 10px;
          flex: 1;
        }
      }
    `
  });

  // Export to Excel
  const handleExportExcel = () => {
    if (!reportData?.claims) return;

    // Flatten data for Excel export
    const flatData = [];
    reportData.claims.forEach((claim) => {
      claim.lines.forEach((line) => {
        flatData.push({
          'رقم المطالبة': claim.claimNumber,
          'رقم الموافقة': claim.preAuthNumber || '-',
          'اسم المنتفع': claim.patientNameArabic || claim.patientName,
          'رقم التأمين': claim.insuranceNumber || '-',
          'الخدمة الطبية': line.serviceNameArabic || line.serviceName,
          'تاريخ الخدمة': line.serviceDate,
          الكمية: line.quantity,
          'سعر الوحدة': line.unitPrice,
          'المبلغ الإجمالي (Gross)': line.grossAmount,
          'المبلغ المعتمد (Net)': line.approvedAmount,
          'المبلغ المرفوض': line.rejectedAmount,
          'سبب الرفض': line.rejectionReason || '-',
          'حصة المؤمن': claim.patientShare || 0,
          الحالة: line.lineStatusArabic
        });
      });
      // Add subtotal row for claim
      flatData.push({
        'رقم المطالبة': `إجمالي المطالبة ${claim.claimNumber}`,
        'المبلغ الإجمالي (Gross)': claim.grossAmount,
        'المبلغ المعتمد (Net)': claim.netAmount,
        'المبلغ المرفوض': claim.rejectedAmount,
        'حصة المؤمن': claim.patientShare || 0,
        'مستحق للمرفق': (claim.netAmount || 0) - (claim.patientShare || 0),
        الحالة: claim.statusArabic
      });
    });

    // Add grand totals row
    flatData.push({
      'رقم المطالبة': '=== الإجمالي الكلي ===',
      'المبلغ الإجمالي (Gross)': reportData.totalRequestedAmount,
      'المبلغ المعتمد (Net)': reportData.totalApprovedAmount,
      'المبلغ المرفوض': reportData.totalRejectedAmount,
      'حصة المؤمن': reportData.totalPatientShare,
      'مستحق للمرفق': reportData.netProviderAmount
    });

    const fileName = `تقرير_تسوية_${reportData.providerName || 'مقدم_الخدمة'}_${new Date().toISOString().split('T')[0]}`;
    exportToExcel(flatData, fileName, {
      companyName,
      reportTitle: 'تقرير تسوية مقدم الخدمة',
      summaryCards: [
        {
          label: 'إجمالي المطلوب (Gross)',
          value: formatCurrency(reportData.totalRequestedAmount),
          bgColor: '#E0F2FE',
          textColor: '#0369A1'
        },
        {
          label: 'إجمالي المعتمد (Net)',
          value: formatCurrency(reportData.totalApprovedAmount),
          bgColor: '#ECFDF5',
          textColor: '#047857'
        },
        {
          label: 'إجمالي المرفوض',
          value: formatCurrency(reportData.totalRejectedAmount),
          bgColor: '#FEE2E2',
          textColor: '#B91C1C'
        },
        {
          label: 'حصة المنتفع',
          value: formatCurrency(reportData.totalPatientShare),
          bgColor: '#FEF3C7',
          textColor: '#B45309'
        },
        {
          label: 'صافي المستحق للمرفق',
          value: formatCurrency(reportData.netProviderAmount),
          bgColor: '#0F766E',
          textColor: '#FFFFFF'
        }
      ],
      footerNotes: [
        'عليه، يرجى من سيادتكم تسوية الملاحظات والنواقص خلال مدة أقصاها أسبوعين من تاريخ الاستلام لتسوية القيمة المستحقة نهائياً.',
        'والسلام عليكم - قسم المراجعة والتدقيق'
      ]
    });
  };

  // PDF export disabled - Excel is the official reporting format
  // const handleExportPDF = () => {
  //   if (!reportData?.claims) return;
  //
  //   const flatData = [];
  //   reportData.claims.forEach(claim => {
  //     claim.lines.forEach(line => {
  //       flatData.push({
  //         'رقم المطالبة': claim.claimNumber,
  //         'الخدمة': line.serviceNameArabic || line.serviceName,
  //         'Gross': line.grossAmount,
  //         'Net': line.approvedAmount,
  //         'مرفوض': line.rejectedAmount
  //       });
  //     });
  //   });
  //
  //   const title = `تقرير تسوية ${reportData.providerName}`;
  //   exportToPDF(flatData, title, { companyName, primaryColor });
  // };

  // Toggle claim expansion
  const toggleClaimExpansion = (claimId) => {
    setExpandedClaims((prev) => ({
      ...prev,
      [claimId]: !prev[claimId]
    }));
  };

  // Reset filters
  const handleResetFilters = () => {
    setDateFrom('');
    setDateTo('');
    setSelectedStatuses([]);
    setClaimNumberFilter('');
    setPreAuthNumberFilter('');
  };

  const handleStatusesChange = (event) => {
    const value = event.target.value;
    if (value.includes('__ALL__')) {
      setSelectedStatuses([]);
      return;
    }
    setSelectedStatuses(value);
  };

  // Format currency
  const formatCurrency = (amount) => {
    if (amount == null) return '0.000 د.ل';
    return `${Number(amount).toLocaleString('en-US', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} د.ل`;
  };

  // Format date
  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    try {
      return new Date(dateStr).toLocaleDateString('en-GB');
    } catch {
      return dateStr;
    }
  };

  const formatLYD = (val) => `${(val || 0).toLocaleString('en-US', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} د.ل`;

  // Get status chip color
  const getStatusChipProps = (status) => {
    switch (status) {
      case 'APPROVED':
        return { color: 'success', icon: <ApprovedIcon fontSize="small" /> };
      case 'PARTIAL':
        return { color: 'warning', icon: <PartialIcon fontSize="small" /> };
      case 'REJECTED':
        return { color: 'error', icon: <RejectedIcon fontSize="small" /> };
      case 'PENDING':
        return { color: 'info', icon: <PendingIcon fontSize="small" /> };
      default:
        return { color: 'default', icon: null };
    }
  };

  return (
    <MainCard>
      {/* Header */}
      <ModernPageHeader
        titleKey="تقرير التسوية الموحد (كشف حساب)"
        titleIcon={<ProviderIcon />}
        subtitleKey="تقرير مراجعة مالية شامل يطابق المستندات الورقية الرسمية"
        actions={
          <Stack direction="row" spacing={1} className="no-print">
            <Tooltip title="تحديث">
              <IconButton onClick={fetchReport} disabled={loading || !selectedProviderId}>
                <RefreshIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title="طباعة">
              <IconButton onClick={handlePrint} disabled={!reportData}>
                <PrintIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title="تصدير Excel">
              <IconButton onClick={handleExportExcel} disabled={!reportData}>
                <ExcelIcon />
              </IconButton>
            </Tooltip>
          </Stack>
        }
      />

      {/* Filters */}
      <Card sx={{ mb: '1.5rem' }} className="no-print">
        <CardContent>
          <Grid container spacing={2} alignItems="center">
            {/* Provider Selector - only for admin */}
            <Grid size={{ xs: 12, md: 3 }}>
              <FormControl fullWidth size="small">
                <InputLabel>مقدم الخدمة *</InputLabel>
                <Select
                  value={selectedProviderId}
                  onChange={(e) => setSelectedProviderId(e.target.value)}
                  input={<OutlinedInput label="مقدم الخدمة *" />}
                  disabled={!isAdmin && providers.length === 1}
                >
                  {providers.map((provider) => (
                    <MenuItem key={provider.id} value={provider.id}>
                      {provider.name || provider.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Date From */}
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                fullWidth
                size="small"
                type="date"
                label="من تاريخ"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>

            {/* Date To */}
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                fullWidth
                size="small"
                type="date"
                label="إلى تاريخ"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>

            {/* Status Filter */}
            <Grid size={{ xs: 12, md: 2 }}>
              <FormControl fullWidth size="small">
                <InputLabel>الحالة</InputLabel>
                <Select
                  multiple
                  value={selectedStatuses}
                  onChange={handleStatusesChange}
                  input={<OutlinedInput label="الحالة" />}
                  renderValue={(selected) =>
                    selected.length === 0 ? 'جميع الحالات' : selected.map((status) => getStatusLabel(status)).join('، ')
                  }
                >
                  <MenuItem value="__ALL__">جميع الحالات</MenuItem>
                  {STATUS_OPTIONS.map((statusOption) => (
                    <MenuItem key={statusOption.value} value={statusOption.value}>
                      {statusOption.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Search Actions */}
            <Grid size={{ xs: 12, md: 3 }}>
              <Stack direction="row" spacing={1}>
                <Button variant="contained" startIcon={<FilterIcon />} onClick={fetchReport} disabled={loading || !selectedProviderId}>
                  بحث وتحديث
                </Button>
                <Button variant="outlined" startIcon={<ClearAllIcon />} onClick={handleResetFilters}>
                  مسح
                </Button>
              </Stack>
            </Grid>

            {/* Quick Date Presets */}
            <Grid size={12}>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label="هذا الشهر" variant="outlined" size="small" clickable onClick={() => setQuickDateRange('THIS_MONTH')} />
                <Chip label="الشهر السابق" variant="outlined" size="small" clickable onClick={() => setQuickDateRange('PREVIOUS_MONTH')} />
                <Chip label="السنة الحالية" variant="outlined" size="small" clickable onClick={() => setQuickDateRange('THIS_YEAR')} />
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Report Content */}
      <Box ref={printRef} className="report-root">
        {loading ? (
          <Box className="no-print">
            <Skeleton variant="rectangular" height={100} sx={{ mb: '1.0rem' }} />
            <Skeleton variant="rectangular" height={400} />
          </Box>
        ) : reportData ? (
          <>
            {/* Screen-Only Summary (Dashboard Style) */}
            <Grid container spacing={2} sx={{ mb: '1.5rem' }} className="no-print">
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ bgcolor: 'info.lighter', textAlign: 'center', p: '1.0rem' }}>
                  <Typography variant="caption" color="info.main" fontWeight="bold">إجمالي المطالب (له)</Typography>
                  <Typography variant="h5" fontWeight="bold">{formatLYD(reportData.totalRequestedAmount)}</Typography>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ bgcolor: 'error.lighter', textAlign: 'center', p: '1.0rem' }}>
                  <Typography variant="caption" color="error.main" fontWeight="bold">استقطاعات ورفض (عليه)</Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {formatLYD((reportData.totalRejectedAmount || 0) + (reportData.totalPatientShare || 0))}
                  </Typography>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ bgcolor: 'warning.lighter', textAlign: 'center', p: '1.0rem' }}>
                  <Typography variant="caption" color="warning.main" fontWeight="bold">نسبة الخصم</Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {reportData.totalRequestedAmount > 0
                      ? ((reportData.totalRejectedAmount / reportData.totalRequestedAmount) * 100).toFixed(2)
                      : 0}%
                  </Typography>
                </Card>
              </Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Card sx={{ bgcolor: 'primary.main', textAlign: 'center', p: '1.0rem', color: 'white' }}>
                  <Typography variant="caption" sx={{ opacity: 0.9, fontWeight: 'bold' }}>صافي المستحق للمرفق</Typography>
                  <Typography variant="h5" fontWeight="bold">{formatLYD(reportData.netProviderAmount)}</Typography>
                </Card>
              </Grid>
            </Grid>

            {/* PRINT & SCREEN: THE CLEAN PAPER FORMAT */}
            <Box className="paper-printable-content">
              <style type="text/css">
                {`
                        @media screen {
                            .paper-printable-content { padding: 20px; background: #fff; border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); }
                        }
                        .report-header-box { text-align: center; margin-bottom: 25px; }
                        .report-title-main { font-size: 20px; font-weight: bold; margin-bottom: 4px; }
                        .patient-info-block { border: 1px solid #000; margin-bottom: 15px; font-size: 11px; }
                        .pi-row { display: flex; border-bottom: 1px solid #000; }
                        .pi-row:last-child { border-bottom: none; }
                        .pi-col { flex: 1; padding: 6px 12px; border-left: 1px solid #000; }
                        .pi-col:last-child { border-left: none; }
                        
                        table.report-table { width: '6.25rem'%; border-collapse: collapse; font-size: 10px; margin-bottom: 5px; }
                        table.report-table th, table.report-table td { border: 1px solid #000; padding: 6px; text-align: center; color: #000; }
                        table.report-table th { background: #f2f2f2 !important; font-weight: bold; -webkit-print-color-adjust: exact; }
                        table.report-table .subtotal-row { font-weight: bold; background: #fafafa; }
                        
                        .global-report-footer { display: flex; border: 2px solid #000; margin-top: 20px; font-weight: bold; }
                        .footer-col { flex: 1; padding: 10px; text-align: center; border-left: 1px solid #000; }
                        .footer-col:last-child { border-left: none; }
                    `}
              </style>

              {/* Report Top Meta with Logo */}
              <Box className="report-header-box">
                  <Stack direction="row" justifyContent="center" sx={{ mb: '1.0rem' }}>
                       {/* Professional Logo Placeholder */}
                       <Box sx={{ width: '5.0rem', height: '3.125rem', border: '3px solid #000', borderRadius: '40%', position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <Typography variant="caption" fontWeight="bold" sx={{ color: '#000', fontSize: '0.75rem' }}>WAAD TPA</Typography>
                       </Box>
                  </Stack>
                  <Typography className="report-title-main">شركة وعد لإدارة النفقات الطبية</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 'bold' }}>تقرير التسوية المالية الموحد (كشف حساب)</Typography>
                  <Typography variant="body1" fontWeight="bold" sx={{ mt: 1 }}>{reportData.providerName}</Typography>
                  <Typography variant="body2">الفترة: من {formatDate(reportData.fromDate)} إلى {formatDate(reportData.toDate)}</Typography>
              </Box>

              {reportData.claims?.map((claim, idx) => (
                <Box key={claim.claimId} sx={{ mb: '2.0rem', pageBreakInside: 'avoid' }}>
                  <div className="patient-info-block">
                    <div className="pi-row">
                      <div className="pi-col" style={{ flex: 0.4 }}><strong>No.:</strong> {idx + 1}</div>
                      <div className="pi-col"><strong>Originator No.:</strong> {claim.claimNumber}</div>
                    </div>
                    <div className="pi-row">
                      <div className="pi-col"><strong>Insurance No:</strong> {claim.insuranceNumber || '-'}</div>
                      <div className="pi-col"><strong>Patient Name:</strong> {claim.patientNameArabic || claim.patientName}</div>
                    </div>
                  </div>

                  <table className="report-table">
                    <thead>
                      <tr>
                        <th style={{ width: '35%' }}>Medical Service (الخدمة الطبية)</th>
                        <th style={{ width: '12%' }}>Date</th>
                        <th style={{ width: '10%' }}>Gross</th>
                        <th style={{ width: '10%' }}>Net</th>
                        <th style={{ width: '10%' }}>Rejected</th>
                        <th style={{ width: '23%' }}>Rejection Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {claim.lines?.map((line, lIdx) => (
                        <tr key={lIdx}>
                          <td style={{ textAlign: 'right' }}>{line.serviceNameArabic || line.serviceName}</td>
                          <td>{formatDate(line.serviceDate)}</td>
                          <td>{formatLYD(line.grossAmount)}</td>
                          <td>{formatLYD(line.approvedAmount)}</td>
                          <td>{formatLYD(line.rejectedAmount)}</td>
                          <td style={{ textAlign: 'right', fontSize: '0.75rem' }}>{line.rejectionReason || '-'}</td>
                        </tr>
                      ))}
                      <tr className="subtotal-row">
                        <td colSpan={2} style={{ textAlign: 'left' }}>SUBTOTAL (الإجمالي الفرعي)</td>
                        <td>{formatLYD(claim.grossAmount)}</td>
                        <td>{formatLYD(claim.netAmount)}</td>
                        <td>{formatLYD(claim.rejectedAmount)}</td>
                        <td></td>
                      </tr>
                    </tbody>
                  </table>
                </Box>
              ))}

              <div className="global-report-footer">
                <div className="footer-col">GRAND TOTAL (الإجمالي الكلي)</div>
                <div className="footer-col">GROSS: {formatLYD(reportData.totalRequestedAmount)}</div>
                <div className="footer-col">NET: {formatLYD(reportData.totalApprovedAmount)}</div>
                <div className="footer-col" style={{ color: 'red' }}>REJECTED: {formatLYD(reportData.totalRejectedAmount)}</div>
              </div>

              <Box sx={{ mt: '1.5rem', pt: '1.0rem', borderTop: '1px dashed #000', textAlign: 'center', fontSize: '0.75rem' }}>
                <Typography variant="body2">حرر هذا التقرير بتاريخ {formatDate(new Date())} بواسطة قسم المراجعة والتدقيق المالي</Typography>
                <Typography variant="caption">شركة وعد لإدارة النفقات الطبية - جميع الحقوق محفوظة ©</Typography>
              </Box>
            </Box>
          </>
        ) : (
          <Alert severity="info" className="no-print">يرجى اختيار مقدم الخدمة والنقر على "بحث وتحديث" لعرض التقرير الموحد</Alert>
        )}
      </Box>
    </MainCard>
  );
};

export default ProviderSettlementReport;


