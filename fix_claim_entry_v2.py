import os

file_path = r'd:\tba_waad_system-main\tba_waad_system-main\frontend\src\pages\claims\batches\ClaimBatchEntry.jsx'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = 0
for i in range(len(lines)):
    if skip > 0:
        skip -= 1
        continue
    
    # Matching the broken part exactly as shown in view_file
    if '<Stack direction="row" spacing={0} justifyContent="center" sx={{ \'& .MuiIconButton-root\': { p: 0.5 } }}>' in lines[i]:
        # Check if next lines match the expected broken pattern
        if i + 3 < len(lines) and 'removeLine(idx)' in lines[i+1] and 'DeleteIcon' in lines[i+2] and '</TableCell>' in lines[i+4]:
             # Wait, the view_file showed:
             # 1079: Stack
             # 1080: IconButton
             # 1081: DeleteIcon
             # 1082: IconButton closing
             # 1083: TableCell closing
             
             indent = lines[i][:lines[i].find('<Stack')]
             new_lines.append(f'{indent}<TableCell align="center">\n')
             new_lines.append(f'{indent}    <Stack direction="row" spacing={{0}} justifyContent="center" sx={{{{ \'& .MuiIconButton-root\': {{ p: 0.5 }} }}}}>\n')
             new_lines.append(f'{indent}        <IconButton size="small" color={{line.rejected ? "error" : "default"}}\n')
             new_lines.append(f'{indent}            onClick={{() => line.rejected ? updateLine(idx, {{ rejected: false }}) : openRejectDialog("line", idx)}}>\n')
             new_lines.append(f'{indent}            <RejectIcon sx={{{{ fontSize: 15 }}}} />\n')
             new_lines.append(f'{indent}        </IconButton>\n')
             new_lines.append(f'{indent}        <IconButton size="small" color="error" onClick={{() => removeLine(idx)}}>\n')
             new_lines.append(f'{indent}            <DeleteIcon sx={{{{ fontSize: 15 }}}} />\n')
             new_lines.append(f'{indent}        </IconButton>\n')
             new_lines.append(f'{indent}    </Stack>\n')
             new_lines.append(f'{indent}</TableCell>\n')
             skip = 4 # Skip the 4 original lines that were part of the broken block
        else:
            # If it doesn't match the full pattern, still try to wrap it if it's the broken one
             indent = lines[i][:lines[i].find('<Stack')]
             new_lines.append(f'{indent}<TableCell align="center">\n')
             new_lines.append(lines[i])
             # The rest will be handled in next iterations or I can just fix it here
             # Let's do a more direct string replacement on the whole file content instead.
             pass

# Revision: simplify. I'll just use string replacement on the whole content.
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

broken = """                                                <Stack direction="row" spacing={0} justifyContent="center" sx={{ '& .MuiIconButton-root': { p: 0.5 } }}>
                                                    <IconButton size="small" color="error" onClick={() => removeLine(idx)}>
                                                        <DeleteIcon sx={{ fontSize: 15 }} />
                                                    </IconButton>
                                                </TableCell>"""

fixed = """                                                <TableCell align="center">
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

# Try with \n
text = text.replace(broken, fixed)
# Try with \r\n
text = text.replace(broken.replace('\n', '\r\n'), fixed.replace('\n', '\r\n'))

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Fix applied")
