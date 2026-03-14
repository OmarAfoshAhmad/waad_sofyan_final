/**
 * Provider Account View Page - Phase 3B Settlement (FIXED)
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║              PROVIDER ACCOUNT VIEW - FIXED VERSION                            ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Shows provider account details with transaction history                       ║
 * ║ Uses MUI DataGrid instead of GenericDataTable to avoid React errors           ║
 * ║                                                                               ║
 * ║ BUG FIXES:                                                                    ║
 * ║ ✅ Fixed: Objects are not valid as React child                                ║
 * ║ ✅ Fixed: All values converted to strings                                     ║
 * ║ ✅ Fixed: Safe optional chaining for all nested properties                    ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */

import { useState, useMemo, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';

// MUI Components
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField
} from '@mui/material';

// MUI Icons
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import RefreshIcon from '@mui/icons-material/Refresh';
import VerifiedIcon from '@mui/icons-material/Verified';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import PrintIcon from '@mui/icons-material/Print';
import TableChartIcon from '@mui/icons-material/TableChart';
import PaymentIcon from '@mui/icons-material/Payment';

// Project Components
import MainCard from 'components/MainCard';
import UnifiedPageHeader from 'components/UnifiedPageHeader';
import PermissionGuard from 'components/PermissionGuard';
import { UnifiedMedicalTable } from 'components/common';

// Services
import { providerAccountsService, providerPaymentsService } from 'services/api/settlement.service';

// Utils
import { exportProviderAccountTransactionsToExcel } from 'utils/settlementExcelExport';

// Snackbar
import { openSnackbar } from 'api/snackbar';

// ============================================================================
// CONSTANTS
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50];

// Transaction type labels in Arabic
const TRANSACTION_TYPE_LABELS = {
  CREDIT: 'دائن',
  DEBIT: 'مدين',
  PAYMENT: 'خصم (مدين)',
  ADJUSTMENT: 'تسوية'
};

