import PropTypes from 'prop-types';

// material-ui
import { Box, Skeleton, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Stack } from '@mui/material';

/**
 * Table Skeleton Loader
 * Used for loading state in data tables
 */
export default function TableSkeleton({ rows = 5, columns = 6 }) {
  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            {Array.from({ length: columns }).map((_, index) => (
              <TableCell key={index}>
                <Skeleton variant="text" width="80%" />
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {Array.from({ length: rows }).map((_, rowIndex) => (
            <TableRow key={rowIndex}>
              {Array.from({ length: columns }).map((_, colIndex) => (
                <TableCell key={colIndex}>
                  <Skeleton variant="text" />
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

TableSkeleton.propTypes = {
  rows: PropTypes.number,
  columns: PropTypes.number
};

/**
 * Card Grid Skeleton Loader
 * Used for loading state in card grids
 */
export function CardGridSkeleton({ count = 6, columns = 3 }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, 1fr)',
          md: `repeat(${columns}, 1fr)`
        },
        gap: '1.0rem'
      }}
    >
      {Array.from({ length: count }).map((_, index) => (
        <Box
          key={index}
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            p: '1.0rem'
          }}
        >
          <Skeleton variant="text" width="60%" height={32} />
          <Skeleton variant="text" width="40%" sx={{ mt: 1 }} />
          <Stack spacing={1} sx={{ mt: '1.0rem' }}>
            <Skeleton variant="text" />
            <Skeleton variant="text" />
            <Skeleton variant="text" width="80%" />
          </Stack>
          <Stack direction="row" spacing={1} sx={{ mt: '1.0rem' }}>
            <Skeleton variant="rectangular" width={80} height={32} />
            <Skeleton variant="rectangular" width={80} height={32} />
          </Stack>
        </Box>
      ))}
    </Box>
  );
}

CardGridSkeleton.propTypes = {
  count: PropTypes.number,
  columns: PropTypes.number
};
