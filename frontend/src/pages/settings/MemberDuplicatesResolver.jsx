import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  Card,
  CardContent,
  CircularProgress,
  Button,
  Radio,
  RadioGroup,
  FormControlLabel,
  FormControl,
  Alert,
  Divider,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Grid,
} from "@mui/material";
import { Group as GroupIcon, MergeType as MergeIcon, FamilyRestroom as FamilyIcon } from "@mui/icons-material";
import axios from "../../utils/axios";

export default function MemberDuplicatesResolver() {
  const [loading, setLoading] = useState(true);
  const [duplicates, setDuplicates] = useState([]);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const [selectedPrimaryMap, setSelectedPrimaryMap] = useState({});
  const [mergingId, setMergingId] = useState(null);

  const [searchTerm, setSearchTerm] = useState("");
  const [familyDialog, setFamilyDialog] = useState({ open: false, group: null });

  const fetchDuplicates = async () => {
    setLoading(true);
    try {
      const response = await axios.get("/api/v1/system-settings/member-duplicates");
      if (response.data?.status === 'success') {
        setDuplicates(response.data.data);
        
        // Auto-select the one with most visits/claims as primary by default
        const newPrimaryMap = {};
        response.data.data.forEach((group, index) => {
          if (group.members && group.members.length > 0) {
            let bestMember = group.members[0];
            let bestScore = -1;
            group.members.forEach(m => {
              let score = m.visitCount + m.claimCount + m.dependentCount;
              if (score > bestScore) {
                bestScore = score;
                bestMember = m;
              }
            });
            newPrimaryMap[index] = bestMember.id;
          }
        });
        setSelectedPrimaryMap(newPrimaryMap);
      }
    } catch (err) {
      console.error(err);
      setError("حدث خطأ أثناء جلب البيانات المكررة");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDuplicates();
  }, []);

  const handlePrimaryChange = (groupIndex, memberId) => {
    setSelectedPrimaryMap({
      ...selectedPrimaryMap,
      [groupIndex]: parseInt(memberId)
    });
  };

  const handleMerge = async (groupIndex, group) => {
    const primaryId = selectedPrimaryMap[groupIndex];
    if (!primaryId) return;

    const duplicateIds = group.members.map(m => m.id).filter(id => id !== primaryId);

    setMergingId(groupIndex);
    try {
      const res = await axios.post("/api/v1/system-settings/member-duplicates/merge", {
        primaryMemberId: primaryId,
        duplicateMemberIds: duplicateIds
      });
      if (res.data?.status === 'success') {
        setSuccessMsg(`تم دمج المشترك "${group.normalizedName}" بنجاح!`);
        // Remove this group from the list
        setDuplicates(duplicates.filter((_, i) => i !== groupIndex));
      }
    } catch (err) {
      console.error(err);
      setError("حدث خطأ أثناء الدمج");
    } finally {
      setMergingId(null);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
        <GroupIcon sx={{ mr: 1, color: 'primary.main', fontSize: 30 }} />
        <Typography variant="h5" fontWeight="bold">
          معالج تكرار المشتركين
        </Typography>
      </Box>

      <Typography variant="body1" color="text.secondary" mb={3}>
        هذه الأداة تقوم بالبحث التلقائي عن المشتركين المكررين في النظام وتسمح لك بدمج الحركات (المطالبات والزيارات) الخاصة بهم في حساب واحد ثم حذف البقية.
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>{error}</Alert>}
      {successMsg && <Alert severity="success" sx={{ mb: 3 }} onClose={() => setSuccessMsg(null)}>{successMsg}</Alert>}

      {duplicates.length === 0 ? (
        <Alert severity="info">لا يوجد أي تكرارات في النظام! جميع بيانات المشتركين سليمة.</Alert>
      ) : (
        <Box>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', mb: 3, gap: 2, bgcolor: 'background.paper', p: 2, borderRadius: 2, boxShadow: 1 }}>
            <Box sx={{ display: 'flex', gap: 2 }}>
               <Chip label={`إجمالي الحالات المكررة: ${duplicates.length}`} color="primary" variant="outlined" />
               <Chip label={`إجمالي السجلات المكررة: ${duplicates.reduce((acc, curr) => acc + (curr.members?.length || 0), 0)}`} color="secondary" variant="outlined" />
            </Box>
            <Box>
              <input 
                type="text" 
                placeholder="بحث بالاسم أو البطاقة..." 
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{ padding: '8px 12px', borderRadius: '4px', border: '1px solid #ccc', width: '250px' }}
              />
            </Box>
          </Box>
          <Grid container spacing={3}>
            {duplicates.filter(group => group.normalizedName.includes(searchTerm) || (group.members && group.members.some(m => m.cardNumber.includes(searchTerm) || (m.nationalNumber && m.nationalNumber.includes(searchTerm))))).map((group, index) => (
            <Grid item xs={12} key={index}>
              <Card sx={{ borderLeft: '4px solid', borderLeftColor: 'warning.main' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Box>
                      <Typography variant="h6" color="primary">
                        {group.normalizedName}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {group.isPrincipal 
                          ? `موظف رئيسي - جهة العمل: ${group.employerName}` 
                          : `تابع - رقم بطاقة الرئيسي: ${group.parentCardNumber}`}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <Button variant="outlined" size="small" startIcon={<FamilyIcon/>} onClick={() => setFamilyDialog({ open: true, group })}>
                           عرض العائلة للتأكد
                        </Button>
                        <Chip label={`${group.members.length} سجلات مكررة`} color="warning" size="small" />
                    </Box>
                  </Box>

                  <Divider sx={{ mb: 2 }} />

                  <Typography variant="subtitle2" mb={1} color="text.primary">
                    اختر السجل الصحيح (الرئيسي) الذي تريد الاحتفاظ به:
                  </Typography>

                  <FormControl component="fieldset" fullWidth>
                    <RadioGroup
                      value={selectedPrimaryMap[index] || ""}
                      onChange={(e) => handlePrimaryChange(index, e.target.value)}
                    >
                      {group.members.map(member => (
                        <Box 
                          key={member.id} 
                          sx={{ 
                            p: 1.5, 
                            mb: 1, 
                            border: '1px solid', 
                            borderColor: selectedPrimaryMap[index] === member.id ? 'primary.main' : 'divider',
                            borderRadius: 1,
                            backgroundColor: selectedPrimaryMap[index] === member.id ? 'primary.50' : 'transparent',
                            display: 'flex',
                            alignItems: 'center'
                          }}
                        >
                          <FormControlLabel 
                            value={member.id} 
                            control={<Radio />} 
                            label=""
                            sx={{ mr: 1 }}
                          />
                          <Box sx={{ flexGrow: 1 }}>
                            <Typography variant="body1" fontWeight="bold">
                              {member.fullName} <Typography component="span" variant="caption" color="text.secondary">({member.cardNumber})</Typography>
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 2, mt: 0.5 }}>
                              {member.nationalNumber && <Typography variant="caption">رقم وطني: {member.nationalNumber}</Typography>}
                              <Typography variant="caption">تاريخ الإضافة: {new Date(member.createdAt).toLocaleDateString('ar-LY')}</Typography>
                            </Box>
                          </Box>
                          
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            {member.visitCount > 0 && <Chip size="small" label={`${member.visitCount} زيارات`} color="info" variant="outlined" />}
                            {member.claimCount > 0 && <Chip size="small" label={`${member.claimCount} مطالبات`} color="error" variant="outlined" />}
                            {member.dependentCount > 0 && <Chip size="small" label={`${member.dependentCount} تابعين`} color="success" variant="outlined" />}
                            {(member.visitCount === 0 && member.claimCount === 0 && member.dependentCount === 0) && (
                               <Chip size="small" label="لا يوجد حركات" color="default" variant="outlined" />
                            )}
                          </Box>
                        </Box>
                      ))}
                    </RadioGroup>
                  </FormControl>

                  <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<MergeIcon />}
                      onClick={() => handleMerge(index, group)}
                      disabled={mergingId === index || !selectedPrimaryMap[index]}
                    >
                      {mergingId === index ? "جاري الدمج..." : "دمج السجلات المكررة"}
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
        </Box>
      )}

      <Dialog open={familyDialog.open} onClose={() => setFamilyDialog({ open: false, group: null })} maxWidth="sm" fullWidth>
        <DialogTitle>بيانات العائلة للتأكد: {familyDialog.group?.normalizedName}</DialogTitle>
        <DialogContent dividers>
           {familyDialog.group && (
              <Box>
                 {familyDialog.group.isPrincipal ? (
                    <>
                       <Typography variant="subtitle1" fontWeight="bold" color="primary" mb={1}>التابعين المرتبطين بكل سجل:</Typography>
                       {familyDialog.group.members.map((m) => (
                           <Box key={m.id} sx={{ mb: 2, p: 1.5, bgcolor: 'background.default', borderRadius: 1, border: '1px solid #eee' }}>
                              <Typography variant="body2" fontWeight="bold">سجل البطاقة ({m.cardNumber}): {m.dependentCount} تابعين</Typography>
                              {m.dependentNames && m.dependentNames.length > 0 ? (
                                  <ul style={{ margin: '5px 0 0 0', paddingInlineStart: '20px' }}>
                                     {m.dependentNames.map((name, idx) => <li key={idx}><Typography variant="caption">{name}</Typography></li>)}
                                  </ul>
                              ) : <Typography variant="caption" color="text.secondary">لا يوجد تابعين مرتبطين بهذا السجل</Typography>}
                           </Box>
                       ))}
                    </>
                 ) : (
                    <>
                       <Typography variant="subtitle1" fontWeight="bold" color="primary" mb={1}>بيانات المشترك الرئيسي (رب الأسرة):</Typography>
                       <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1, border: '1px solid #eee' }}>
                           <Typography variant="body2" mb={1}>الاسم: <strong>{familyDialog.group.parentName || 'غير متوفر'}</strong></Typography>
                           <Typography variant="body2">رقم البطاقة: <strong>{familyDialog.group.parentCardNumber || 'غير متوفر'}</strong></Typography>
                       </Box>
                       <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
                          جميع هذه السجلات المكررة تعتبر (تابع) لنفس المشترك الرئيسي الموضح أعلاه. يمكنك اختيار أي سجل للاحتفاظ به بناءً على عدد الحركات.
                       </Typography>
                    </>
                 )}
              </Box>
           )}
        </DialogContent>
        <DialogActions>
           <Button onClick={() => setFamilyDialog({ open: false, group: null })}>إغلاق</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
