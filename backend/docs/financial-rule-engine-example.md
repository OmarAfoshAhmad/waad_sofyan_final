# Financial Rule Engine - Configuration and Trace Example

## Example Rule Configuration JSON

```json
{
  "name": "Times Limit Rule",
  "ruleType": "TIMES_LIMIT_RULE",
  "priority": 10,
  "enabled": true,
  "ruleGroup": "PRE_VALIDATION_RULES",
  "dependencyRules": [],
  "configuration": {}
}
```

```json
{
  "name": "Coverage Percent Rule",
  "ruleType": "COVERAGE_PERCENT_RULE",
  "priority": 20,
  "enabled": true,
  "ruleGroup": "COVERAGE_CALCULATION_RULES",
  "dependencyRules": [],
  "configuration": {
    "roundingMode": "HALF_UP",
    "scale": 2
  }
}
```

```json
{
  "name": "Amount Limit Rule",
  "ruleType": "AMOUNT_LIMIT_RULE",
  "priority": 30,
  "enabled": true,
  "ruleGroup": "LIMIT_ENFORCEMENT_RULES",
  "dependencyRules": ["Coverage Percent Rule"],
  "configuration": {}
}
```

## Step-by-Step Execution Trace Example

Input context:

```json
{
  "claimId": 1001,
  "requestedAmount": 200.0,
  "usedTimes": 2,
  "timesLimit": 5,
  "usedAmount": 900.0,
  "amountLimit": 1000.0,
  "coveragePercent": 80.0,
  "correlationId": "CORR-ABC-001"
}
```

1. Times Limit Rule -> PASS (2 < 5)
2. Coverage Percent Rule -> MODIFY coveredAmount=160.00, patientShare=40.00
3. Amount Limit Rule -> MODIFY coveredAmount=100.00, patientShare=100.00 (soft cap)

Final result:

```json
{
  "decisionStatus": "PARTIAL_APPROVED",
  "coveredAmount": 100.0,
  "patientShare": 100.0,
  "applyUsageDelta": true,
  "usageTimesDelta": 1,
  "usageAmountDelta": 100.0
}
```
