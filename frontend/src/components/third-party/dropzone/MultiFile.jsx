import PropTypes from 'prop-types';
// material-ui
import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';

// third-party
import { useDropzone } from 'react-dropzone';

// project imports
import RejectionFiles from './RejectionFiles';
import PlaceholderContent from './PlaceholderContent';
import FilesPreview from './FilesPreview';
import { DropzoneType } from 'config';

const DropzoneWrapper = styled('div')(({ theme }) => {
  const varsPalette = (theme?.vars && theme.vars.palette) || theme.palette || {};
  const backgroundVars = varsPalette.background || theme.palette?.background || {};
  const secondaryVars = varsPalette.secondary || theme.palette?.secondary || {};
  const paperColor = backgroundVars.paper ?? theme.palette?.background?.paper ?? '#ffffff';
  const secondaryMain = secondaryVars.main ?? theme.palette?.secondary?.main ?? '#1976d2';
  return {
    outline: 'none',
    padding: theme.spacing(5, 1),
    borderRadius: theme.shape.borderRadius,
    background: paperColor,
    border: `1px dashed ${secondaryMain}`,
    '&:hover': { opacity: 0.72, cursor: 'pointer' }
  };
});

// ==============================|| UPLOAD - MULTIPLE FILE ||============================== //

export default function MultiFileUpload({ error, showList = false, files, type, setFieldValue, sx, onUpload }) {
  const { getRootProps, getInputProps, isDragActive, isDragReject, fileRejections } = useDropzone({
    multiple: true,
    onDrop: (acceptedFiles) => {
      if (files) {
        setFieldValue('files', [...files, ...acceptedFiles.map((file) => Object.assign(file, { preview: URL.createObjectURL(file) }))]);
      } else {
        setFieldValue(
          'files',
          acceptedFiles.map((file) => Object.assign(file, { preview: URL.createObjectURL(file) }))
        );
      }
    }
  });

  const onRemoveAll = () => {
    setFieldValue('files', null);
  };

  const onRemove = (file) => {
    const filteredItems = files && files.filter((_file) => _file !== file);
    setFieldValue('files', filteredItems);
  };

  return (
    <>
      <Box sx={{ width: '100%', ...(type === DropzoneType.STANDARD && { width: 'auto', display: 'flex' }), ...sx }}>
        <Stack {...(type === DropzoneType.STANDARD && { alignItems: 'center' })}>
          <DropzoneWrapper
            {...getRootProps()}
            sx={{
              ...(type === DropzoneType.STANDARD && { p: 0, mb: 1, mr: 0.5, width: '5.375rem', height: '5.25rem' }),
              ...(isDragActive && { opacity: 0.72 }),
              ...((isDragReject || error) && {
                color: 'error.main',
                borderColor: 'error.light',
                bgcolor: 'error.lighter'
              })
            }}
          >
            <input {...getInputProps()} />
            <PlaceholderContent type={type} />
          </DropzoneWrapper>
          {type === DropzoneType.STANDARD && files && files.length > 1 && (
            <Button variant="contained" color="error" size="extraSmall" onClick={onRemoveAll}>
              Remove all
            </Button>
          )}
        </Stack>
        {fileRejections.length > 0 && <RejectionFiles fileRejections={fileRejections} />}
        {files && files.length > 0 && <FilesPreview files={files} showList={showList} onRemove={onRemove} type={type} />}
      </Box>

      {type !== DropzoneType.STANDARD && files && files.length > 0 && (
        <Stack direction="row" sx={{ gap: '0.75rem', justifyContent: 'flex-end' }}>
          <Button color="inherit" size="small" onClick={onRemoveAll}>
            Remove all
          </Button>
          <Button size="small" variant="contained" onClick={onUpload}>
            Upload files
          </Button>
        </Stack>
      )}
    </>
  );
}

MultiFileUpload.propTypes = {
  error: PropTypes.any,
  showList: PropTypes.bool,
  files: PropTypes.any,
  type: PropTypes.any,
  setFieldValue: PropTypes.any,
  sx: PropTypes.any,
  onUpload: PropTypes.any
};


