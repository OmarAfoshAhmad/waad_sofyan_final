import { useEffect, useState } from 'react';
import { Autocomplete, Box, Button, Collapse, Stack, TextField } from '@mui/material';
import NotesOutlinedIcon from '@mui/icons-material/NotesOutlined';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

/**
 * The optional half of the claim header.
 *
 * Both fields here are exceptions rather than the normal path: most claims carry
 * no recorded complaint, and most are entered with no pre-authorization to link.
 * Keeping the pre-auth field in the primary row cost every clerk a field of
 * width and attention on every claim, to serve the minority that use it.
 *
 * The section opens by itself whenever either field already carries data --
 * persisted values must never sit hidden behind a collapsed panel, which is the
 * failure mode a collapsible optional section invites.
 */
export const ClaimAdditionalDetails = ({
  complaint,
  setComplaint,
  setIsDirty,
  preAuthResults,
  searchingPreAuth,
  preAuthId,
  setPreAuthId,
  doctorName,
  setDoctorName
}) => {
  const hasComplaint = Boolean(complaint?.trim());
  const hasPreAuth = Boolean(preAuthId);
  const hasDoctor = Boolean(doctorName?.trim());
  const hasDetails = hasComplaint || hasPreAuth || hasDoctor;
  const [open, setOpen] = useState(hasDetails);

  // Existing claim/draft data is hydrated after the first render. Never leave
  // persisted clinical text or a linked approval hidden behind a closed section.
  useEffect(() => {
    if (hasDetails) setOpen(true);
  }, [hasDetails]);

  const update = (setter) => (event) => {
    setter(event.target.value);
    setIsDirty(true);
  };

  const options = Array.isArray(preAuthResults) ? preAuthResults : [];
  const recorded = [
    hasPreAuth ? 'موافقة مسبقة' : null,
    hasDoctor ? 'طبيب' : null,
    hasComplaint ? 'شكوى' : null
  ]
    .filter(Boolean)
    .join(' و');

  return (
    <Box>
      <Button
        size="small"
        variant="text"
        startIcon={<NotesOutlinedIcon />}
        endIcon={<ExpandMoreIcon sx={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 150ms' }} />}
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        {/* The label names what is actually recorded, so a collapsed section
            never hides the fact that something is set. */}
        تفاصيل إضافية{recorded ? ` · ${recorded}` : ' (اختياري)'}
      </Button>
      <Collapse in={open}>
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          <Autocomplete
            size="small"
            options={options}
            loading={searchingPreAuth}
            value={options.find((item) => String(item.id) === String(preAuthId)) || null}
            onChange={(_, value) => {
              setPreAuthId(value?.id || '');
              setIsDirty(true);
            }}
            getOptionLabel={(item) => `${item.number || item.id} · ${item.serviceName || 'خدمة معتمدة'} · ${item.approvedAmount ?? '-'} د.ل`}
            isOptionEqualToValue={(option, value) => option.id === value?.id}
            renderInput={(params) => <TextField {...params} size="small" label="ربط موافقة مسبقة صالحة" />}
            noOptionsText="لا توجد موافقة صالحة لهذا المستفيد والمزود في تاريخ الخدمة"
          />
          {/* Optional here and optional on the server: ClaimCreateDto declares
              doctorName under OPTIONAL FIELDS with only a length bound, and the
              column is nullable. The required marker it carried in the header
              row was a front-end invention that blocked submission over a field
              the system never needed. */}
          <TextField
            size="small"
            label="اسم الطبيب المعالج"
            value={doctorName}
            onChange={update(setDoctorName)}
            inputProps={{ maxLength: 255 }}
          />
          <TextField
            size="small"
            label="شكوى المستفيد"
            value={complaint}
            onChange={update(setComplaint)}
            multiline
            minRows={2}
            inputProps={{ maxLength: 1000 }}
          />
        </Stack>
      </Collapse>
    </Box>
  );
};
