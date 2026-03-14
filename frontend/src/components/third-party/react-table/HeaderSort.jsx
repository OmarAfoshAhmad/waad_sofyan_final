import PropTypes from 'prop-types';
// material-ui
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';

// assets
import CaretDownOutlined from '@ant-design/icons/CaretDownOutlined';
import CaretUpOutlined from '@ant-design/icons/CaretUpOutlined';

var SortType;

(function (SortType) {
  SortType['ASC'] = 'asc';
  SortType['DESC'] = 'desc';
})(SortType || (SortType = {}));

function SortToggler({ type }) {
  return (
    <Stack
      sx={{
        fontSize: '0.75rem',
        color: 'secondary.light',
        ...(type === SortType.ASC && { '& .caret-up': { color: 'secondary.main' } }),
        ...(type === SortType.DESC && { '& .caret-down': { color: 'secondary.main' } })
      }}
    >
      <CaretUpOutlined className="caret-up" />
      <CaretDownOutlined className="caret-down" style={{ marginTop: -2 }} />
    </Stack>
  );
}

// ==============================|| HEADER SORT ||============================== //

export default function HeaderSort({ column, sort = true }) {
  return (
    <Box {...(sort && { onClick: column.getToggleSortingHandler(), sx: { cursor: 'pointer' } })}>
      {{
        asc: <SortToggler type={SortType.ASC} />,
        desc: <SortToggler type={SortType.DESC} />
      }[column.getIsSorted()] ?? <SortToggler />}
    </Box>
  );
}

SortToggler.propTypes = { type: PropTypes.any };

HeaderSort.propTypes = { column: PropTypes.object, sort: PropTypes.bool };

