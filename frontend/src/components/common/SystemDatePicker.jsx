import PropTypes from 'prop-types';
import { DatePicker as MuiDatePicker } from '@mui/x-date-pickers/DatePicker';
import useSystemConfig from 'hooks/useSystemConfig';
import { DEFAULT_DATE_DISPLAY_FORMAT, toDayjsDateDisplayFormat } from 'utils/dateConfig';

/** The only DatePicker applications should use; display format comes from system settings. */
const SystemDatePicker = ({ format, ...props }) => {
  const { uiConfig } = useSystemConfig();
  const configuredFormat = format || uiConfig?.dateDisplayFormat || DEFAULT_DATE_DISPLAY_FORMAT;
  return <MuiDatePicker {...props} format={toDayjsDateDisplayFormat(configuredFormat)} />;
};

SystemDatePicker.propTypes = { format: PropTypes.string };

export default SystemDatePicker;