// Transaction type colors
const TRANSACTION_TYPE_COLORS = {
  CREDIT: 'success',
  DEBIT: 'error',
  PAYMENT: 'primary',
  ADJUSTMENT: 'warning'
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Format currency with LYD - ALWAYS returns string
 */
const formatCurrency = (value) => {
  if (value === null || value === undefined || isNaN(value)) return '0 د.ل';
  return `${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} د.ل`;
};

/**
 * Format datetime for display - ALWAYS returns string
 */
const formatDateTime = (date) => {
  if (!date) return '-';
  try {
    return new Date(date).toLocaleString('ar-LY', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch {
    return String(date);
  }
};

/**
 * Get balance color based on amount
 */
const getBalanceColor = (balance) => {
  if (balance > 0) return 'error.main';
  if (balance < 0) return 'success.main';
  return 'text.primary';
};

/**
 * Safely get provider name from various data structures
 */
const getProviderName = (account) => {
  if (!account) return 'مقدم الخدمة';
  return (
    account.providerName || account.provider?.name || account.provider?.nameArabic || `مقدم خدمة #${account.providerId || account.id || ''}`
  );
};

const resolveDebitCredit = (transactionType, amount) => {
  const numericAmount = Number(amount) || 0;
  const absAmount = Math.abs(numericAmount);

  if (transactionType === 'CREDIT') return { creditAmount: absAmount, debitAmount: 0 };
  if (transactionType === 'DEBIT' || transactionType === 'PAYMENT') return { creditAmount: 0, debitAmount: absAmount };

  if (transactionType === 'ADJUSTMENT') {
    return numericAmount >= 0 ? { creditAmount: absAmount, debitAmount: 0 } : { creditAmount: 0, debitAmount: absAmount };
  }

  return numericAmount >= 0 ? { creditAmount: absAmount, debitAmount: 0 } : { creditAmount: 0, debitAmount: absAmount };
};

const calculateTransactionTotals = (transactions) => {
  if (!Array.isArray(transactions) || transactions.length === 0) {
    return {
      totalCredit: 0,
      totalDebit: 0,
      netMovement: 0
    };
  }

  const totalCredit = transactions.reduce((sum, tx) => sum + (Number(tx.creditAmount) || 0), 0);
  const totalDebit = transactions.reduce((sum, tx) => sum + (Number(tx.debitAmount) || 0), 0);
  const netMovement = totalCredit - totalDebit;

  return {
    totalCredit,
    totalDebit,
    netMovement
  };
};

const applyFallbackRunningBalance = (transactions, currentRunningBalance, isFirstPage = true) => {
  if (!Array.isArray(transactions) || transactions.length === 0) return [];

  const hasRealRunningBalance = transactions.some((tx) => {
    const value = Number(tx.runningBalanceAfter);
    return Number.isFinite(value) && value !== 0;
  });

  if (hasRealRunningBalance) return transactions;
  if (!isFirstPage) return transactions;

  const currentBalance = Number(currentRunningBalance) || 0;
  const parseTime = (value) => {
    const timestamp = Date.parse(value || '');
    return Number.isFinite(timestamp) ? timestamp : null;
  };

  const firstTime = parseTime(transactions[0]?.createdAt);
  const lastTime = parseTime(transactions[transactions.length - 1]?.createdAt);
  const isDescending = firstTime !== null && lastTime !== null ? firstTime >= lastTime : true;

  if (isDescending) {
    let rollingBalance = currentBalance;
    return transactions.map((tx) => {
      const txWithBalance = { ...tx, runningBalanceAfter: rollingBalance };
      const effect = (Number(tx.creditAmount) || 0) - (Number(tx.debitAmount) || 0);
      rollingBalance -= effect;
      return txWithBalance;
    });
  }

  const totalEffect = transactions.reduce((sum, tx) => sum + (Number(tx.creditAmount) || 0) - (Number(tx.debitAmount) || 0), 0);
  let rollingBalance = currentBalance - totalEffect;

  return transactions.map((tx) => {
    const effect = (Number(tx.creditAmount) || 0) - (Number(tx.debitAmount) || 0);
    rollingBalance += effect;
    return { ...tx, runningBalanceAfter: rollingBalance };
  });
};

const appendTotalsRow = (transactions, totals, suffix) => {
  const rows = Array.isArray(transactions) ? transactions : [];
  return [
    ...rows,
    {
      id: `totals-row-${suffix}`,
      isTotalsRow: true,
      creditAmount: Number(totals?.totalCredit) || 0,
      debitAmount: Number(totals?.totalDebit) || 0,
      runningBalanceAfter: Number(totals?.netMovement) || 0
    }
  ];
};

// ============================================================================
// TAB PANEL COMPONENT
// ============================================================================

const TabPanel = ({ children, value, index, ...other }) => (
  <Box role="tabpanel" hidden={value !== index} id={`account-tabpanel-${index}`} aria-labelledby={`account-tab-${index}`} {...other}>
    {value === index && <Box sx={{ pt: '1.0rem' }}>{children}</Box>}
  </Box>
);

// ============================================================================
// ACCOUNT SUMMARY CARD
// ============================================================================

const AccountSummaryCard = ({ account, isLoading }) => {
  if (isLoading) {
    return (
      <Card sx={{ mb: '1.5rem' }}>
        <CardContent>
          <Stack spacing={2}>
            <Skeleton variant="text" width="60%" height={40} />
            <Grid container spacing={3}>
              {[1, 2, 3, 4].map((i) => (
                <Grid key={i} size={{ xs: 12, sm: 6, md: 3 }}>
                  <Skeleton variant="rectangular" height={100} />
                </Grid>
              ))}
            </Grid>
          </Stack>
        </CardContent>
      </Card>
    );
  }

  if (!account) {
    return (
      <Alert severity="warning" sx={{ mb: '1.5rem' }}>
        لم يتم العثور على بيانات الحساب
      </Alert>
    );
  }

  const providerName = getProviderName(account);
  const providerId = account?.providerId || account?.id || '';
  const runningBalance = Number(account?.runningBalance) || 0;
  const totalApproved = Number(account?.totalApproved) || 0;
  const totalPaid = Number(account?.totalPaid) || 0;
  const transactionCount = Number(account?.transactionCount) || 0;

  return (
    <Card sx={{ mb: '1.5rem' }}>
      <CardContent>
        {/* Provider Name */}
        <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: '1.5rem' }}>
          <AccountBalanceWalletIcon sx={{ fontSize: '2.5rem', color: 'primary.main' }} />
          <Box>
            <Typography variant="h4" fontWeight={600}>
              {String(providerName)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {`حساب مقدم الخدمة #${String(providerId)}`}
            </Typography>
          </Box>
        </Stack>

        <Divider sx={{ mb: '1.5rem' }} />

        {/* Financial Summary */}
        <Grid container spacing={3}>
          {/* Running Balance */}
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Paper
              elevation={0}
              sx={{
                p: '1.0rem',
                bgcolor: runningBalance > 0 ? 'error.lighter' : 'success.lighter',
                borderRadius: '0.125rem',
                textAlign: 'center'
              }}
            >
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                الرصيد الحالي
              </Typography>
              <Typography variant="h4" fontWeight={700} sx={{ color: getBalanceColor(runningBalance) }}>
                {formatCurrency(runningBalance)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {runningBalance > 0 ? 'مستحق للمقدم' : runningBalance < 0 ? 'مستحق على المقدم' : 'لا رصيد'}
              </Typography>
            </Paper>
          </Grid>

          {/* Total Approved */}
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'primary.lighter', borderRadius: '0.125rem', textAlign: 'center' }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                إجمالي المعتمد
              </Typography>
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
                <TrendingUpIcon color="primary" />
                <Typography variant="h4" fontWeight={700} color="primary.main">
                  {formatCurrency(totalApproved)}
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                مجموع المطالبات المعتمدة
              </Typography>
            </Paper>
          </Grid>

          {/* Total Paid */}
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'success.lighter', borderRadius: '0.125rem', textAlign: 'center' }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                إجمالي المدفوع
              </Typography>
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
                <TrendingDownIcon color="success" />
                <Typography variant="h4" fontWeight={700} color="success.main">
                  {formatCurrency(totalPaid)}
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                مجموع المدفوعات المحولة
              </Typography>
            </Paper>
          </Grid>

          {/* Transaction Count */}
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Paper elevation={0} sx={{ p: '1.0rem', bgcolor: 'grey.100', borderRadius: '0.125rem', textAlign: 'center' }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                عدد الحركات
              </Typography>
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={1}>
                <HistoryIcon color="action" />
                <Typography variant="h4" fontWeight={700}>
                  {String(transactionCount)}
                </Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                إجمالي الحركات المالية
              </Typography>
            </Paper>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const ProviderAccountView = () => {
  const { providerId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // Guard: providerId must be a valid integer. If not (e.g. "actions" from a mismatched route),
  // skip all API calls and redirect back to the list page.
  const isValidProvider = !!providerId && !isNaN(Number(providerId)) && Number.isInteger(Number(providerId));

  useEffect(() => {
    if (providerId && !isValidProvider) {
      navigate('/settlement/provider-payments', { replace: true });
    }
  }, [providerId, isValidProvider, navigate]);

  const [activeTab, setActiveTab] = useState(0);
  const [verificationResult, setVerificationResult] = useState(null);
  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 10 });
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [paymentForm, setPaymentForm] = useState({ amount: '', paymentReference: '', notes: '' });
  const [isSubmittingPayment, setIsSubmittingPayment] = useState(false);

  // ========================================
  // DATA FETCHING
  // ========================================

  // Fetch account summary
  const {
    data: accountData,
    isLoading: isLoadingAccount,
    isError: isAccountError,
    error: accountError
  } = useQuery({
    queryKey: ['provider-account', providerId],
    queryFn: () => providerAccountsService.getByProviderId(providerId),
    enabled: isValidProvider,
    staleTime: 1000 * 60 * 2
  });

  // Fetch transactions
  const {
    data: transactionsData,
    isLoading: isLoadingTransactions
  } = useQuery({
    queryKey: ['provider-account', providerId, 'transactions', paginationModel.page, paginationModel.pageSize],
    queryFn: () =>
      providerAccountsService.getTransactions(providerId, {
        page: paginationModel.page,
        size: paginationModel.pageSize
      }),
    enabled: isValidProvider,
    staleTime: 1000 * 60 * 2
  });

  // Fetch recent transactions for quick view
  const { data: recentTransactionsRaw, isLoading: isLoadingRecent } = useQuery({
    queryKey: ['provider-account', providerId, 'recent'],
    queryFn: () => providerAccountsService.getRecentTransactions(providerId),
    enabled: isValidProvider,
    staleTime: 1000 * 60 * 2
  });

  // ========================================
  // PROCESS TRANSACTIONS DATA
  // ========================================

  const processTransactions = useCallback((rawData) => {
    if (!rawData) return [];
    const list = Array.isArray(rawData) ? rawData : rawData?.content || [];
    return list.map((tx, index) => {
      const transactionType = tx.transactionType || 'UNKNOWN';
      const amount = Number(tx.amount) || 0;
      const { creditAmount, debitAmount } = resolveDebitCredit(transactionType, amount);

      return {
        id: tx.id || `tx-${index}`,
        createdAt: tx.createdAt,
        transactionType,
        amount,
        creditAmount,
        debitAmount,
        runningBalanceAfter: Number(tx.runningBalanceAfter) || 0,
        referenceType: tx.referenceType || '-',
        referenceId: tx.referenceId || '',
        description: tx.description || ''
      };
    });
  }, []);

  const recentTransactions = useMemo(() => {
    const processed = processTransactions(recentTransactionsRaw);
    return applyFallbackRunningBalance(processed, accountData?.runningBalance, true);
  }, [recentTransactionsRaw, processTransactions, accountData]);

  const allTransactions = useMemo(() => {
    const content = transactionsData?.items || transactionsData?.content || transactionsData;
    const processed = processTransactions(content);
    return applyFallbackRunningBalance(processed, accountData?.runningBalance, paginationModel.page === 0);
  }, [transactionsData, processTransactions, accountData, paginationModel.page]);

  const totalTransactions = transactionsData?.total || transactionsData?.totalElements || allTransactions.length;
  const recentTotals = useMemo(() => calculateTransactionTotals(recentTransactions), [recentTransactions]);
  const allTotals = useMemo(() => calculateTransactionTotals(allTransactions), [allTransactions]);
  const recentRowsWithTotals = useMemo(() => appendTotalsRow(recentTransactions, recentTotals, 'recent'), [recentTransactions, recentTotals]);
  const allRowsWithTotals = useMemo(() => appendTotalsRow(allTransactions, allTotals, `all-${paginationModel.page}`), [allTransactions, allTotals, paginationModel.page]);

  // ========================================
  // HANDLERS
  // ========================================

  const handleBack = useCallback(() => {
    navigate('/settlement/provider-payments');
  }, [navigate]);

  const handleRefresh = useCallback(() => {
    // Invalidate ALL queries related to this provider account in one call.
    // This covers: account summary, paginated transactions, AND recent transactions.
    queryClient.invalidateQueries({ queryKey: ['provider-account', providerId] });
    openSnackbar({
      message: 'جاري تحديث البيانات...',
      variant: 'info'
    });
  }, [queryClient, providerId]);

  const handleVerifyBalance = useCallback(async () => {
    const accountId = accountData?.accountId || accountData?.id;
    if (!accountId) return;

    try {
      const result = await providerAccountsService.verifyBalance(accountId);
      setVerificationResult(result);
      const isValid = result?.balanceVerified ?? result?.isValid;
      openSnackbar({
        message: isValid ? 'الرصيد متطابق ✓' : 'يوجد فرق في الرصيد!',
        variant: isValid ? 'success' : 'warning'
      });
    } catch (error) {
      openSnackbar({
        message: error?.message || 'فشل التحقق من الرصيد',
        variant: 'error'
      });
    }
  }, [accountData]);

  const handlePaymentSubmit = async () => {
    if (!paymentForm.amount || !paymentForm.paymentReference) {
      openSnackbar({ message: 'الرجاء تعبئة الحقول المطلوبة', variant: 'warning' });
      return;
    }

    // Amount can not be greater than outstanding balance
    const currentBalance = Number(accountData?.runningBalance) || 0;
    const amountVal = Number(paymentForm.amount);
    
    if (amountVal > currentBalance) {
      openSnackbar({ message: 'المبلغ المدخل يجب ألا يتجاوز رصيد مقدم الخدمة', variant: 'error' });
      return;
    }

    setIsSubmittingPayment(true);
    try {
      await providerPaymentsService.createProviderInstallment(providerId, paymentForm);
      openSnackbar({ message: 'تم تسجيل الدفعة بنجاح', variant: 'success' });
      setIsPaymentModalOpen(false);
      setPaymentForm({ amount: '', paymentReference: '', notes: '' });
      // Invalidate ALL provider-account related queries so balances and all transaction tabs update.
      queryClient.invalidateQueries({ queryKey: ['provider-account', providerId] });
    } catch (error) {
      openSnackbar({ message: error?.message || 'حدث خطأ أثناء تسجيل الدفعة', variant: 'error' });
    } finally {
      setIsSubmittingPayment(false);
    }
  };

  const handleTabChange = (_, newValue) => {
    setActiveTab(newValue);
  };

  const buildPrintableRows = useCallback((rows) => {
    const safeRows = Array.isArray(rows) ? rows.filter((row) => !row.isTotalsRow) : [];
    return safeRows
      .map(
        (tx) => `
          <tr>
            <td>${formatDateTime(tx.createdAt)}</td>
            <td>${TRANSACTION_TYPE_LABELS[tx.transactionType] || String(tx.transactionType || '-')}</td>
            <td>${Number(tx.creditAmount) > 0 ? formatCurrency(tx.creditAmount) : '-'}</td>
            <td>${Number(tx.debitAmount) > 0 ? formatCurrency(tx.debitAmount) : '-'}</td>
            <td>${formatCurrency(tx.runningBalanceAfter)}</td>
            <td>${String(tx.referenceType || '-')}${tx.referenceId ? ` #${String(tx.referenceId)}` : ''}</td>
            <td>${String(tx.description || '-')}</td>
          </tr>
        `
      )
      .join('');
  }, []);

  const handlePrint = useCallback(() => {
    const providerName = getProviderName(accountData);
    const rows = activeTab === 0 ? recentTransactions : allTransactions;
    const totals = activeTab === 0 ? recentTotals : allTotals;
    const title = activeTab === 0 ? 'آخر الحركات' : 'كل الحركات';

    const tableRows = buildPrintableRows(rows);
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;

    const html = `
      <!DOCTYPE html>
      <html lang="ar" dir="rtl">
      <head>
        <meta charset="UTF-8" />
        <title>طباعة حركات الحساب</title>
        <style>
          body { font-family: Arial, sans-serif; margin: 16px; direction: rtl; }
          h2, p { margin: 0 0 10px 0; }
          table { width: '6.25rem'%; border-collapse: collapse; table-layout: fixed; }
          th, td { border: 1px solid #cbd5e1; padding: 6px 8px; font-size: 12px; vertical-align: top; }
          th { background: #e6f4f1; }
          td.desc { white-space: normal; word-break: break-word; }
          tfoot td { font-weight: 700; background: #f8fafc; }
          .center { text-align: center; }
          .right { text-align: right; }
          .credit { color: #15803d; font-weight: 700; }
          .debit { color: #dc2626; font-weight: 700; }
        </style>
      </head>
      <body>
        <h2>تفاصيل حساب مقدم الخدمة</h2>
        <p>${providerName} - ${title}</p>
        <table>
          <thead>
            <tr>
              <th>التاريخ</th>
              <th>نوع الحركة</th>
              <th>الدائن</th>
              <th>المدين</th>
              <th>رصيد الحركة</th>
              <th>المرجع</th>
              <th>الوصف</th>
            </tr>
          </thead>
          <tbody>
            ${tableRows}
          </tbody>
          <tfoot>
            <tr>
              <td></td>
              <td></td>
              <td class="right credit">${formatCurrency(totals.totalCredit)}</td>
              <td class="right debit">${formatCurrency(totals.totalDebit)}</td>
              <td class="right ${totals.netMovement >= 0 ? 'credit' : 'debit'}">${formatCurrency(totals.netMovement)}</td>
              <td></td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </body>
      </html>
    `;

    printWindow.document.write(html);
    printWindow.document.close();
    printWindow.focus();
    setTimeout(() => printWindow.print(), 250);
  }, [accountData, activeTab, recentTransactions, allTransactions, recentTotals, allTotals, buildPrintableRows]);

  // Export to Excel
  const handleExportExcel = useCallback(async () => {
    const dataToExport = activeTab === 0 ? recentTransactions : allTransactions;
    if (!dataToExport.length) return;

    try {
      const transactions = dataToExport
        .filter((tx) => !tx.isTotalsRow)
        .map((tx) => ({
          createdAt: formatDateTime(tx.createdAt),
          transactionTypeLabel: TRANSACTION_TYPE_LABELS[tx.transactionType] || tx.transactionType,
          creditAmount: Number(tx.creditAmount) || 0,
          debitAmount: Number(tx.debitAmount) || 0,
          runningBalanceAfter: Number(tx.runningBalanceAfter) || 0,
          reference: `${String(tx.referenceType || '-')}${tx.referenceId ? ` #${String(tx.referenceId)}` : ''}`,
          description: tx.description || '-'
        }));

      await exportProviderAccountTransactionsToExcel({
        providerName: getProviderName(accountData),
        transactions,
        exportDate: new Date().toLocaleString('ar-LY')
      });

      openSnackbar({ message: 'تم تصدير ملف Excel بنجاح', variant: 'success' });
    } catch (error) {
      openSnackbar({ message: error?.message || 'فشل تصدير ملف Excel', variant: 'error' });
    }
  }, [accountData, activeTab, recentTransactions, allTransactions]);

  // ========================================
  // TABLE COLUMNS (UnifiedMedicalTable format)
  // ========================================

  const transactionColumns = useMemo(
    () => [
      {
        id: 'createdAt',
        label: 'التاريخ',
        minWidth: '9.0625rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'transactionType',
        label: 'نوع الحركة',
        minWidth: '6.875rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'creditAmount',
        label: 'الدائن',
        minWidth: '7.5rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'debitAmount',
        label: 'المدين',
        minWidth: '7.5rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'runningBalanceAfter',
        label: 'رصيد الحركة',
        minWidth: '7.8125rem',
        align: 'right',
        sortable: false
      },
      {
        id: 'referenceType',
        label: 'المرجع',
        minWidth: '6.5625rem',
        align: 'center',
        sortable: false
      },
      {
        id: 'description',
        label: 'الوصف',
        minWidth: '23.75rem',
        width: '40%',
        sortable: false
      }
    ],
    []
  );

  const renderTransactionCell = useCallback((transaction, column) => {
    if (!transaction) return null;

    if (transaction.isTotalsRow) {
      if (column.id === 'creditAmount') {
        return (
          <Typography fontWeight={700} color="success.main">
            {formatCurrency(transaction.creditAmount)}
          </Typography>
        );
      }
      if (column.id === 'debitAmount') {
        return (
          <Typography fontWeight={700} color="error.main">
            {formatCurrency(transaction.debitAmount)}
          </Typography>
        );
      }
      if (column.id === 'runningBalanceAfter') {
        return (
          <Typography fontWeight={700} color={Number(transaction.runningBalanceAfter) >= 0 ? 'success.main' : 'error.main'}>
            {formatCurrency(transaction.runningBalanceAfter)}
          </Typography>
        );
      }
      return '';
    }

    switch (column.id) {
      case 'createdAt':
        return <Typography variant="body2">{formatDateTime(transaction.createdAt)}</Typography>;

      case 'transactionType':
        return (
          <Chip
            label={TRANSACTION_TYPE_LABELS[transaction.transactionType] || String(transaction.transactionType || '-')}
            color={TRANSACTION_TYPE_COLORS[transaction.transactionType] || 'default'}
            size="small"
            variant="filled"
          />
        );

      case 'creditAmount':
        return (
          <Typography fontWeight={600} color="success.main">
            {Number(transaction.creditAmount) > 0 ? `+ ${formatCurrency(transaction.creditAmount)}` : '-'}
          </Typography>
        );

      case 'debitAmount':
        return (
          <Typography fontWeight={600} color="error.main">
            {Number(transaction.debitAmount) > 0 ? `- ${formatCurrency(transaction.debitAmount)}` : '-'}
          </Typography>
        );

      case 'runningBalanceAfter':
        return <Typography fontWeight={600}>{formatCurrency(transaction.runningBalanceAfter)}</Typography>;

      case 'referenceType':
        return (
          <Stack spacing={0.25}>
            <Typography variant="body2">{String(transaction.referenceType || '-')}</Typography>
            {transaction.referenceId ? (
              <Typography variant="caption" color="text.secondary">
                {`#${String(transaction.referenceId)}`}
              </Typography>
            ) : null}
          </Stack>
        );

      case 'description':
        return <Typography variant="body2" sx={{ whiteSpace: 'normal', wordBreak: 'break-word', lineHeight: 1.5 }}>{String(transaction.description || '-')}</Typography>;

      default:
        return String(transaction[column.id] || '-');
    }
  }, []);

  // ========================================
  // BREADCRUMBS
  // ========================================

  const breadcrumbs = [
    { label: 'الرئيسية', path: '/' },
    { label: 'التسويات', path: '/settlement' },
    { label: 'حسابات مقدمي الخدمة', path: '/settlement/provider-accounts' },
    { label: getProviderName(accountData) }
  ];

  // ========================================
  // PAGE ACTIONS
  // ========================================

  const pageActions = (
    <Stack direction="row" spacing={1}>
      <Tooltip title="دفع دفعة مالية">
        <Button 
          variant="contained" 
          color="success" 
          startIcon={<PaymentIcon />} 
          onClick={() => setIsPaymentModalOpen(true)}
          disabled={!accountData || Number(accountData.runningBalance) <= 0}
        >
          دفع دفعة للرصيد المستحق
        </Button>
      </Tooltip>
      <Tooltip title="التحقق من الرصيد">
        <Button variant="outlined" color="info" startIcon={<VerifiedIcon />} onClick={handleVerifyBalance} disabled={!accountData}>
          التحقق من الرصيد
        </Button>
      </Tooltip>
      <Tooltip title="تصدير Excel">
        <IconButton onClick={handleExportExcel} color="success">
          <TableChartIcon />
        </IconButton>
      </Tooltip>
      <Tooltip title="طباعة">
        <IconButton onClick={handlePrint} color="primary">
          <PrintIcon />
        </IconButton>
      </Tooltip>
      <Tooltip title="تحديث">
        <IconButton onClick={handleRefresh} color="primary">
          <RefreshIcon />
        </IconButton>
      </Tooltip>
      <Tooltip title="رجوع">
        <IconButton onClick={handleBack}>
          <ArrowBackIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  );

  // ========================================
  // RENDER
  // ========================================

  if (isAccountError) {
    return (
      <Box>
        <UnifiedPageHeader
          title="تفاصيل الحساب"
          breadcrumbs={breadcrumbs}
          icon={AccountBalanceWalletIcon}
          actions={
            <IconButton onClick={handleBack}>
              <ArrowBackIcon />
            </IconButton>
          }
        />
        <Alert severity="error">{accountError?.message || 'فشل في تحميل بيانات الحساب'}</Alert>
      </Box>
    );
  }

  return (
    <PermissionGuard resource="provider_accounts" action="view" fallback={<Alert severity="error">ليس لديك صلاحية لعرض هذه الصفحة</Alert>}>
      <Box>
        {/* Page Header */}
        <UnifiedPageHeader
          title="تفاصيل حساب مقدم الخدمة"
          subtitle={String(getProviderName(accountData))}
          breadcrumbs={breadcrumbs}
          icon={AccountBalanceWalletIcon}
          actions={pageActions}
        />

        {/* Persistent balance mismatch warning (server-detected) */}
        {accountData && accountData.balanceVerified === false && (
          <Alert severity="error" icon={<ErrorIcon />} sx={{ mb: '1.0rem' }}>
            ⚠️ <strong>تحذير:</strong> الرصيد المحسوب لا يتطابق مع مجموع حركات الحساب. يرجى التحقق من الحركات المالية أو التواصل مع الدعم الفني.
          </Alert>
        )}

        {/* Verification Result */}
        {verificationResult && (
          (() => {
            const isValid = verificationResult.balanceVerified ?? verificationResult.isValid;
            return (
          <Alert
            severity={isValid ? 'success' : 'warning'}
            icon={isValid ? <CheckCircleIcon /> : <ErrorIcon />}
            sx={{ mb: '1.0rem' }}
            onClose={() => setVerificationResult(null)}
          >
            {isValid ? 'الرصيد متطابق مع مجموع الحركات' : 'يوجد عدم تطابق في الرصيد، يرجى مراجعة الحركات المالية.'}
          </Alert>
            );
          })()
        )}

        {/* Account Summary */}
        <AccountSummaryCard account={accountData} isLoading={isLoadingAccount} />

        {/* Tabs */}
        <MainCard>
          <Tabs value={activeTab} onChange={handleTabChange} sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tab label="آخر الحركات" icon={<HistoryIcon />} iconPosition="start" />
            <Tab label="كل الحركات" icon={<AccountBalanceWalletIcon />} iconPosition="start" />
          </Tabs>

          {/* Recent Transactions Tab */}
          <TabPanel value={activeTab} index={0}>
            <UnifiedMedicalTable
              columns={transactionColumns}
              data={recentRowsWithTotals}
              loading={isLoadingRecent}
              renderCell={renderTransactionCell}
              totalItems={recentTransactions.length}
              page={0}
              rowsPerPage={Math.max(recentTransactions.length, 10)}
              rowsPerPageOptions={PAGE_SIZE_OPTIONS}
              emptyStateConfig={{
                icon: HistoryIcon,
                title: 'لا توجد حركات مالية',
                description: 'لا توجد حركات مالية حديثة'
              }}
            />
          </TabPanel>

          {/* All Transactions Tab */}
          <TabPanel value={activeTab} index={1}>
            <UnifiedMedicalTable
              columns={transactionColumns}
              data={allRowsWithTotals}
              loading={isLoadingTransactions}
              renderCell={renderTransactionCell}
              totalItems={totalTransactions}
              page={paginationModel.page}
              rowsPerPage={paginationModel.pageSize}
              onPageChange={(newPage) => setPaginationModel((prev) => ({ ...prev, page: newPage }))}
              onRowsPerPageChange={(newSize) => setPaginationModel({ page: 0, pageSize: newSize })}
              rowsPerPageOptions={PAGE_SIZE_OPTIONS}
              emptyStateConfig={{
                icon: AccountBalanceWalletIcon,
                title: 'لا توجد حركات مالية',
                description: 'لا توجد حركات مالية لعرضها'
              }}
            />
          </TabPanel>
        </MainCard>

        {/* Modal for Payment Submission */}
        <Dialog open={isPaymentModalOpen} onClose={() => setIsPaymentModalOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle>تسجيل دفعة لمقدم الخدمة</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: '1.0rem' }}>
              <Typography variant="body2" color="text.secondary">
                الرصيد المستحق: <strong>{formatCurrency(accountData?.runningBalance)}</strong>
              </Typography>
              <TextField 
                label="المبلغ" 
                type="number" 
                fullWidth 
                required 
                value={paymentForm.amount}
                onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })}
                error={Number(paymentForm.amount) > Number(accountData?.runningBalance)}
                helperText={Number(paymentForm.amount) > Number(accountData?.runningBalance) ? 'المبلغ أكبر من الرصيد المستحق' : ''}
              />
              <TextField 
                label="المرجع (إيصال، حوالة...)" 
                fullWidth 
                required 
                value={paymentForm.paymentReference}
                onChange={(e) => setPaymentForm({ ...paymentForm, paymentReference: e.target.value })}
              />
              <TextField 
                label="ملاحظات" 
                fullWidth 
                multiline 
                rows={3} 
                value={paymentForm.notes}
                onChange={(e) => setPaymentForm({ ...paymentForm, notes: e.target.value })}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setIsPaymentModalOpen(false)} color="inherit" disabled={isSubmittingPayment}>إلغاء</Button>
            <Button onClick={handlePaymentSubmit} variant="contained" color="primary" disabled={isSubmittingPayment}>
              {isSubmittingPayment ? 'جاري التسجيل...' : 'تسجيل الدفعة'}
            </Button>
          </DialogActions>
        </Dialog>

      </Box>
    </PermissionGuard>
  );
};

export default ProviderAccountView;

