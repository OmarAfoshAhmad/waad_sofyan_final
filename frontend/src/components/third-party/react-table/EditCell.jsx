import PropTypes from 'prop-types';
import { useState } from 'react';

// material-ui
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';
import TableCell from '@mui/material/TableCell';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';

import { useFormik, Field, FormikProvider } from 'formik';
import * as Yup from 'yup';

// project imports
import LinearWithLabel from 'components/@extended/progress/LinearWithLabel';

// assets
import CheckOutlined from '@ant-design/icons/CheckOutlined';

// ==============================|| EDITABLE CELL ||============================== //

export default function EditCell({ cell, onSave }) {
  // @ts-expect-error: columnDef may not always have a `dataType` property
  const dataType = cell.column.columnDef.dataType;
  const columnId = cell.column.id;
  const value = cell.getValue();

  const [isEditMode, setEditMode] = useState(false);

  let validationSchema;
  switch (columnId) {
    case 'fullName':
      validationSchema = Yup.object({
        [columnId]: Yup.string()
          .test('trim', 'Name cannot be empty or contain only spaces', (value) => !!value && value.trim().length > 0)
          .required('Name is required')
      });
      break;
    case 'email':
      validationSchema = Yup.object({
        [columnId]: Yup.string().required('Email is required').email('Invalid email address')
      });
      break;
    case 'age':
      validationSchema = Yup.object({
        [columnId]: Yup.number()
          .typeError('Age must be a number')
          .required('Age is required')
          .min(18, 'Minimum age is 18')
          .max(65, 'Maximum age is 65')
      });
      break;
    case 'visits':
      validationSchema = Yup.object({
        [columnId]: Yup.number()
          .typeError('Visits must be a number')
          .required('Visits are required')
          .positive('Visits must be a positive number')
      });
      break;
    default:
      // For any other fields, use a generic required message
      validationSchema = Yup.object({
        [columnId]: Yup.string().required('This field is required')
      });
      break;
  }

  const formik = useFormik({
    initialValues: {
      [columnId]: value
    },
    validationSchema,
    enableReinitialize: true,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: (values) => {
      const newValue = values[columnId];
      if (value !== newValue) {
        onSave(newValue);
      }
    }
  });

  const handleSaveClick = () => {
    formik.handleSubmit();
  };

  let cellContent;
  switch (dataType) {
    case 'number':
    case 'text':
      cellContent = (
        <Field name={columnId} validateOnChange>
          {({ field, meta }) => (
            <TextField
              {...field}
              fullWidth
              variant="outlined"
              value={field.value}
              onChange={(e) => {
                field.onChange(e);
                formik.setFieldTouched(columnId, true, false);
              }}
              onBlur={(e) => {
                const trimmed = (e.target.value ?? '').trim();
                if (trimmed !== formik.values[columnId]) {
                  formik.setFieldValue(columnId, trimmed, false); // write back to Formik
                }
                handleSaveClick();
              }}
              error={meta.touched && !!meta.error}
              helperText={meta.touched && meta.error}
              slotProps={{ htmlInput: { sx: { py: 0.75 } } }}
              sx={{
                '& .MuiOutlinedInput-input': { py: 0.75, px: 1, minWidth: { xs: 100 } },
                '& .MuiFormHelperText-root': { mx: 0 },
                '& .MuiOutlinedInput-root': {
                  '& .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'transparent' // Default: No border
                  },
                  '&:hover .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'transparent' // No border on hover
                  },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'primary.light' // Show primary color border when focused
                  },
                  '&.Mui-error .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'error.light' // Show error border if there's an error
                  },
                  '&.Mui-error:hover .MuiOutlinedInput-notchedOutline': {
                    borderColor: 'error.light' // Keep error border on hover
                  }
                }
              }}
            />
          )}
        </Field>
      );
      break;
    case 'select':
      cellContent = (
        <Select
          value={formik.values[columnId]}
          onChange={(e) => {
            formik.setFieldValue(columnId, e.target.value);
            formik.setFieldTouched(columnId, true, false);
          }}
          size="small"
          onBlur={handleSaveClick}
          slotProps={{ input: { sx: { py: 0.575 } } }}
          sx={{
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: 'transparent' // Default: No border
            },
            '&:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: 'transparent' // No border on hover
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderColor: 'primary.light' // Show primary border when focused
            },
            '&.Mui-error .MuiOutlinedInput-notchedOutline': {
              borderColor: 'error.light' // Show error border when there's an error
            },
            '&.Mui-error:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: 'error.light' // Keep error border on hover
            },
            '& .MuiSelect-icon': {
              display: 'none' // Hide the dropdown arrow
            },
            '& .MuiSelect-select': {
              paddingRight: '14px !important' // Remove extra padding
            }
          }}
        >
          <MenuItem value="Complicated">
            <Chip color="error" label="Complicated" size="small" variant="light" />
          </MenuItem>
          <MenuItem value="Relationship">
            <Chip color="success" label="Relationship" size="small" variant="light" />
          </MenuItem>
          <MenuItem value="Single">
            <Chip color="info" label="Single" size="small" variant="light" />
          </MenuItem>
        </Select>
      );
      break;
    case 'progress':
      cellContent = isEditMode ? (
        <Stack direction="row" sx={{ gap: 1, alignItems: 'center', pl: 1, minWidth: '7.5rem' }}>
          <Slider
            value={formik.values[columnId]}
            min={0}
            max={100}
            step={1}
            onChange={(_, newValue) => {
              formik.setFieldValue(columnId, newValue);
              formik.setFieldTouched(columnId, true, false);
            }}
            onBlur={handleSaveClick}
            valueLabelDisplay="auto"
            aria-labelledby="non-linear-slider"
          />
          <Tooltip title="Submit">
            <IconButton
              color="primary"
              size="small"
              onClick={() => {
                handleSaveClick();
                setEditMode(false);
              }}
            >
              <CheckOutlined />
            </IconButton>
          </Tooltip>
        </Stack>
      ) : (
        <Box onClick={() => setEditMode(true)}>
          <LinearWithLabel value={formik.values[columnId]} sx={{ minWidth: '4.6875rem' }} />
        </Box>
      );
      break;
    default:
      cellContent = value;
      break;
  }

  return (
    <FormikProvider value={formik}>
      <TableCell key={cell.id} {...cell.column.columnDef.meta}>
        {cellContent}
      </TableCell>
    </FormikProvider>
  );
}

EditCell.propTypes = { cell: PropTypes.any, onSave: PropTypes.any };
