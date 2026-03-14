import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';

// material-ui
import InputAdornment from '@mui/material/InputAdornment';
import OutlinedInput from '@mui/material/OutlinedInput';

// assets
import SearchOutlined from '@ant-design/icons/SearchOutlined';

// ==============================|| FILTER - INPUT ||============================== //

export default function DebouncedInput({
  value: initialValue,
  onFilterChange,
  debounce = 500,
  size = 'medium',
  startIcon = <SearchOutlined />,
  ...props
}) {
  const [value, setValue] = useState(initialValue);

  const handleInputChange = (event) => setValue(event.target.value);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      onFilterChange(value);
    }, debounce);

    return () => clearTimeout(timeout);
    // eslint-disable-next-line
  }, [value]);

  return (
    <OutlinedInput
      {...props}
      value={value}
      onChange={handleInputChange}
      sx={{ minWidth: '6.25rem', ...props.sx }}
      {...(startIcon && { startAdornment: <InputAdornment position="start">{startIcon}</InputAdornment> })}
      size={size}
    />
  );
}

DebouncedInput.propTypes = {
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onFilterChange: PropTypes.func,
  debounce: PropTypes.number,
  size: PropTypes.string,
  startIcon: PropTypes.oneOfType([PropTypes.any, PropTypes.bool]),
  SearchOutlined: PropTypes.any,
  props: PropTypes.any
};
