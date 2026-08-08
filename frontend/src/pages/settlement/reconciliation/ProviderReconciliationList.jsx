import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import {
  Box,
  Chip,
  Stack,
  Typography,
  IconButton,
  Tooltip,
  TextField,
  InputAdornment,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Alert,
  CircularProgress
} from '@mui/material';
import {
  Search as SearchIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
  WarningAmber as WarningIcon,
  AccountBalanceWallet as CreditIcon
} from '@mui/icons-material';

import MainCard from 'components/MainCard';
import { ModernPageHeader } from 'components/tba';
import { formatCurrency } from 'utils/currency-formatter';
import { reconciliationService } from 'services/api/settlement.service';

import ProviderPaymentDetailDrawer from './ProviderPaymentDetailDrawer';

// Arabic labels for every Finding the backend can return. Unknown findings still
// render (raw key) instead of disappearing, so a new backend Finding is visible
// immediately rather than silently dropped.
const FINDING_LABELS = {
  MATCHED: 'مطابق',
  BALANCE_DRIFT: 'انحراف رصيد',
  BALANCE_EQUATION_BROKEN: 'معادلة الرصيد مكسورة',
  DOCUMENT_WITHOUT_LEDGER: 'مستند بلا قيد',
  LEDGER_WITHOUT_DOCUMENT: 'قيد بلا مستند',
  UNDER_ALLOCATED: 'تخصيص ناقص',
  OVER_ALLOCATED: 'تخصيص زائد',
  PROVIDER_CREDIT_BALANCE: 'رصيد دائن للمزود',
  UNPOSTED_PAYMENT: 'دفعة غير مرحّلة'
};

const FINDING_SEVERITY = {
  MATCHED: 'success',
  UNPOSTED_PAYMENT: 'info',
  UNDER_ALLOCATED: 'info',
  PROVIDER_CREDIT_BALANCE: 'info',
  BALANCE_DRIFT: 'error',
  BALANCE_EQUATION_BROKEN: 'error',
  DOCUMENT_WITHOUT_LEDGER: 'warning',
  LEDGER_WITHOUT_DOCUMENT: 'warning',
  OVER_ALLOCATED: 'error'
};

function FindingChips({ findings }) {
  if (!findings || findings.length === 0) return null;
  return (
    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
      {findings.map((f) => (
        <Chip
          key={f}
          size="small"
          label={FINDING_LABELS[f] || f}
          color={FINDING_SEVERITY[f] || 'default'}
          icon={f === 'MATCHED' ? <CheckCircleIcon /> : <WarningIcon />}
          variant={f === 'MATCHED' ? 'filled' : 'outlined'}
        />
      ))}
    </Stack>
  );
}

export default function ProviderReconciliationList() {
  const [search, setSearch] = useState('');
  const [selectedProviderId, setSelectedProviderId] = useState(null);

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['provider-reconciliation-all'],
    queryFn: reconciliationService.reconcileAll,
    staleTime: 30_000
  });

  const rows = useMemo(() => {
    const list = Array.isArray(data) ? data : [];
    const term = search.trim().toLowerCase();
    if (!term) return list;
    return list.filter((r) => (r.providerName || '').toLowerCase().includes(term) || String(r.providerId).includes(term));
  }, [data, search]);

  return (
    <Box>
      <ModernPageHeader
        title="مطابقة ودفعات مقدمي الخدمة"
        subtitle="المستحق، المدفوع، الرصيد، وحالة المطابقة لكل مقدم خدمة — من الدفتر مباشرة"
        breadcrumbs={[{ title: 'الرئيسية', to: '/' }, { title: 'التسويات' }, { title: 'المطابقة' }]}
        actions={
          <Tooltip title="تحديث">
            <IconButton onClick={() => refetch()} disabled={isFetching}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        }
      />

      <MainCard sx={{ mt: 2 }}>
        <Box sx={{ mb: 2 }}>
          <TextField
            size="small"
            placeholder="بحث باسم مقدم الخدمة أو المعرّف..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              )
            }}
            sx={{ minWidth: 320 }}
          />
        </Box>

        {isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error?.message || 'تعذّر تحميل بيانات المطابقة'}
          </Alert>
        )}

        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress />
          </Box>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>مقدم الخدمة</TableCell>
                  <TableCell align="right">المستحق (الاعتماد الصافي)</TableCell>
                  <TableCell align="right">المدفوع (الدفتر)</TableCell>
                  <TableCell align="right">الرصيد</TableCell>
                  <TableCell>حالة المطابقة</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r) => {
                  const isCredit = Number(r.accountRunningBalance) < 0;
                  return (
                    <TableRow key={r.providerId} hover onClick={() => setSelectedProviderId(r.providerId)} sx={{ cursor: 'pointer' }}>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {r.providerName || `مقدم خدمة #${r.providerId}`}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">{formatCurrency(r.accountTotalApproved)}</TableCell>
                      <TableCell align="right">{formatCurrency(r.ledgerNet)}</TableCell>
                      <TableCell align="right">
                        {isCredit ? (
                          <Chip size="small" icon={<CreditIcon />} color="info" label={`رصيد دائن ${formatCurrency(r.creditBalance)}`} />
                        ) : (
                          formatCurrency(r.accountRunningBalance)
                        )}
                      </TableCell>
                      <TableCell>
                        <FindingChips findings={r.findings} />
                      </TableCell>
                    </TableRow>
                  );
                })}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 3 }}>
                        لا توجد بيانات مطابقة بعد
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </MainCard>

      <ProviderPaymentDetailDrawer
        providerId={selectedProviderId}
        open={Boolean(selectedProviderId)}
        onClose={() => setSelectedProviderId(null)}
        onChanged={() => refetch()}
      />
    </Box>
  );
}
