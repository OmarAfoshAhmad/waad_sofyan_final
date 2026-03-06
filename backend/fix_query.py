import re
import os

path = r'd:\tba_waad_system-main\tba_waad_system-main\backend\src\main\java\com\waad\tba\modules\claim\repository\ClaimRepository.java'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Identifying the query block
# It starts with COALESCE(SUM(c.requestedAmount), 0),
# And has multiple parts.

# Let's find the specific block for getFinancialSummary
pattern = re.compile(r'(@Query\("SELECT COUNT\(c\),\s*"\s*\+\s*"COALESCE\(SUM\(c\.requestedAmount\),\s*0\),\s*"\s*\+.*?"COALESCE\(SUM\(CASE WHEN c\.status\s*=\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED.*?COUNT\(CASE WHEN c\.status\s*=\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED\s+THEN\s+1\s+END\)\s+"\s*\+)', re.DOTALL)

# Easier: just search for the specific lines 733-741 using a simpler regex
# We want to insert COALESCE(SUM(c.refusedAmount), 0), after the approvedAmount sum.

replacement = """        @Query("SELECT COUNT(c), " +
                      "COALESCE(SUM(c.requestedAmount), 0), " +
                      "COALESCE(SUM(CASE WHEN c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED, com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED) THEN c.approvedAmount ELSE 0 END), 0), " +
                      "COALESCE(SUM(c.refusedAmount), 0), " +
                      "COALESCE(SUM(CASE WHEN c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED THEN COALESCE(c.netProviderAmount, c.approvedAmount) ELSE 0 END), 0), " +
                      "COUNT(CASE WHEN c.status IN (com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED, com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED, com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED) THEN 1 END), " +
                      "COUNT(CASE WHEN c.status = com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED THEN 1 END) " +"""

# Looking for the old block
# I'll use a very specific but space-flexible regex
old_pattern = re.compile(r'\s*@Query\("SELECT COUNT\(c\),\s*"\s*\+\s*"COALESCE\(SUM\(c\.requestedAmount\),\s*0\),\s*"\s*\+\s*"COALESCE\(SUM\(CASE WHEN c\.status\s*IN\s*\(com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.APPROVED,\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED\)\s*THEN\s*c\.approvedAmount\s*ELSE\s*0\s*END\),\s*0\),\s*"\s*\+\s*"COALESCE\(SUM\(CASE WHEN c\.status\s*=\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED\s*THEN\s*COALESCE\(c\.netProviderAmount,\s*c\.approvedAmount\)\s*ELSE\s*0\s*END\),\s*0\),\s*"\s*\+\s*"COUNT\(CASE WHEN c\.status\s*IN\s*\(com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.APPROVED,\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED\)\s*THEN\s*1\s*END\),\s*"\s*\+\s*"COUNT\(CASE WHEN c\.status\s*=\s*com\.waad\.tba\.modules\.claim\.entity\.ClaimStatus\.SETTLED\s*THEN\s*1\s*END\)\s+"\s*\+', re.DOTALL)

new_content = old_pattern.sub(replacement, content)

if new_content != content:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Successfully updated the query.")
else:
    print("Pattern matched 0 times. Content not changed.")
    # Debug: print first few characters around where we expect the match
    idx = content.find('SELECT COUNT(c)')
    if idx != -1:
        print("Found SELECT COUNT(c) at index:", idx)
        print("Surrounding text:", repr(content[idx-20:idx+500]))
    else:
        print("Could not find SELECT COUNT(c) at all.")
