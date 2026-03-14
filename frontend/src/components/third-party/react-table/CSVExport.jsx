import PropTypes from 'prop-types';
// material-ui
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';

// third party
import { CSVLink } from 'react-csv';

// assets
import DownloadOutlined from '@ant-design/icons/DownloadOutlined';

// ==============================|| CSV EXPORT ||============================== //

export default function CSVExport({ data, filename, headers }) {
  return (
    <CSVLink data={data} filename={filename} headers={headers} tabIndex={-1}>
      <Tooltip title="CSV Export">
        <Stack sx={{ color: 'text.secondary', alignContent: 'center' }}>
          <DownloadOutlined style={{ fontSize: '1.5rem' }} />
        </Stack>
      </Tooltip>
    </CSVLink>
  );
}

CSVExport.propTypes = { data: PropTypes.array, filename: PropTypes.string, headers: PropTypes.any };
