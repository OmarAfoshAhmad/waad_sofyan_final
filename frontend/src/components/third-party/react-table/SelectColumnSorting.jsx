import PropTypes from 'prop-types';

// material-ui
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import OutlinedInput from '@mui/material/OutlinedInput';
import Select from '@mui/material/Select';
import Typography from '@mui/material/Typography';

const ITEM_HEIGHT = 48;
const ITEM_PADDING_TOP = 8;
const MenuProps = {
  PaperProps: {
    style: {
      maxHeight: ITEM_HEIGHT * 4.5 + ITEM_PADDING_TOP,
      width: '12.5rem'
    }
  }
};

// ==============================|| COLUMN SORTING - SELECT ||============================== //

export default function SelectColumnSorting({ getState, getAllColumns, setSorting, size = 'medium', formControlProps }) {
  return (
    <FormControl {...formControlProps} sx={{ width: '12.5rem', ...formControlProps?.sx }}>
      <Select
        id="column-sorting"
        multiple
        displayEmpty
        value={getState().sorting.length > 0 ? getState().sorting : []}
        input={<OutlinedInput id="select-column-sorting" placeholder="select column" />}
        renderValue={(selected) => {
          const selectedColumn = getAllColumns().find((column) => selected.length > 0 && column.id === selected[0].id);
          if (selectedColumn) {
            return (
              <Typography variant="subtitle2">
                Sort by ({typeof selectedColumn.columnDef.header === 'string' ? selectedColumn.columnDef.header : '#'})
              </Typography>
            );
          }
          return <Typography variant="subtitle2">Sort By</Typography>;
        }}
        MenuProps={MenuProps}
        size={size}
      >
        {getAllColumns().map(
          (column) =>
            'header' in column.columnDef &&
            column.getCanSort() && (
              <MenuItem
                key={column.id}
                value={column.id}
                onClick={() =>
                  setSorting(
                    getState().sorting.length > 0 && column.id === getState().sorting[0].id ? [] : [{ id: column.id, desc: false }]
                  )
                }
              >
                <Checkbox checked={getState().sorting.length > 0 && column.id === getState().sorting[0].id} color="success" />
                <ListItemText primary={column.columnDef.header} />
              </MenuItem>
            )
        )}
      </Select>
    </FormControl>
  );
}

SelectColumnSorting.propTypes = {
  getState: PropTypes.func,
  getAllColumns: PropTypes.func,
  setSorting: PropTypes.func,
  size: PropTypes.string,
  formControlProps: PropTypes.any
};
