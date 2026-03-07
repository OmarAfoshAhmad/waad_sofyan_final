# Database Migrations — TBA WAAD System

## Folder Structure

```
db/migration/
├── V001__sequences.sql              ← All sequences (must run first)
├── V005__schema_employers.sql       ← Employers table
├── V006__schema_providers.sql       ← Providers + allowed employers + docs
├── V010__schema_users.sql           ← Users (auth, flattened RBAC)
├── V011__schema_auth_tokens.sql     ← Email verification + password reset tokens
├── V012__schema_login_audit.sql     ← Login attempts + user audit log
├── V015__schema_system_config.sql   ← System settings + feature flags + module access + PDF settings
├── V020__schema_medical_categories.sql
├── V021__schema_medical_services.sql  ← Services + multi-context categories + aliases
├── V022__schema_medical_specialties.sql
├── V023__schema_medical_codes.sql   ← CPT codes + ICD-10 codes
├── V030__schema_provider_services.sql ← Provider service directory + reviewer assignments
├── V031__schema_provider_mapping.sql  ← Raw services → canonical mapping center
├── V040__schema_benefit_policies.sql  ← Benefit policies + coverage rules
├── V045__schema_provider_contracts.sql ← Contracts + pricing + networks + legacy
├── V050__schema_members.sql         ← Members + attributes + deductibles + assignments
├── V051__schema_member_import.sql   ← Bulk import logs + errors
├── V060__schema_visits.sql          ← Patient visits + attachments
├── V061__schema_eligibility_checks.sql
├── V065__schema_pre_authorization.sql ← Pre-auth requests + compat + attachments + audit
├── V070__schema_claims.sql          ← Insurance claims
├── V071__schema_claim_lines.sql     ← Claim lines + attachments + history + audit logs
├── V080__schema_financial.sql       ← Provider accounts + immutable transaction ledger
├── V081__schema_settlement.sql      ← Settlement batches + items + provider payments
├── V090__indexes.sql                ← All compound / partial performance indexes
├── V095__seed_feature_flags.sql     ← Initial data: feature flags + system settings
│
└── archive/                         ← Old V1–V121 migrations (historical reference)
                                       Flyway does NOT scan subdirectories.
```

## Running Migrations

### Fresh database (recommended for development)
```bash
docker-compose down -v   # wipe volumes
docker-compose up -d     # Flyway runs V001-V095 automatically
```

### Existing database (retain data)
Run once in psql, then restart the backend:
```sql
INSERT INTO flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (999, '999', 'Baseline before reorganization', 'BASELINE',
   '<< Flyway Schema Baseline >>', 0, 'postgres', NOW(), 0, TRUE);
```
Then in `application.yml` uncomment:
```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 999
```

## Adding New Migrations

- Next version: **V096** onward
- Format: `V{NNN}__{entity_name}.sql`
- Place in `db/migration/` only — never in `archive/`
- One primary entity per file (junction tables can share their parent entity file)
