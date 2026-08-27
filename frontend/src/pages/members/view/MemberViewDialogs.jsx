import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';
import MemberAvatar from 'components/tba/MemberAvatar';
import { MEMBER_TYPES } from 'services/api/unified-members.service';

/**
 * All confirmation/utility dialogs owned by UnifiedMemberView: the enlarged
 * photo view, the suspend-with-reason dialog, and the two delete
 * confirmations (dependent hard-delete, member soft-delete). Bundled into
 * one file since each is small and they share no state with each other --
 * only with the parent, which passes everything down explicitly.
 */
const MemberViewDialogs = ({
  member,
  photoDialogOpen,
  onClosePhoto,
  statusChangeDialog,
  onCloseStatusChange,
  onChangeStatusReason,
  onConfirmStatusChange,
  statusChangeLoading,
  hardDeleteDepDialogOpen,
  hardDeletingDep,
  onCloseHardDeleteDep,
  onConfirmHardDeleteDep,
  deleteDialogOpen,
  deletingMember,
  onCloseDelete,
  onConfirmDelete
}) => (
  <>
    <Dialog open={photoDialogOpen} onClose={onClosePhoto} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ textAlign: 'center' }}>صورة المستفيد</DialogTitle>
      <DialogContent sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <MemberAvatar member={member} size={260} />
      </DialogContent>
      <DialogActions sx={{ justifyContent: 'center', pb: 2 }}>
        <Button variant="contained" onClick={onClosePhoto}>
          إغلاق
        </Button>
      </DialogActions>
    </Dialog>

    <Dialog open={statusChangeDialog.open} onClose={() => (statusChangeLoading ? null : onCloseStatusChange())} maxWidth="xs" fullWidth>
      <DialogTitle>تعليق المستفيد</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>يرجى توضيح سبب تعليق هذا المستفيد.</DialogContentText>
        <TextField
          autoFocus
          fullWidth
          multiline
          minRows={2}
          label="سبب التعليق"
          value={statusChangeDialog.reason}
          onChange={(e) => onChangeStatusReason(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCloseStatusChange} disabled={statusChangeLoading}>
          إلغاء
        </Button>
        <Button
          variant="contained"
          color="warning"
          disabled={statusChangeLoading || !statusChangeDialog.reason.trim()}
          onClick={onConfirmStatusChange}
        >
          تأكيد التعليق
        </Button>
      </DialogActions>
    </Dialog>

    <Dialog open={hardDeleteDepDialogOpen} onClose={onCloseHardDeleteDep}>
      <DialogTitle sx={{ fontWeight: 600 }}>حذف نهائي؟</DialogTitle>
      <DialogContent>
        <DialogContentText>
          سيتم حذف التابع <strong>{hardDeletingDep?.fullName}</strong> نهائياً من قاعدة البيانات. هذا الإجراء لا يمكن التراجع عنه!
          <Alert severity="error" sx={{ mt: '1.0rem' }}>
            <strong>تنبيه:</strong> إذا كان للتابع مطالبات أو زيارات مرتبطة سيفشل الحذف.
          </Alert>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCloseHardDeleteDep}>إلغاء</Button>
        <Button onClick={onConfirmHardDeleteDep} color="error" variant="contained" autoFocus>
          تأكيد الحذف النهائي
        </Button>
      </DialogActions>
    </Dialog>

    <Dialog open={deleteDialogOpen} onClose={onCloseDelete}>
      <DialogTitle sx={{ fontWeight: 600 }}>تأكيد الحذف</DialogTitle>
      <DialogContent>
        <DialogContentText>
          {deletingMember?.type === MEMBER_TYPES.PRINCIPAL ? (
            <>
              هل أنت متأكد من حذف الموظف <strong>{deletingMember?.fullName}</strong>؟
              <Alert severity="warning" sx={{ mt: '1.0rem' }}>
                <strong>تنبيه:</strong> سيتم حذف جميع التابعين ({member?.dependentsCount || 0}) تلقائياً (CASCADE DELETE).
              </Alert>
            </>
          ) : (
            <>
              هل أنت متأكد من حذف التابع <strong>{deletingMember?.fullName}</strong>؟
            </>
          )}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCloseDelete}>إلغاء</Button>
        <Button onClick={onConfirmDelete} color="error" variant="contained" autoFocus>
          تأكيد الحذف
        </Button>
      </DialogActions>
    </Dialog>
  </>
);

export default MemberViewDialogs;
