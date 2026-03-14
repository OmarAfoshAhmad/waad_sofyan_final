import PropTypes from 'prop-types';
// material-ui
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';

// project imports
import DebouncedInput from './DebouncedInput';

// assets
import MinusOutlined from '@ant-design/icons/MinusOutlined';

const filterStyle = { fontSize: '0.75rem' };

// ==============================|| FILTER - NUMBER FIELD ||============================== //

function NumberInput({ columnFilterValue, getFacetedMinMaxValues, setFilterValue }) {
  const minOpt = getFacetedMinMaxValues()?.[0];
  const min = Number(minOpt ?? '');

  const maxOpt = getFacetedMinMaxValues()?.[1];
  const max = Number(maxOpt || 0);

  return (
    <Stack direction="row" sx={{ gap: 0.5, alignItems: 'center' }}>
      <DebouncedInput
        type="number"
        value={columnFilterValue?.[0] ?? ''}
        onFilterChange={(value) => setFilterValue((old) => [value, old?.[1]])}
        placeholder={`Min ${minOpt ? `(${min})` : ''}`}
        fullWidth
        slotProps={{ input: { min: min, max: max, sx: { p: 1 } } }}
        startIcon={false}
        sx={filterStyle}
      />
      <MinusOutlined color="disabled" />
      <DebouncedInput
        type="number"
        value={columnFilterValue?.[1] ?? ''}
        onFilterChange={(value) => setFilterValue((old) => [old?.[0], value])}
        placeholder={`Max ${maxOpt ? `(${max})` : ''}`}
        fullWidth
        slotProps={{ input: { min: min, max: max, sx: { p: 1 } } }}
        startIcon={false}
        sx={filterStyle}
      />
    </Stack>
  );
}

// ==============================|| FILTER - TEXT FIELD ||============================== //

function TextInput({ columnId, columnFilterValue, header, setFilterValue }) {
  const dataListId = columnId + 'list';

  return (
    <DebouncedInput
      type="text"
      fullWidth
      value={columnFilterValue ?? ''}
      onFilterChange={(value) => setFilterValue(value)}
      placeholder={`Search ${header}`}
      slotProps={{ input: { list: dataListId, sx: { p: 1 } } }}
      startIcon={false}
      sx={filterStyle}
    />
  );
}

// ==============================|| FILTER - INPUT ||============================== //

export default function Filter({ column }) {
  const columnFilterValue = column.getFilterValue();
  const colMeta = column.columnDef.meta?.filtertype;

  switch (colMeta) {
    case 'number':
      return (
        <NumberInput
          columnFilterValue={columnFilterValue}
          getFacetedMinMaxValues={column.getFacetedMinMaxValues}
          setFilterValue={column.setFilterValue}
        />
      );

    case 'slider': {
      const [facetedMin, facetedMax] = column.getFacetedMinMaxValues() ?? [0, 100];
      return (
        <Slider
          size="medium"
          min={0}
          max={100}
          value={Array.isArray(columnFilterValue) ? columnFilterValue : [facetedMin, facetedMax]}
          onChange={(_, value) => column.setFilterValue(value)}
          valueLabelDisplay="auto"
          sx={{ width: '9.375rem' }}
        />
      );
    }

    case 'select': {
      const options = Array.from(column.getFacetedUniqueValues().keys());

      return (
        <Select size="small" value={columnFilterValue ?? ''} onChange={(e) => column.setFilterValue(e.target.value)} displayEmpty fullWidth>
          <MenuItem value="" sx={{ textTransform: 'none' }}>
            All
          </MenuItem>
          {options.map((opt) => (
            <MenuItem key={opt} value={opt} sx={{ textTransform: 'none' }}>
              {opt}
            </MenuItem>
          ))}
        </Select>
      );
    }

    default:
      return (
        <TextInput
          columnId={column.id}
          columnFilterValue={columnFilterValue}
          setFilterValue={column.setFilterValue}
          header={column.columnDef.header}
        />
      );
  }
}

NumberInput.propTypes = { columnFilterValue: PropTypes.number, getFacetedMinMaxValues: PropTypes.func, setFilterValue: PropTypes.func };

TextInput.propTypes = {
  columnId: PropTypes.string,
  columnFilterValue: PropTypes.string,
  header: PropTypes.string,
  setFilterValue: PropTypes.func
};

Filter.propTypes = { column: PropTypes.any };


