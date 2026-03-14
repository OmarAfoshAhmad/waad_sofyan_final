import PropTypes from 'prop-types';
// material-ui
import Box from '@mui/material/Box';

// third-party
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';

// project imports
import { ThemeDirection } from 'config';

// ==============================|| QUILL EDITOR ||============================== //

export default function ReactQuillDemo({ value, editorMinHeight = 135, onChange }) {
  return (
    <Box
      sx={(theme) => ({
        borderRadius: '0.375rem',
        '& .quill': {
          '& .ql-toolbar': {
            bgcolor: 'secondary.lighter',
            borderColor: 'grey.300',
            borderTopLeftRadius: '4px',
            borderTopRightRadius: '4px',
            ...theme.applyStyles('dark', { bgcolor: 'secondary.400', borderColor: 'grey.200' })
          },
          '& .ql-container': {
            borderColor: 'grey.300',
            borderBottomLeftRadius: '4px',
            borderBottomRightRadius: '4px',
            ...theme.applyStyles('dark', { borderColor: 'grey.200' }),
            '& .ql-editor': { minHeight: editorMinHeight }
          },
          ...(theme.direction === ThemeDirection.RTL && {
            '& .ql-snow .ql-picker:not(.ql-color-picker):not(.ql-icon-picker) svg': {
              right: '0%',
              left: 'inherit'
            }
          })
        }
      })}
    >
      <ReactQuill {...(value && { value })} {...(onChange && { onChange })} />
    </Box>
  );
}

ReactQuillDemo.propTypes = { value: PropTypes.string, editorMinHeight: PropTypes.number, onChange: PropTypes.func };

