import PropTypes from 'prop-types';
import {
  Box,
  Grid,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  OutlinedInput,
  InputAdornment,
  IconButton,
  Tooltip,
  Paper
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import { EmployerSelectField, ProviderSelectField } from 'components/tba';

import { ALL_CLAIM_STATUSES, CLAIM_STATUS_LABELS, DEFAULT_FILTERS } from 'hooks/useClaimsReport';

/**
 * ClaimsFilters Component
 *
 * Client-side filter controls for Claims Operational Report
 *
 * Filters:
 * - Employer (ADMIN only)
 * - Status (multi-select)
 * - Member search (text)
 * - Amount ranges (requested/approved)
 *
 * @param {Object} filters - Current filter state
 * @param {Function} onFilterChange - Filter change handler
 * @param {Array} employers - Available employers (for admin)
 * @param {boolean} canSelectEmployer - Whether employer selector is enabled
 * @param {number|null} selectedEmployerId - Currently selected employer
 * @param {Function} onEmployerChange - Employer change handler
 * @param {Array} providers - Available providers list
 * @param {number|null} selectedProviderId - Currently selected provider
 * @param {Function} onProviderChange - Provider change handler
 */
const ClaimsFilters = ({
  filters,
  onFilterChange,
  employers = [],
  canSelectEmployer = false,
  selectedEmployerId,
  onEmployerChange,
  providers = [],
  selectedProviderId,
  onProviderChange
}) => {
  /**
   * Handle filter field change
   */
  const handleChange = (field) => (event) => {
    const value = event.target.value;
    onFilterChange({
      ...filters,
      [field]: value
    });
  };

  /**
   * Clear all filters
   */
  const handleClearFilters = () => {
    onFilterChange(DEFAULT_FILTERS);
  };

  /**
   * Check if any filter is active
   */
  const hasActiveFilters =
    filters.statuses.length > 0 ||
    filters.memberSearch.trim() !== '' ||
    filters.dateFrom ||
    filters.dateTo ||
    filters.minAmount !== '' ||
    filters.maxAmount !== '' ||
    filters.financialStatus !== 'ALL';

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.25,
        mb: 1,
        borderRadius: 1.5,
        borderColor: 'divider',
        bgcolor: '#fff'
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box>
            <Box component="span" sx={{ fontWeight: 900, color: 'text.primary', fontSize: '0.9rem' }}>
              فلاتر البحث
            </Box>
            <Box component="div" sx={{ fontSize: '0.72rem', color: 'text.secondary', mt: 0.15 }}>
              نطاق البحث ينعكس مباشرة على الجدول والملخص
            </Box>
          </Box>
        </Box>
        <Tooltip title={hasActiveFilters ? 'إعادة ضبط الفلاتر' : 'لا توجد فلاتر مفعلة'}>
          <span>
            <IconButton size="small" onClick={handleClearFilters} disabled={!hasActiveFilters}>
              <ClearIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Box>

      <Grid container spacing={1.25} alignItems="center">
        {/* Employer Selector (Admin Only) */}
        {canSelectEmployer && (
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <EmployerSelectField
              value={selectedEmployerId ?? ''}
              onChange={(employerId) => onEmployerChange(employerId || null)}
              options={employers}
              label="الشريك"
              placeholder="ابحث باسم أو رمز الشريك..."
              allLabel="جميع الشركاء"
            />
          </Grid>
        )}

        {/* Provider Selector */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <ProviderSelectField
            value={selectedProviderId ?? ''}
            onChange={(providerId) => onProviderChange(providerId || null)}
            options={providers}
            label="مقدم الخدمة"
            placeholder="ابحث باسم أو كود مقدم الخدمة..."
            allLabel="جميع مقدمي الخدمة"
          />
        </Grid>

        {/* Status Multi-Select */}
        <Grid size={{ xs: 12, sm: 6, md: canSelectEmployer ? 3 : 4 }}>
          <FormControl fullWidth size="small">
            <InputLabel id="status-filter-label">الحالة</InputLabel>
            <Select
              labelId="status-filter-label"
              multiple
              value={filters.statuses}
              onChange={handleChange('statuses')}
              input={<OutlinedInput label="الحالة" />}
              renderValue={(selected) => (selected.length === 1 ? CLAIM_STATUS_LABELS[selected[0]] : `${selected.length} حالات مختارة`)}
            >
              {ALL_CLAIM_STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {CLAIM_STATUS_LABELS[status]}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>

        {/* Member Search */}
        <Grid size={{ xs: 12, sm: 6, md: canSelectEmployer ? 3 : 4 }}>
          <TextField
            fullWidth
            size="small"
            label="بحث موحد"
            value={filters.memberSearch}
            onChange={handleChange('memberSearch')}
            placeholder="اسم المؤمن، رقم المطالبة، البطاقة..."
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: filters.memberSearch && (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => onFilterChange({ ...filters, memberSearch: '' })}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Date From */}
        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            type="date"
            label="من تاريخ"
            value={filters.dateFrom || ''}
            onChange={handleChange('dateFrom')}
            InputLabelProps={{ shrink: true }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <CalendarTodayIcon fontSize="small" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Date To */}
        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            type="date"
            label="إلى تاريخ"
            value={filters.dateTo || ''}
            onChange={handleChange('dateTo')}
            InputLabelProps={{ shrink: true }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <CalendarTodayIcon fontSize="small" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            type="number"
            label="الحد الأدنى للمطلوب"
            value={filters.minAmount ?? ''}
            onChange={handleChange('minAmount')}
            inputProps={{ min: 0, step: '0.01' }}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            type="number"
            label="الحد الأعلى للمطلوب"
            value={filters.maxAmount ?? ''}
            onChange={handleChange('maxAmount')}
            inputProps={{ min: 0, step: '0.01' }}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <FormControl fullWidth size="small">
            <InputLabel id="financial-status-filter-label">النتيجة المالية</InputLabel>
            <Select
              labelId="financial-status-filter-label"
              value={filters.financialStatus ?? 'ALL'}
              label="النتيجة المالية"
              onChange={handleChange('financialStatus')}
            >
              <MenuItem value="ALL">الكل</MenuItem>
              <MenuItem value="HAS_REJECTION">بها رفض أو فرق</MenuItem>
              <MenuItem value="FULLY_APPROVED">معتمدة بالكامل</MenuItem>
            </Select>
          </FormControl>
        </Grid>
      </Grid>
    </Paper>
  );
};

ClaimsFilters.propTypes = {
  filters: PropTypes.shape({
    statuses: PropTypes.array,
    memberSearch: PropTypes.string,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string,
    minAmount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    maxAmount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    financialStatus: PropTypes.string
  }).isRequired,
  onFilterChange: PropTypes.func.isRequired,
  employers: PropTypes.array,
  canSelectEmployer: PropTypes.bool,
  selectedEmployerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onEmployerChange: PropTypes.func,
  providers: PropTypes.array,
  selectedProviderId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onProviderChange: PropTypes.func
};

export default ClaimsFilters;
