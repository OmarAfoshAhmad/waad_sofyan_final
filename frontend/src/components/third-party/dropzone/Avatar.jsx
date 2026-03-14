import PropTypes from 'prop-types';
// material-ui
import { styled } from '@mui/material/styles';
import CardMedia from '@mui/material/CardMedia';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

// third-party
import { useDropzone } from 'react-dropzone';

// project imports
import RejectionFiles from './RejectionFiles';

// assets
import CameraOutlined from '@ant-design/icons/CameraOutlined';

const DropzoneWrapper = styled('div')({
  zIndex: 0,
  width: '100%',
  height: '100%',
  outline: 'none',
  display: 'flex',
  overflow: 'hidden',
  borderRadius: '50%',
  position: 'relative',
  alignItems: 'center',
  justifyContent: 'center',
  '& > *': { width: '100%', height: '100%' },
  '&:hover': {
    cursor: 'pointer',
    '& .placeholder': {
      zIndex: 9
    }
  }
});

const PlaceholderWrapper = styled('div')(({ theme }) => {
  const varsPalette = (theme?.vars && theme.vars.palette) || theme.palette || {};
  const textVars = varsPalette.text || theme.palette?.text || {};
  const textSecondary = textVars.secondary ?? theme.palette?.text?.secondary ?? '#616161';
  return {
    display: 'flex',
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    color: textSecondary,
    transition: theme.transitions.create('opacity', {
      easing: theme.transitions.easing.easeInOut,
      duration: theme.transitions.duration.shorter
    }),
    '&:hover': { opacity: 0.85 }
  };
});

// ==============================|| UPLOAD - AVATAR ||============================== //

export default function AvatarUpload({ error, file, setFieldValue, sx }) {
  const { getRootProps, getInputProps, isDragActive, isDragReject, fileRejections } = useDropzone({
    accept: { 'image/*': [] },
    multiple: false,
    onDrop: (acceptedFiles) => {
      setFieldValue(
        'files',
        acceptedFiles.map((file) => Object.assign(file, { preview: URL.createObjectURL(file) }))
      );
    }
  });

  const thumbs =
    file &&
    file.map((item) => (
      <CardMedia
        key={item.name}
        component="img"
        src={item.preview}
        onLoad={() => {
          URL.revokeObjectURL(item.preview);
        }}
      />
    ));

  return (
    <>
      <Box
        sx={{
          ...((isDragReject || error) && { borderColor: 'error.light' }),
          width: '7.75rem',
          height: '7.75rem',
          borderRadius: '50%',
          border: '1px dashed',
          borderColor: 'primary.main',
          bgcolor: 'primary.lighter',
          ...sx
        }}
      >
        <DropzoneWrapper {...getRootProps()} sx={{ ...(isDragActive && { opacity: 0.6 }) }}>
          <input {...getInputProps()} />
          {thumbs}
          <PlaceholderWrapper
            className="placeholder"
            sx={{
              ...(thumbs && { opacity: 0, color: 'common.white', bgcolor: 'grey.900' }),
              ...((isDragReject || error) && { bgcolor: 'error.lighter' })
            }}
          >
            <Stack sx={{ gap: 0.5, alignItems: 'center', color: 'secondary.main' }}>
              <CameraOutlined style={{ fontSize: '2rem' }} />
              <Typography>{file ? 'Update' : 'Upload'}</Typography>
            </Stack>
          </PlaceholderWrapper>
        </DropzoneWrapper>
      </Box>
      {fileRejections.length > 0 && <RejectionFiles fileRejections={fileRejections} />}
    </>
  );
}

AvatarUpload.propTypes = { error: PropTypes.any, file: PropTypes.any, setFieldValue: PropTypes.any, sx: PropTypes.any };
