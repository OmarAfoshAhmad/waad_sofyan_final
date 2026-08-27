import { Chip, Tooltip } from '@mui/material';
import { memberStatusColor, memberStatusLabel } from './memberView.helpers';

/**
 * Clickable status chip used for both the principal header and each
 * dependent row -- was two byte-identical inline blocks before this
 * extraction. Clicking opens the shared status-change menu (owned by the
 * parent, positioned via `onClick`'s event.currentTarget).
 */
const MemberStatusChip = ({ status, blockedReason, onClick, size = 'small' }) => (
  <Tooltip title={status === 'SUSPENDED' && blockedReason ? `سبب الإيقاف: ${blockedReason}` : 'اضغط لتغيير الحالة'}>
    <Chip
      label={memberStatusLabel(status)}
      color={memberStatusColor(status)}
      size={size}
      onClick={onClick}
      sx={{ height: '1.5rem', fontSize: '0.75rem', cursor: 'pointer' }}
    />
  </Tooltip>
);

export default MemberStatusChip;
