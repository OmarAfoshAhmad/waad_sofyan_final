import PropTypes from 'prop-types';
// material-ui
import CardMedia from '@mui/material/CardMedia';
import List from '@mui/material/List';
import ListItemText from '@mui/material/ListItemText';
import ListItem from '@mui/material/ListItem';

// project imports
import IconButton from 'components/@extended/IconButton';
import { DropzoneType } from 'config';

// utils
import getDropzoneData from 'utils/getDropzoneData';

// type

// assets
import CloseCircleFilled from '@ant-design/icons/CloseCircleFilled';
import FileFilled from '@ant-design/icons/FileFilled';

// ==============================|| MULTI UPLOAD - PREVIEW ||============================== //

export default function FilesPreview({ showList = false, files, onRemove, type }) {
  const hasFile = files.length > 0;

  return (
    <List
      disablePadding
      sx={{
        ...(hasFile && type !== DropzoneType.STANDARD && { my: '1.5rem' }),
        ...(type === DropzoneType.STANDARD && { width: 'calc(100% - 84px)' })
      }}
    >
      {files.map((file, index) => {
        const { key, name, size, preview, type } = getDropzoneData(file, index);

        if (showList) {
          return (
            <ListItem
              key={key}
              sx={{
                p: 0,
                mx: 0.5,
                mt: -0.25,
                mb: 0.5,
                width: '5.0rem',
                height: '5.25rem',
                borderRadius: '0.078125rem',
                position: 'relative',
                display: 'inline-flex',
                verticalAlign: 'text-top',
                border: '1px solid',
                borderColor: 'divider',
                overflow: 'hidden'
              }}
            >
              {type?.includes('image') && (
                <CardMedia component="img" alt="preview" src={preview} style={{ width: '100%', objectFit: 'cover' }} />
              )}
              {!type?.includes('image') && <FileFilled style={{ width: '100%', fontSize: '1.5rem' }} />}

              {onRemove && (
                <IconButton
                  size="small"
                  color="error"
                  shape="rounded"
                  onClick={() => onRemove(file)}
                  sx={{
                    fontSize: '0.875rem',
                    bgcolor: 'background.paper',
                    p: 0,
                    width: 'auto',
                    height: 'auto',
                    top: '1.0rem',
                    right: '0.125rem',
                    position: 'absolute'
                  }}
                >
                  <CloseCircleFilled />
                </IconButton>
              )}
            </ListItem>
          );
        }

        return (
          <ListItem key={key} sx={{ my: 1, px: '1.0rem', py: 0.75, borderRadius: 0.75, border: 'solid 1px', borderColor: 'divider' }}>
            <FileFilled style={{ width: '1.875rem', height: '1.875rem', fontSize: '1.15rem', marginRight: '0.375rem' }} />
            <ListItemText
              primary={typeof file === 'string' ? file : name}
              secondary={typeof file === 'string' ? '' : size}
              slotProps={{ primary: { variant: 'subtitle2' }, secondary: { variant: 'caption' } }}
            />
            {onRemove && (
              <IconButton color="error" edge="end" size="small" onClick={() => onRemove(file)}>
                <CloseCircleFilled style={{ fontSize: '1rem' }} />
              </IconButton>
            )}
          </ListItem>
        );
      })}
    </List>
  );
}

FilesPreview.propTypes = { showList: PropTypes.bool, files: PropTypes.any, onRemove: PropTypes.any, type: PropTypes.any };

