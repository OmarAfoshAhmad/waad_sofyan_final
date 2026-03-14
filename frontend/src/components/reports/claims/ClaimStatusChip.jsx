import PropTypes from 'prop-types';
import { Chip, Typography } from '@mui/material';
import { CLAIM_STATUS, CLAIM_STATUS_LABELS } from 'hooks/useClaimsReport';

/**
 * Color mapping for claim statuses
 * Matches design spec exactly
 * ALL 7 statuses are mapped for future-proofing
 */
const STATUS_COLORS = {
  [CLAIM_STATUS.DRAFT]: { bg: '#9e9e9e', color: '#fff' }, // Gray
  [CLAIM_STATUS.SUBMITTED]: { bg: '#2196f3', color: '#fff' }, // Blue
  [CLAIM_STATUS.UNDER_REVIEW]: { bg: '#ff9800', color: '#fff' }, // Orange
  [CLAIM_STATUS.APPROVAL_IN_PROGRESS]: { bg: '#ffb74d', color: '#fff' }, // Orange Light
  [CLAIM_STATUS.APPROVED]: { bg: '#4caf50', color: '#fff' }, // Green
  [CLAIM_STATUS.BATCHED]: { bg: '#7e57c2', color: '#fff' }, // Deep Purple
  [CLAIM_STATUS.REJECTED]: { bg: '#f44336', color: '#fff' }, // Red
  [CLAIM_STATUS.NEEDS_CORRECTION]: { bg: '#9c27b0', color: '#fff' }, // Purple
  [CLAIM_STATUS.SETTLED]: { bg: '#009688', color: '#fff' } // Teal
};

/**
 * Default/fallback colors for unknown statuses
 */
const FALLBACK_COLORS = { bg: '#757575', color: '#fff' };

/**
 * ClaimStatusChip Component
 *
 * Displays claim status as a colored chip with Arabic label.
 * Handles null/undefined status gracefully.
 * All 7 ClaimStatus enum values are fully mapped.
 *
 * @param {string} status - Claim status enum value
 * @param {string} size - Chip size ('small' | 'medium')
 */
const ClaimStatusChip = ({ status, size = 'small' }) => {
  // Null-safe: Handle missing status
  if (!status) {
    return (
      <Typography variant="body2" color="text.disabled">
        —
      </Typography>
    );
  }

  // Get colors with fallback for unknown statuses
  const colors = STATUS_COLORS[status] || FALLBACK_COLORS;

  // Get label with fallback to raw status value
  const label = CLAIM_STATUS_LABELS[status] || status;

  return (
    <Chip
      label={label}
      size={size}
      sx={{
        backgroundColor: colors.bg,
        color: colors.color,
        fontWeight: 500,
        fontSize: size === 'small' ? '0.75rem' : '0.875rem',
        minWidth: '5.0rem'
      }}
    />
  );
};

ClaimStatusChip.propTypes = {
  status: PropTypes.string,
  size: PropTypes.oneOf(['small', 'medium'])
};

ClaimStatusChip.defaultProps = {
  status: null,
  size: 'small'
};

export default ClaimStatusChip;


