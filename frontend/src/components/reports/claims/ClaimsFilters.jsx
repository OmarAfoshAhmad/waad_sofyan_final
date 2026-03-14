import PropTypes from 'prop-types';
import {
  Box,
  Grid,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  OutlinedInput,
  InputAdornment,
  IconButton,
  Tooltip,
  Paper
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import FilterListIcon from '@mui/icons-material/FilterList';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';

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
  const hasActiveFilters = filters.statuses.length > 0 || filters.memberSearch.trim() !== '' || filters.dateFrom || filters.dateTo;

  return (
    <Paper sx={{ p: '1.0rem', mb: '1.0rem' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: '1.0rem' }}>
        <FilterListIcon sx={{ mr: 1, color: 'text.secondary' }} />
        <Box component="span" sx={{ fontWeight: 600, color: 'text.primary' }}>
          فلاتر البحث
        </Box>
        {hasActiveFilters && (
          <Tooltip title="مسح الفلاتر">
            <IconButton size="small" onClick={handleClearFilters} sx={{ ml: 'auto' }}>
              <ClearIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Box>

      <Grid container spacing={2}>
        {/* Employer Selector (Admin Only) */}
        {canSelectEmployer && (
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <FormControl fullWidth size="small">
              <InputLabel id="employer-filter-label">الشريك</InputLabel>
              <Select
                labelId="employer-filter-label"
                value={selectedEmployerId ?? ''}
                label="الشريك"
                onChange={(e) => onEmployerChange(e.target.value || null)}
                startAdornment={
                  <InputAdornment position="start">
                    <BusinessIcon fontSize="small" />
                  </InputAdornment>
                }
              >
                <MenuItem value="">
                  <em>جميع الشركاء</em>
                </MenuItem>
                {employers.map((employer) => (
                  <MenuItem key={employer.id} value={employer.id}>
                    {employer.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
        )}

        {/* Provider Selector */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <FormControl fullWidth size="small">
            <InputLabel id="provider-filter-label">مقدم الخدمة</InputLabel>
            <Select
              labelId="provider-filter-label"
              value={selectedProviderId ?? ''}
              label="مقدم الخدمة"
              onChange={(e) => onProviderChange(e.target.value || null)}
              startAdornment={
                <InputAdornment position="start">
                  <LocalHospitalIcon fontSize="small" />
                </InputAdornment>
              }
            >
              <MenuItem value="">
                <em>جميع مقدمي الخدمة</em>
              </MenuItem>
              {providers.map((provider) => (
                <MenuItem key={provider.id} value={provider.id}>
                  {provider.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
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
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selected.map((status) => (
                    <Chip
                      key={status}
                      label={CLAIM_STATUS_LABELS[status]}
                      size="small"
                      onDelete={() => {
                        onFilterChange({
                          ...filters,
                          statuses: filters.statuses.filter((s) => s !== status)
                        });
                      }}
                      onMouseDown={(e) => e.stopPropagation()}
                    />
                  ))}
                </Box>
              )}
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
            label="بحث بالعضو"
            value={filters.memberSearch}
            onChange={handleChange('memberSearch')}
            placeholder="اسم العضو..."
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
      </Grid>
    </Paper>
  );
};

ClaimsFilters.propTypes = {
  filters: PropTypes.shape({
    statuses: PropTypes.array,
    memberSearch: PropTypes.string,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string
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

