import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';
import 'dayjs/locale/ar-sa'; // Import Arabic locale if needed, though AdapterDayjs might handle it via theme

// Set global locale if the app is Arabic primarily
// dayjs.locale('ar-sa');

const GregorianDatePicker = ({ label, name, value, onChange, ...props }) => {
  // Handle DatePicker change (returns Date/Dayjs object)
  const handleDateChange = (newValue) => {
    // Convert to YYYY-MM-DD string or null
    // If newValue is valid dayjs object
    const formattedValue = newValue && dayjs(newValue).isValid() ? dayjs(newValue).format('YYYY-MM-DD') : '';

    // Create synthetic event
    const syntheticEvent = {
      target: {
        name: name,
        value: formattedValue
      }
    };

    if (onChange) {
      onChange(syntheticEvent);
    }
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <DatePicker
        label={label}
        value={value ? dayjs(value) : null}
        onChange={handleDateChange}
        format="DD/MM/YYYY"
        slotProps={{
          textField: {
            fullWidth: true,
            name: name,
            ...props
          }
        }}
      />
    </LocalizationProvider>
  );
};

export default GregorianDatePicker;
