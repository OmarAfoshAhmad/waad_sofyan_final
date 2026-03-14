import PropTypes from 'prop-types';

// material-ui
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Drawer from '@mui/material/Drawer';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';

// third-party
import { Formik, Form } from 'formik';

// assets
import CloseOutlined from '@ant-design/icons/CloseOutlined';
import SaveOutlined from '@ant-design/icons/SaveOutlined';

// ==============================|| CRUD DRAWER - TBA MODULE ||============================== //

export default function CrudDrawer({
  open,
  onClose,
  title,
  initialValues = {},
  validationSchema,
  onSubmit,
  children,
  width = 400,
  submitLabel = 'Save',
  cancelLabel = 'Cancel'
}) {
  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: { xs: '100%', sm: width },
          maxWidth: '100%'
        }
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <Box sx={{ p: '1.25rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="h5">{title}</Typography>
          <IconButton onClick={onClose} size="small" color="secondary">
            <CloseOutlined />
          </IconButton>
        </Box>

        <Divider />

        {/* Content */}
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          <Formik
            initialValues={initialValues}
            validationSchema={validationSchema}
            onSubmit={async (values, { setSubmitting, setErrors }) => {
              try {
                await onSubmit(values);
                onClose();
              } catch (error) {
                console.error('Form submission error:', error);
                if (error.data?.errors) {
                  setErrors(error.data.errors);
                }
                setSubmitting(false);
              }
            }}
            enableReinitialize
          >
            {({ isSubmitting, dirty }) => (
              <Form>
                <Box sx={{ p: '1.25rem' }}>
                  <Stack spacing={2.5}>{typeof children === 'function' ? children({ isSubmitting }) : children}</Stack>
                </Box>

                {/* Footer */}
                <Divider />
                <Box sx={{ p: '1.25rem' }}>
                  <Stack direction="row" spacing={2} justifyContent="flex-end">
                    <Button variant="outlined" color="secondary" onClick={onClose} disabled={isSubmitting}>
                      {cancelLabel}
                    </Button>
                    <Button type="submit" variant="contained" disabled={isSubmitting || !dirty} startIcon={<SaveOutlined />}>
                      {isSubmitting ? 'Saving...' : submitLabel}
                    </Button>
                  </Stack>
                </Box>
              </Form>
            )}
          </Formik>
        </Box>
      </Box>
    </Drawer>
  );
}

CrudDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  title: PropTypes.string.isRequired,
  initialValues: PropTypes.object,
  validationSchema: PropTypes.object,
  onSubmit: PropTypes.func.isRequired,
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.func]).isRequired,
  width: PropTypes.number,
  submitLabel: PropTypes.string,
  cancelLabel: PropTypes.string
};
