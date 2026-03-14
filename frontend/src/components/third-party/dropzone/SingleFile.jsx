import PropTypes from 'prop-types';
// material-ui
import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import CardMedia from '@mui/material/CardMedia';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';

// third-party
import { useDropzone } from 'react-dropzone';

// project imports
import RejectionFiles from './RejectionFiles';
import PlaceholderContent from './PlaceholderContent';

const DropzoneWrapper = styled('div')(({ theme }) => {
  const varsPalette = (theme?.vars && theme.vars.palette) || theme.palette || {};
  const backgroundVars = varsPalette.background || theme.palette?.background || {};
  const secondaryVars = varsPalette.secondary || theme.palette?.secondary || {};
  const paperColor = backgroundVars.paper ?? theme.palette?.background?.paper ?? '#ffffff';
  const secondaryMain = secondaryVars.main ?? theme.palette?.secondary?.main ?? '#1976d2';
  return {
    outline: 'none',
    overflow: 'hidden',
    position: 'relative',
    padding: theme.spacing(5, 1),
    borderRadius: theme.shape.borderRadius,
    transition: theme.transitions.create('padding'),
    background: paperColor,
    border: `1px dashed ${secondaryMain}`,
    '&:hover': { opacity: 0.72, cursor: 'pointer' }
  };
});

// ==============================|| UPLOAD - SINGLE FILE ||============================== //

export default function SingleFileUpload({ error, file, setFieldValue, sx }) {
  const { getRootProps, getInputProps, isDragActive, isDragReject, fileRejections } = useDropzone({
    accept: {
      'image/*': []
    },
    multiple: false,
    onDrop: (acceptedFiles) => {
      setFieldValue(
        'files',
        acceptedFiles.map((file) =>
          Object.assign(file, {
            preview: URL.createObjectURL(file)
          })
        )
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
        sx={{
          top: '4.0rem',
          left: '0.375rem',
          borderRadius: '0.125rem',
          position: 'absolute',
          width: 'calc(100% - 16px)',
          height: 'calc(100% - 16px)',
          bgcolor: 'background.paper'
        }}
        onLoad={() => {
          URL.revokeObjectURL(item.preview);
        }}
      />
    ));

  const onRemove = () => {
    setFieldValue('files', null);
  };

  return (
    <Box sx={{ width: '100%', ...sx }}>
      <DropzoneWrapper
        {...getRootProps()}
        sx={{
          ...(isDragActive && { opacity: 0.72 }),
          ...((isDragReject || error) && { color: 'error.main', borderColor: 'error.light', bgcolor: 'error.lighter' }),
          ...(file && { padding: '12% 0' })
        }}
      >
        <input {...getInputProps()} />
        <PlaceholderContent />
        {thumbs}
      </DropzoneWrapper>
      {fileRejections.length > 0 && <RejectionFiles fileRejections={fileRejections} />}
      {file && file.length > 0 && (
        <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: '0.75rem' }}>
          <Button variant="contained" color="error" onClick={onRemove}>
            Remove
          </Button>
        </Stack>
      )}
    </Box>
  );
}

SingleFileUpload.propTypes = { error: PropTypes.any, file: PropTypes.any, setFieldValue: PropTypes.any, sx: PropTypes.any };


