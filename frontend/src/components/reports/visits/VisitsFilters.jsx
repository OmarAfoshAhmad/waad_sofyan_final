import PropTypes from 'prop-types';
import { Box, Grid, TextField, FormControl, InputLabel, Select, MenuItem, InputAdornment, IconButton, Tooltip, Paper } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import FilterListIcon from '@mui/icons-material/FilterList';
import BusinessIcon from '@mui/icons-material/Business';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';

import { DEFAULT_FILTERS } from 'hooks/useVisitsReport';

/**
 * VisitsFilters Component
 *
 * Client-side filter controls for Visits Operational Report
 *
 * Filters:
 * - Employer (ADMIN only)
 * - Provider search (text)
 * - Member search (text)
 * - Date range
 * - Has Claims
 * - Services count range
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
const VisitsFilters = ({
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
    filters.providerSearch.trim() !== '' ||
    filters.memberSearch.trim() !== '' ||
    filters.dateFrom !== '' ||
    filters.dateTo !== '' ||
    filters.hasClaims !== '' ||
    filters.minServicesCount !== '' ||
    filters.maxServicesCount !== '';

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

        {/* Provider Search */}
        <Grid size={{ xs: 12, sm: 6, md: canSelectEmployer ? 3 : 4 }}>
          <TextField
            fullWidth
            size="small"
            label="بحث بمقدم الخدمة"
            value={filters.providerSearch}
            onChange={handleChange('providerSearch')}
            placeholder="اسم مقدم الخدمة..."
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: filters.providerSearch && (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => onFilterChange({ ...filters, providerSearch: '' })}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              )
            }}
          />
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

        {/* Has Claims Filter */}
        <Grid size={{ xs: 12, sm: 6, md: canSelectEmployer ? 3 : 4 }}>
          <FormControl fullWidth size="small">
            <InputLabel id="has-claims-filter-label">حالة المطالبات</InputLabel>
            <Select labelId="has-claims-filter-label" value={filters.hasClaims} label="حالة المطالبات" onChange={handleChange('hasClaims')}>
              <MenuItem value="">الكل</MenuItem>
              <MenuItem value="yes">لديها مطالبة</MenuItem>
              <MenuItem value="no">بدون مطالبة</MenuItem>
            </Select>
          </FormControl>
        </Grid>

        {/* Date From */}
        <Grid size={{ xs: 6, sm: 3, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            label="من تاريخ"
            type="date"
            value={filters.dateFrom}
            onChange={handleChange('dateFrom')}
            InputLabelProps={{ shrink: true }}
          />
        </Grid>

        {/* Date To */}
        <Grid size={{ xs: 6, sm: 3, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            label="إلى تاريخ"
            type="date"
            value={filters.dateTo}
            onChange={handleChange('dateTo')}
            InputLabelProps={{ shrink: true }}
          />
        </Grid>

        {/* Services Count Range */}
        <Grid size={{ xs: 6, sm: 3, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            label="الحد الأدنى للخدمات"
            type="number"
            value={filters.minServicesCount}
            onChange={handleChange('minServicesCount')}
            placeholder="0"
            InputProps={{
              inputProps: { min: 0 }
            }}
          />
        </Grid>
        <Grid size={{ xs: 6, sm: 3, md: 2 }}>
          <TextField
            fullWidth
            size="small"
            label="الحد الأقصى للخدمات"
            type="number"
            value={filters.maxServicesCount}
            onChange={handleChange('maxServicesCount')}
            placeholder="∞"
            InputProps={{
              inputProps: { min: 0 }
            }}
          />
        </Grid>
      </Grid>
    </Paper>
  );
};

VisitsFilters.propTypes = {
  filters: PropTypes.shape({
    providerSearch: PropTypes.string,
    memberSearch: PropTypes.string,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string,
    hasClaims: PropTypes.string,
    minServicesCount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    maxServicesCount: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
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

export default VisitsFilters;

