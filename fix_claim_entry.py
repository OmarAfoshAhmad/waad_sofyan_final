import os

file_path = r'd:\tba_waad_system-main\tba_waad_system-main\frontend\src\pages\claims\batches\ClaimBatchEntry.jsx'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Combine action headers
old_header = """                                        <TH align="center" w={40}></TH>
                                        <TH align="center" w={40}></TH>"""
new_header = '                                        <TH align="center" w={70}></TH>'
# Using a more robust match for headers
content = content.replace('<TH align="center" w={40}></TH>\n                                        <TH align="center" w={40}></TH>', 
                          '<TH align="center" w={70}></TH>')
content = content.replace('<TH align="center" w={40}></TH>\r\n                                        <TH align="center" w={40}></TH>', 
                          '<TH align="center" w={70}></TH>')

# 2. Add usage info
old_no_options = 'noOptionsText={loadingServices ? "جاري تحميل خدمات العقد..." : "لم يتم العثور على خدمات في العقد"}'
usage_block = """
                                                    {line.usageDetails && (
                                                        <Box sx={{ mt: 0.5, px: 0.5, display: 'flex', flexWrap: 'wrap', gap: 1, borderTop: '1px dashed #eee', pt: 0.3 }}>
                                                            {line.usageDetails.timesLimit > 0 && (
                                                                <Typography variant="caption" sx={{ fontSize: '0.62rem', color: line.usageDetails.timesExceeded ? 'error.main' : 'text.secondary', fontWeight: 800 }}>
                                                                    المرات: {line.usageDetails.usedCount}/{line.usageDetails.timesLimit} (متبقي {Math.max(0, line.usageDetails.timesLimit - line.usageDetails.usedCount)})
                                                                </Typography>
                                                            )}
                                                            {line.usageDetails.amountLimit > 0 && (
                                                                <Typography variant="caption" sx={{ fontSize: '0.62rem', color: line.usageDetails.amountExceeded ? 'error.main' : 'text.secondary', fontWeight: 800 }}>
                                                                    المبلغ: {line.usageDetails.usedAmount}/{line.usageDetails.amountLimit} (متبقي {Math.max(0, line.usageDetails.amountLimit - line.usageDetails.usedAmount).toFixed(2)})
                                                                </Typography>
                                                            )}
                                                        </Box>
                                                    )}"""

content = content.replace(old_no_options + '\n                                                    />', 
                          old_no_options + '\n                                                    />' + usage_block)
content = content.replace(old_no_options + '\r\n                                                    />', 
                          old_no_options + '\r\n                                                    />' + usage_block)


# 3. Fix action buttons (The broken part)
broken_stack = '<Stack direction="row" spacing={0} justifyContent="center" sx={{ \'& .MuiIconButton-root\': { p: 0.5 } }}>'
new_action_cell = """                                                <TableCell align="center">
                                                    <Stack direction="row" spacing={0} justifyContent="center" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
                                                        <IconButton size="small" color={line.rejected ? "error" : "default"}
                                                            onClick={() => line.rejected ? updateLine(idx, { rejected: false }) : openRejectDialog('line', idx)}>
                                                            <RejectIcon sx={{ fontSize: 15 }} />
                                                        </IconButton>
                                                        <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
                                                            <DeleteIcon sx={{ fontSize: 15 }} />
                                                        </IconButton>
                                                    </Stack>
                                                </TableCell>"""

# Find the broken part and replace it
import re
pattern = re.compile(r'\s*<Stack direction="row" spacing=\{0\} justifyContent="center" sx=\{\{ \'\& \.MuiIconButton-root\': \{ p: 0\.5 \} \}\}>\s*<IconButton size="small" color="error" onClick=\{\(\) => removeLine\(idx\) \}>\s*<DeleteIcon sx=\{\{ fontSize: 15 \}\} />\s*</IconButton>\s*</TableCell>', re.MULTILINE)

content = pattern.sub(new_action_cell, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Replacement complete")
