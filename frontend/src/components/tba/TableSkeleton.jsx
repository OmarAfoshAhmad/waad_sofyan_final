import PropTypes from 'prop-types';
import { Box, Skeleton, Stack } from '@mui/material';

const TableSkeleton = ({ rows = 5, columns = 4 }) => {
  return (
    <Box sx={{ width: '100%' }}>
      <Stack direction="row" spacing={2} sx={{ mb: '1.0rem' }}>
        {[...Array(columns)].map((_, colIndex) => (
          <Skeleton key={colIndex} variant="rectangular" height={40} sx={{ flex: 1 }} />
        ))}
      </Stack>

      {[...Array(rows)].map((_, rowIndex) => (
        <Stack key={rowIndex} direction="row" spacing={2} sx={{ mb: '0.75rem' }}>
          {[...Array(columns)].map((_, colIndex) => (
            <Skeleton key={colIndex} variant="rectangular" height={50} sx={{ flex: 1 }} />
          ))}
        </Stack>
      ))}
    </Box>
  );
};

TableSkeleton.propTypes = {
  rows: PropTypes.number,
  columns: PropTypes.number
};

export default TableSkeleton;


