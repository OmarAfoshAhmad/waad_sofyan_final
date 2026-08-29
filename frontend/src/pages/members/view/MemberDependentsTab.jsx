import {
  Box,
  Button,
  Chip,
  Divider,
  FormControlLabel,
  IconButton,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Tooltip,
  Typography
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon, Edit as EditIcon, RestoreFromTrash as RestoreFromTrashIcon } from '@mui/icons-material';
import MemberAvatar from 'components/tba/MemberAvatar';
import { formatDate } from 'utils/formatters';
import { GENDERS } from 'services/api/unified-members.service';
import { RELATIONSHIP_AR } from '../member.shared';
import MemberStatusChip from './MemberStatusChip';

/**
 * Tab 1 (principal only): dependents table with add/edit/delete/restore.
 * Pagination and the "show deleted" toggle are owned by UnifiedMemberView
 * (the same `pg`/`rpp` used to survive tab switches without resetting).
 */
const MemberDependentsTab = ({
  dependents,
  showDeleted,
  onToggleShowDeleted,
  onAddClick,
  onEditClick,
  onDeleteConfirm,
  onRestore,
  onHardDeleteConfirm,
  onOpenStatusMenu,
  pg,
  rpp,
  onChangePage,
  onChangeRowsPerPage
}) => {
  const visibleDependents = dependents.filter((dep) =>
    showDeleted ? dep.active === false || dep.status === 'TERMINATED' : dep.status !== 'TERMINATED'
  );

  return (
    <Stack spacing={3}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: '1.0rem' }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="subtitle1" fontWeight="bold">
            التابعون المسجلون
          </Typography>
          <FormControlLabel
            control={<Switch checked={showDeleted} onChange={(e) => onToggleShowDeleted(e.target.checked)} color="warning" size="small" />}
            label={
              <Typography variant="body2" color={showDeleted ? 'warning.main' : 'text.secondary'}>
                عرض المحذوفات
              </Typography>
            }
          />
        </Stack>
        <Button variant="contained" startIcon={<AddIcon />} onClick={onAddClick} disabled={showDeleted}>
          إضافة تابع
        </Button>
      </Stack>

      <Divider />

      <Box>
        {dependents.length === 0 ? (
          <Typography variant="body2" align="center" color="text.secondary" sx={{ py: '1.5rem' }}>
            لا يوجد تابعين مسجلين حالياً.
          </Typography>
        ) : (
          <>
            <TableContainer component={Paper} elevation={0} variant="outlined" sx={{ minHeight: '14.375rem' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell align="center">#</TableCell>
                    <TableCell align="center">الاسم</TableCell>
                    <TableCell align="center">القرابة</TableCell>
                    <TableCell align="center">رقم البطاقة</TableCell>
                    <TableCell align="center">الرقم الوطني</TableCell>
                    <TableCell align="center">الجنس</TableCell>
                    <TableCell align="center">تاريخ الميلاد</TableCell>
                    <TableCell align="center">الحالة</TableCell>
                    <TableCell align="center">إجراءات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {visibleDependents.slice(pg * rpp, pg * rpp + rpp).map((dep, index) => (
                    <TableRow key={dep.id} hover>
                      <TableCell align="center">{pg * rpp + index + 1}</TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" fontWeight="medium">
                          {dep.fullName}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={RELATIONSHIP_AR[dep.relationship] || dep.relationship}
                          size="small"
                          variant="outlined"
                          color="primary"
                        />
                      </TableCell>
                      <TableCell align="center">{dep.cardNumber || '-'}</TableCell>
                      <TableCell align="center">{dep.nationalNumber || '-'}</TableCell>
                      <TableCell align="center">
                        {dep.gender === GENDERS.MALE ? 'ذكر' : dep.gender === GENDERS.FEMALE ? 'أنثى' : '-'}
                      </TableCell>
                      <TableCell align="center" dir="ltr">
                        {formatDate(dep.birthDate)}
                      </TableCell>
                      <TableCell align="center">
                        <MemberStatusChip
                          status={dep.status}
                          blockedReason={dep.blockedReason}
                          onClick={(e) => onOpenStatusMenu(e, dep.id)}
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="row" spacing={1} justifyContent="center">
                          {showDeleted ? (
                            <>
                              <Tooltip title="استعادة">
                                <IconButton size="small" color="success" onClick={() => onRestore(dep.id)}>
                                  <RestoreFromTrashIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="حذف نهائي">
                                <IconButton size="small" color="error" onClick={() => onHardDeleteConfirm(dep)}>
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          ) : (
                            <>
                              <Tooltip title="تعديل">
                                <IconButton size="small" color="secondary" onClick={() => onEditClick(dep)}>
                                  <EditIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="حذف">
                                <IconButton size="small" color="error" onClick={() => onDeleteConfirm(dep)}>
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={[6, 12, 24]}
              component="div"
              count={dependents.length}
              rowsPerPage={rpp}
              page={pg}
              onPageChange={onChangePage}
              onRowsPerPageChange={onChangeRowsPerPage}
              labelRowsPerPage="صفوف لكل صفحة:"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`}
            />
          </>
        )}
      </Box>
    </Stack>
  );
};

export default MemberDependentsTab;
