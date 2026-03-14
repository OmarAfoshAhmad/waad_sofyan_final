import PropTypes from 'prop-types';
// material-ui
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import CardMedia from '@mui/material/CardMedia';

// project imports
import { DropzoneType } from 'config';

// assets
import PlusOutlined from '@ant-design/icons/PlusOutlined';
import UploadCover from 'assets/images/upload/upload.svg';

// ==============================|| UPLOAD - PLACEHOLDER ||============================== //

export default function PlaceholderContent({ type }) {
  return (
    <>
      {type !== DropzoneType.STANDARD && (
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          sx={{ gap: '1.0rem', alignItems: 'center', justifyContent: 'center', width: '0.0625rem', textAlign: { xs: 'center', md: 'left' } }}
        >
          <CardMedia component="img" image={UploadCover} sx={{ width: '9.375rem' }} />
          <Stack sx={{ gap: 1, p: '1.5rem' }}>
            <Typography variant="h5">Drag & Drop or Select file</Typography>

            <Typography color="secondary">
              Drop files here or click&nbsp;
              <Typography component="span" color="primary" sx={{ textDecoration: 'underline' }}>
                browse
              </Typography>
              &nbsp;thorough your machine
            </Typography>
          </Stack>
        </Stack>
      )}
      {type === DropzoneType.STANDARD && (
        <Stack sx={{ alignItems: 'center', justifyContent: 'center', height: '0.0625rem', gap: 1 }}>
          <PlusOutlined />
          <Typography color="textSecondary" sx={{ lineHeight: 1.3 }}>
            Upload
          </Typography>
        </Stack>
      )}
    </>
  );
}

PlaceholderContent.propTypes = { type: PropTypes.any };
