import PropTypes from 'prop-types';
import { Button } from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';

/**
 * Reusable toggle button for switching between active and soft-deleted records.
 * Provides a unified look and terminology across the entire system.
 */
const SoftDeleteToggle = ({ showDeleted, onToggle }) => {
    return (
        <Button
            variant={showDeleted ? 'contained' : 'outlined'}
            startIcon={showDeleted ? <VisibilityIcon /> : <DeleteIcon />}
            onClick={onToggle}
            color={showDeleted ? 'error' : 'inherit'}
            sx={{
                minWidth: '155px',
                ...(showDeleted && {
                    bgcolor: '#d32f2f',
                    '&:hover': { bgcolor: '#b71c1c' }
                })
            }}
        >
            {showDeleted ? 'عرض النشطة' : 'سجل المحذوفات'}
        </Button>
    );
};

SoftDeleteToggle.propTypes = {
    showDeleted: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired
};

export default SoftDeleteToggle;
