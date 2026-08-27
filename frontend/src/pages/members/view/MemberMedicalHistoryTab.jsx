import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {
  History as HistoryIcon,
  LocalHospital as VisitIcon,
  ReceiptLong as ClaimIcon,
  FactCheck as PreAuthIcon,
  Search as SearchIcon,
  Visibility as VisibilityIcon
} from '@mui/icons-material';
import { formatDate } from 'utils/formatters';
import { formatMoney, medicalHistoryStatusColor, medicalHistoryStatusLabel } from './memberView.helpers';

const EVENT_ICONS = {
  visit: <VisitIcon fontSize="small" />,
  claim: <ClaimIcon fontSize="small" />,
  preauth: <PreAuthIcon fontSize="small" />
};

/**
 * Medical history tab: a unified, filterable, paginated timeline of a
 * member's visits/claims/pre-authorizations. Data + filter state comes from
 * {@link useMemberMedicalHistory}; this component is presentational only.
 */
const MemberMedicalHistoryTab = ({ history, onNavigate }) => {
  const {
    medicalHistory,
    loading,
    error,
    search,
    setSearch,
    type,
    setType,
    status,
    setStatus,
    statusOptions,
    filteredEvents,
    paginatedEvents,
    page,
    rowsPerPage,
    onPageChange,
    onRowsPerPageChange
  } = history;

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, bgcolor: 'primary.lighter' }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <VisitIcon color="primary" />
            <Box>
              <Typography variant="caption" color="text.secondary">
                الزيارات
              </Typography>
              <Typography variant="h5" fontWeight="bold" color="primary.main">
                {medicalHistory?.visits?.length ?? 0}
              </Typography>
            </Box>
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, bgcolor: 'success.lighter' }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <ClaimIcon color="success" />
            <Box>
              <Typography variant="caption" color="text.secondary">
                المطالبات
              </Typography>
              <Typography variant="h5" fontWeight="bold" color="success.main">
                {medicalHistory?.claims?.length ?? 0}
              </Typography>
            </Box>
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, bgcolor: 'warning.lighter' }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <PreAuthIcon color="warning" />
            <Box>
              <Typography variant="caption" color="text.secondary">
                الموافقات
              </Typography>
              <Typography variant="h5" fontWeight="bold" color="warning.main">
                {medicalHistory?.preAuths?.length ?? 0}
              </Typography>
            </Box>
          </Stack>
        </Paper>
      </Stack>

      {error && <Alert severity="warning">{error}</Alert>}

      <Paper variant="outlined">
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}
        >
          <Typography variant="subtitle1" fontWeight="bold">
            السجل الطبي الموحد
          </Typography>
          <Chip
            size="small"
            color="primary"
            variant="outlined"
            label={`${filteredEvents.length} من ${medicalHistory?.events?.length ?? 0} حركة`}
          />
        </Stack>

        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
          <TextField
            fullWidth
            size="small"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="بحث بالمرجع، الوصف، مقدم الخدمة أو الحالة..."
            InputProps={{ startAdornment: <SearchIcon fontSize="small" sx={{ color: 'text.secondary', mr: 1 }} /> }}
          />
          <FormControl size="small" sx={{ minWidth: { xs: '100%', md: 180 } }}>
            <InputLabel>نوع الحركة</InputLabel>
            <Select value={type} label="نوع الحركة" onChange={(event) => setType(event.target.value)}>
              <MenuItem value="ALL">كل الحركات</MenuItem>
              <MenuItem value="visit">الزيارات</MenuItem>
              <MenuItem value="claim">المطالبات</MenuItem>
              <MenuItem value="preauth">الموافقات</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', md: 180 } }}>
            <InputLabel>الحالة</InputLabel>
            <Select value={status} label="الحالة" onChange={(event) => setStatus(event.target.value)}>
              <MenuItem value="ALL">كل الحالات</MenuItem>
              {statusOptions.map((s) => (
                <MenuItem key={s} value={s}>
                  {medicalHistoryStatusLabel(s)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>

        {loading ? (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <CircularProgress size={28} />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              جارِ تحميل السجل الطبي...
            </Typography>
          </Box>
        ) : !medicalHistory?.events?.length ? (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <HistoryIcon color="disabled" sx={{ fontSize: 44, mb: 1 }} />
            <Typography variant="body2" color="text.secondary">
              لا توجد زيارات أو مطالبات أو موافقات مسجلة لهذا المستفيد.
            </Typography>
          </Box>
        ) : !filteredEvents.length ? (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <SearchIcon color="disabled" sx={{ fontSize: 44, mb: 1 }} />
            <Typography variant="body2" color="text.secondary">
              لا توجد حركات مطابقة للفلاتر الحالية.
            </Typography>
          </Box>
        ) : (
          <>
            <TableContainer sx={{ maxHeight: 430 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell align="center">النوع</TableCell>
                    <TableCell align="center">التاريخ</TableCell>
                    <TableCell align="center">المرجع</TableCell>
                    <TableCell align="right">الوصف</TableCell>
                    <TableCell align="center">مقدم الخدمة</TableCell>
                    <TableCell align="center">الحالة</TableCell>
                    <TableCell align="center">المبلغ</TableCell>
                    <TableCell align="center">فتح</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginatedEvents.map((event) => (
                    <TableRow key={event.id} hover>
                      <TableCell align="center">
                        <Chip icon={EVENT_ICONS[event.iconType]} label={event.typeLabel} size="small" variant="outlined" color="primary" />
                      </TableCell>
                      <TableCell align="center" dir="ltr">
                        {formatDate(event.date)}
                      </TableCell>
                      <TableCell align="center">
                        <Typography variant="caption" fontFamily="monospace">
                          {event.reference || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" fontWeight="medium">
                          {event.description}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">{event.provider}</TableCell>
                      <TableCell align="center">
                        <Chip
                          label={medicalHistoryStatusLabel(event.status)}
                          size="small"
                          color={medicalHistoryStatusColor(event.status)}
                        />
                      </TableCell>
                      <TableCell align="center">{formatMoney(event.amount)}</TableCell>
                      <TableCell align="center">
                        <Tooltip title="فتح السجل الأصلي">
                          <span>
                            <IconButton size="small" color="primary" disabled={!event.path} onClick={() => onNavigate(event.path)}>
                              <VisibilityIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={filteredEvents.length}
              page={page}
              onPageChange={onPageChange}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={onRowsPerPageChange}
              rowsPerPageOptions={[5, 10, 20, 50]}
              labelRowsPerPage="حركات لكل صفحة:"
            />
          </>
        )}
      </Paper>
    </Stack>
  );
};

export default MemberMedicalHistoryTab;
