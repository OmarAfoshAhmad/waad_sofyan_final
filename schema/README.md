# Schema Directory — Per-Table SQL Files

هذا المجلد يحتوي على تعريف قاعدة البيانات موزعاً — **جدول واحد في كل ملف**.
المحتوى مشتق من `V200__consolidated_clean_baseline.sql` ويمثل الحالة النهائية للمخطط بعد جميع التعديلات.

---

## البنية / Structure

```
schema/
├── 00_sequences.sql          ← جميع التسلسلات (sequences)
├── README.md
└── tables/
    ├── 01_employers.sql
    ├── 02_providers.sql
    ├── 03_provider_allowed_employers.sql
    ├── 04_provider_admin_documents.sql
    ├── 05_users.sql
    ├── 06_email_verification_tokens.sql
    ├── 07_password_reset_tokens.sql
    ├── 08_user_login_attempts.sql
    ├── 09_user_audit_log.sql
    ├── 10_system_settings.sql
    ├── 11_feature_flags.sql
    ├── 12_module_access.sql
    ├── 13_audit_logs.sql
    ├── 14_pdf_company_settings.sql
    ├── 15_medical_categories.sql
    ├── 16_medical_category_roots.sql
    ├── 17_medical_specialties.sql
    ├── 18_medical_services.sql
    ├── 19_medical_service_categories.sql
    ├── 20_ent_service_aliases.sql
    ├── 21_cpt_codes.sql
    ├── 22_icd_codes.sql
    ├── 23_provider_services.sql
    ├── 24_medical_reviewer_providers.sql
    ├── 25_provider_service_price_import_logs.sql
    ├── 26_benefit_policies.sql
    ├── 27_benefit_policy_rules.sql
    ├── 28_provider_contracts.sql
    ├── 29_provider_contract_pricing_items.sql
    ├── 30_network_providers.sql
    ├── 31_legacy_provider_contracts.sql
    ├── 32_members.sql
    ├── 33_member_attributes.sql
    ├── 34_member_deductibles.sql
    ├── 35_member_policy_assignments.sql
    ├── 36_member_import_logs.sql
    ├── 37_member_import_errors.sql
    ├── 38_visits.sql
    ├── 39_visit_attachments.sql
    ├── 40_eligibility_checks.sql
    ├── 41_preauthorization_requests.sql
    ├── 42_pre_authorizations.sql
    ├── 43_pre_authorization_attachments.sql
    ├── 44_pre_authorization_audit.sql
    ├── 45_claim_batches.sql
    ├── 46_claims.sql
    ├── 47_claim_lines.sql
    ├── 48_claim_attachments.sql
    ├── 49_claim_history.sql
    ├── 50_claim_audit_logs.sql
    ├── 51_provider_accounts.sql
    └── 52_account_transactions.sql
```

---

## ترتيب الإنشاء / Creation Order

يجب تطبيق الملفات **بالترتيب الرقمي** لأن بعض الجداول تعتمد على جداول أخرى (Foreign Keys):

| المجموعة | الجداول                      | الاعتماد                                           |
| -------- | ---------------------------- | -------------------------------------------------- |
| 00       | Sequences                    | لا يوجد                                            |
| 01–02    | employers, providers         | لا يوجد                                            |
| 03–04    | provider sub-tables          | employers, providers                               |
| 05       | users                        | employers, providers                               |
| 06–09    | auth + audit                 | users                                              |
| 10–14    | system config                | لا يوجد                                            |
| 15–22    | medical taxonomy             | self-ref, medical_categories                       |
| 23–25    | provider services            | providers, users                                   |
| 26–31    | benefits + contracts         | employers, providers, medical_services             |
| 32–37    | members                      | employers, benefit_policies                        |
| 38–44    | visits, eligibility, preauth | members, employers, providers                      |
| 45–50    | claims                       | members, providers, visits, preauth, claim_batches |
| 51–52    | financial                    | providers, provider_accounts                       |

---

## تطبيق المخطط / Apply Schema

### قاعدة بيانات جديدة (Fresh Install)

```powershell
.\scripts\03_apply_schema.ps1
```

### يدوياً (Manual)

```bash
psql -U postgres -d tba_waad_system -f schema/00_sequences.sql
psql -U postgres -d tba_waad_system -f schema/tables/01_employers.sql
# ... وهكذا بالترتيب
```

---

## ملاحظات / Notes

- جميع الجداول تستخدم `CREATE TABLE IF NOT EXISTS` — آمن للتطبيق المتكرر.
- الجداول المحذوفة **غير موجودة** هنا: `settlement_batches`, `settlement_batch_items`,
  `provider_payments`, `provider_raw_services`, `provider_service_mappings`, `provider_mapping_audit`.
- المصدر الرسمي للمخطط هو مجلد Flyway migrations:
  `backend/src/main/resources/db/migration/`
