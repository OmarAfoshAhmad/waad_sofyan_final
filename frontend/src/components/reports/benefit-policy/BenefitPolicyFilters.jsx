import PropTypes from 'prop-types';
import { Box, Grid, TextField, FormControl, InputLabel, Select, MenuItem, InputAdornment, Button, Collapse } from '@mui/material';
import { Search as SearchIcon, FilterList as FilterIcon, Clear as ClearIcon } from '@mui/icons-material';
import { useState } from 'react';

import { DEFAULT_FILTERS, STATUS_CONFIG } from 'hooks/useBenefitPolicyReport';

/**
 * BenefitPolicy Filters Component
 *
 * Client-side filters for the BenefitPolicy report:
 * - Policy name/code search
 * - Status filter
 * - Employer name search (admin only)
 * - Date range for coverage start
 */
const BenefitPolicyFilters = ({ filters = DEFAULT_FILTERS, onFilterChange, showEmployerFilter = false, loading = false }) => {
  const [expanded, setExpanded] = useState(false);

  /**
   * Handle individual filter change
   */
  const handleChange = (field) => (event) => {
    onFilterChange({
      ...filters,
      [field]: event.target.value
    });
  };

  /**
   * Clear all filters
   */
  const handleClearAll = () => {
    onFilterChange(DEFAULT_FILTERS);
  };

  /**
   * Check if any filter is active
   */
  const hasActiveFilters = Object.entries(filters).some(([key, value]) => {
    if (key === 'employerSearch' && !showEmployerFilter) return false;
    return value !== '' && value !== DEFAULT_FILTERS[key];
  });

  return (
    <Box sx={{ mb: '1.5rem' }}>
      {/* Primary Filters Row */}
      <Grid container spacing={2} alignItems="center">
        {/* Policy Search */}
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <TextField
            fullWidth
            size="small"
            label="بحث في الوثائق"
            placeholder="اسم الوثيقة أو الرمز..."
            value={filters.policySearch}
            onChange={handleChange('policySearch')}
            disabled={loading}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              )
            }}
          />
        </Grid>

        {/* Status Filter */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <FormControl fullWidth size="small">
            <InputLabel>حالة الوثيقة</InputLabel>
            <Select value={filters.status} onChange={handleChange('status')} label="حالة الوثيقة" disabled={loading}>
              <MenuItem value="">
                <em>الكل</em>
              </MenuItem>
              {Object.entries(STATUS_CONFIG).map(([status, config]) => (
                <MenuItem key={status} value={status}>
                  {config.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>

        {/* Toggle Advanced Filters */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Button
            variant={expanded ? 'contained' : 'outlined'}
            color="primary"
            size="small"
            startIcon={<FilterIcon />}
            onClick={() => setExpanded(!expanded)}
            sx={{ height: '2.5rem' }}
          >
            {expanded ? 'إخفاء الفلاتر' : 'فلاتر إضافية'}
          </Button>
        </Grid>

        {/* Clear Filters */}
        {hasActiveFilters && (
          <Grid size={{ xs: 12, sm: 6, md: 2 }}>
            <Button
              variant="text"
              color="error"
              size="small"
              startIcon={<ClearIcon />}
              onClick={handleClearAll}
              disabled={loading}
              sx={{ height: '2.5rem' }}
            >
              مسح الكل
            </Button>
          </Grid>
        )}
      </Grid>

      {/* Advanced Filters (Collapsible) */}
      <Collapse in={expanded}>
        <Box sx={{ mt: '1.0rem', pt: '1.0rem', borderTop: '1px dashed', borderColor: 'divider' }}>
          <Grid container spacing={2}>
            {/* Employer Search (Admin only) */}
            {showEmployerFilter && (
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="الشريك"
                  placeholder="بحث باسم الشريك..."
                  value={filters.employerSearch}
                  onChange={handleChange('employerSearch')}
                  disabled={loading}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon fontSize="small" color="action" />
                      </InputAdornment>
                    )
                  }}
                />
              </Grid>
            )}

            {/* Date From */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <TextField
                fullWidth
                size="small"
                type="date"
                label="تاريخ البدء من"
                value={filters.dateFrom}
                onChange={handleChange('dateFrom')}
                disabled={loading}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>

            {/* Date To */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <TextField
                fullWidth
                size="small"
                type="date"
                label="تاريخ البدء إلى"
                value={filters.dateTo}
                onChange={handleChange('dateTo')}
                disabled={loading}
                InputLabelProps={{ shrink: true }}
              />
            </Grid>
          </Grid>
        </Box>
      </Collapse>
    </Box>
  );
};

BenefitPolicyFilters.propTypes = {
  filters: PropTypes.shape({
    policySearch: PropTypes.string,
    status: PropTypes.string,
    employerSearch: PropTypes.string,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string
  }),
  onFilterChange: PropTypes.func.isRequired,
  showEmployerFilter: PropTypes.bool,
  loading: PropTypes.bool
};

export default BenefitPolicyFilters;

