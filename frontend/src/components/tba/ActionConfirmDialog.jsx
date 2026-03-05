import PropTypes from 'prop-types';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogContentText,
    DialogActions,
    Button,
    Typography
} from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

/**
 * Reusable Confirmation Dialog for system actions (Delete, Archive, Restore).
 * Replaces the native browser window.confirm().
 */
const ActionConfirmDialog = ({
    open,
    title,
    message,
    onClose,
    onConfirm,
    confirmText = 'نعم',
    cancelText = 'إلغاء الأمر',
    confirmColor = 'primary',
    icon = null
}) => {
    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {icon || <WarningAmberIcon color={confirmColor} />}
                <Typography variant="h6" component="span">
                    {title}
                </Typography>
            </DialogTitle>
            <DialogContent>
                <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>
                    {message}
                </DialogContentText>
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2 }}>
                <Button onClick={onClose} color="inherit" variant="outlined">
                    {cancelText}
                </Button>
                <Button onClick={onConfirm} color={confirmColor} variant="contained" autoFocus>
                    {confirmText}
                </Button>
            </DialogActions>
        </Dialog>
    );
};

ActionConfirmDialog.propTypes = {
    open: PropTypes.bool.isRequired,
    title: PropTypes.string.isRequired,
    message: PropTypes.string.isRequired,
    onClose: PropTypes.func.isRequired,
    onConfirm: PropTypes.func.isRequired,
    confirmText: PropTypes.string,
    cancelText: PropTypes.string,
    confirmColor: PropTypes.oneOf(['primary', 'secondary', 'error', 'info', 'success', 'warning']),
    icon: PropTypes.node
};

export default ActionConfirmDialog;
