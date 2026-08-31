import { useEffect, useState } from 'react';
import { Box, Button, Collapse, TextField } from '@mui/material';
import NotesOutlinedIcon from '@mui/icons-material/NotesOutlined';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

export const ClaimAdditionalDetails = ({ complaint, setComplaint, setIsDirty }) => {
  const hasDetails = Boolean(complaint?.trim());
  const [open, setOpen] = useState(hasDetails);

  // Existing claim/draft data is hydrated after the first render. Never leave
  // persisted clinical text hidden behind a closed optional section.
  useEffect(() => {
    if (hasDetails) setOpen(true);
  }, [hasDetails]);

  const update = (setter) => (event) => {
    setter(event.target.value);
    setIsDirty(true);
  };

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
        شكوى المستفيد{hasDetails ? ' · مسجّلة' : ' (اختياري)'}
      </Button>
      <Collapse in={open}>
        <Box sx={{ mt: 1 }}>
          <TextField
            size="small"
            label="شكوى المستفيد"
            value={complaint}
            onChange={update(setComplaint)}
            multiline
            minRows={2}
            inputProps={{ maxLength: 1000 }}
          />
        </Box>
      </Collapse>
    </Box>
  );
};
