--
-- PostgreSQL database dump
--

\restrict rJwKtMrnRaNB7xTkD5H7WANojUNnjmNtqs7mdbtLu8oKCAlolxnfds7FrVtHQww

-- Dumped from database version 16.13 (Debian 16.13-1.pgdg13+1)
-- Dumped by pg_dump version 16.13 (Debian 16.13-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: providers; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_accounts; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: account_transactions; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: employers; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: benefit_policies; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: medical_categories; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: medical_services; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: benefit_policy_rules; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: members; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: preauthorization_requests; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: claims; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: claim_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: claim_audit_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: claim_history; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: claim_lines; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: cpt_codes; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: eligibility_checks; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: email_verification_tokens; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: ent_service_aliases; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: feature_flags; Type: TABLE DATA; Schema: public; Owner: postgres
--

INSERT INTO public.feature_flags VALUES (1, 'PROVIDER_PORTAL_ENABLED', 'بوابة الخدمة المباشرة', 'تفعيل بوابة إدخال المطالبات المباشرة عبر مزودي الخدمة. عند التعطيل يعمل النظام في وضع الدفعات الشهرية فقط.', false, NULL, 'SYSTEM', NULL, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907');
INSERT INTO public.feature_flags VALUES (2, 'DIRECT_CLAIM_SUBMISSION_ENABLED', 'التقديم المباشر للمطالبات', 'السماح بإنشاء مطالبات فردية مباشرة من بوابة المزود. يتطلب تفعيل PROVIDER_PORTAL_ENABLED أيضاً.', false, NULL, 'SYSTEM', NULL, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907');
INSERT INTO public.feature_flags VALUES (3, 'BATCH_CLAIMS_ENABLED', 'نظام الدفعات الشهرية', 'تفعيل إدخال المطالبات عبر الدفعات الشهرية. هذا هو المسار الأساسي الحالي لإدخال المطالبات.', true, NULL, 'SYSTEM', NULL, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907');


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

INSERT INTO public.flyway_schema_history VALUES (1, '001', 'sequences', 'SQL', 'V001__sequences.sql', 422857321, 'postgres', '2026-03-07 21:50:50.341838', 11, true);
INSERT INTO public.flyway_schema_history VALUES (2, '005', 'schema employers', 'SQL', 'V005__schema_employers.sql', -1582502973, 'postgres', '2026-03-07 21:50:50.383871', 24, true);
INSERT INTO public.flyway_schema_history VALUES (3, '006', 'schema providers', 'SQL', 'V006__schema_providers.sql', -868603061, 'postgres', '2026-03-07 21:50:50.419624', 46, true);
INSERT INTO public.flyway_schema_history VALUES (4, '010', 'schema users', 'SQL', 'V010__schema_users.sql', 583524572, 'postgres', '2026-03-07 21:50:50.478785', 29, true);
INSERT INTO public.flyway_schema_history VALUES (5, '011', 'schema auth tokens', 'SQL', 'V011__schema_auth_tokens.sql', 1442819301, 'postgres', '2026-03-07 21:50:50.516099', 33, true);
INSERT INTO public.flyway_schema_history VALUES (6, '012', 'schema login audit', 'SQL', 'V012__schema_login_audit.sql', 532733667, 'postgres', '2026-03-07 21:50:50.555657', 55, true);
INSERT INTO public.flyway_schema_history VALUES (7, '015', 'schema system config', 'SQL', 'V015__schema_system_config.sql', 544798756, 'postgres', '2026-03-07 21:50:50.621657', 53, true);
INSERT INTO public.flyway_schema_history VALUES (8, '020', 'schema medical categories', 'SQL', 'V020__schema_medical_categories.sql', 1688003485, 'postgres', '2026-03-07 21:50:50.685576', 19, true);
INSERT INTO public.flyway_schema_history VALUES (9, '021', 'schema medical services', 'SQL', 'V021__schema_medical_services.sql', -507708258, 'postgres', '2026-03-07 21:50:50.711175', 45, true);
INSERT INTO public.flyway_schema_history VALUES (10, '022', 'schema medical specialties', 'SQL', 'V022__schema_medical_specialties.sql', -1828102982, 'postgres', '2026-03-07 21:50:50.763271', 7, true);
INSERT INTO public.flyway_schema_history VALUES (11, '023', 'schema medical codes', 'SQL', 'V023__schema_medical_codes.sql', 1526189267, 'postgres', '2026-03-07 21:50:50.775427', 21, true);
INSERT INTO public.flyway_schema_history VALUES (12, '030', 'schema provider services', 'SQL', 'V030__schema_provider_services.sql', -1951623012, 'postgres', '2026-03-07 21:50:50.802303', 36, true);
INSERT INTO public.flyway_schema_history VALUES (13, '031', 'schema provider mapping', 'SQL', 'V031__schema_provider_mapping.sql', 930657646, 'postgres', '2026-03-07 21:50:50.846809', 27, true);
INSERT INTO public.flyway_schema_history VALUES (14, '040', 'schema benefit policies', 'SQL', 'V040__schema_benefit_policies.sql', -1088735389, 'postgres', '2026-03-07 21:50:50.881065', 37, true);
INSERT INTO public.flyway_schema_history VALUES (15, '045', 'schema provider contracts', 'SQL', 'V045__schema_provider_contracts.sql', 406907110, 'postgres', '2026-03-07 21:50:50.923879', 51, true);
INSERT INTO public.flyway_schema_history VALUES (16, '050', 'schema members', 'SQL', 'V050__schema_members.sql', 454344578, 'postgres', '2026-03-07 21:50:50.981331', 71, true);
INSERT INTO public.flyway_schema_history VALUES (17, '051', 'schema member import', 'SQL', 'V051__schema_member_import.sql', 1869405255, 'postgres', '2026-03-07 21:50:51.069781', 27, true);
INSERT INTO public.flyway_schema_history VALUES (18, '060', 'schema visits', 'SQL', 'V060__schema_visits.sql', -1635864118, 'postgres', '2026-03-07 21:50:51.10109', 44, true);
INSERT INTO public.flyway_schema_history VALUES (19, '061', 'schema eligibility checks', 'SQL', 'V061__schema_eligibility_checks.sql', 114896004, 'postgres', '2026-03-07 21:50:51.151837', 30, true);
INSERT INTO public.flyway_schema_history VALUES (20, '065', 'schema pre authorization', 'SQL', 'V065__schema_pre_authorization.sql', 397108841, 'postgres', '2026-03-07 21:50:51.190805', 53, true);
INSERT INTO public.flyway_schema_history VALUES (21, '070', 'schema claims', 'SQL', 'V070__schema_claims.sql', 181329393, 'postgres', '2026-03-07 21:50:51.250572', 36, true);
INSERT INTO public.flyway_schema_history VALUES (22, '071', 'schema claim lines', 'SQL', 'V071__schema_claim_lines.sql', -1401062828, 'postgres', '2026-03-07 21:50:51.293535', 48, true);
INSERT INTO public.flyway_schema_history VALUES (23, '080', 'schema financial', 'SQL', 'V080__schema_financial.sql', -1989012658, 'postgres', '2026-03-07 21:50:51.347066', 47, true);
INSERT INTO public.flyway_schema_history VALUES (24, '081', 'schema settlement', 'SQL', 'V081__schema_settlement.sql', 1910470033, 'postgres', '2026-03-07 21:50:51.399558', 45, true);
INSERT INTO public.flyway_schema_history VALUES (25, '090', 'indexes', 'SQL', 'V090__indexes.sql', -292980596, 'postgres', '2026-03-07 21:50:51.452804', 45, true);
INSERT INTO public.flyway_schema_history VALUES (26, '095', 'seed feature flags', 'SQL', 'V095__seed_feature_flags.sql', 459012115, 'postgres', '2026-03-07 21:50:51.50252', 5, true);
INSERT INTO public.flyway_schema_history VALUES (27, '096', 'add missing columns', 'SQL', 'V096__add_missing_columns.sql', -77707459, 'postgres', '2026-03-07 21:50:51.513778', 2, true);
INSERT INTO public.flyway_schema_history VALUES (28, '097', 'claim lines missing columns', 'SQL', 'V097__claim_lines_missing_columns.sql', 133045648, 'postgres', '2026-03-07 21:50:51.520022', 2, true);
INSERT INTO public.flyway_schema_history VALUES (29, '098', 'employer financial contract fields', 'SQL', 'V098__employer_financial_contract_fields.sql', -1812757281, 'postgres', '2026-03-07 21:50:51.530697', 2, true);


--
-- Data for Name: icd_codes; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: legacy_provider_contracts; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: medical_reviewer_providers; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: medical_service_categories; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: medical_specialties; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: member_attributes; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: member_deductibles; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: member_import_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: member_import_errors; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: member_policy_assignments; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: module_access; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: network_providers; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: password_reset_tokens; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: pdf_company_settings; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: pre_authorization_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: pre_authorization_audit; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: pre_authorizations; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_admin_documents; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_allowed_employers; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_contracts; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_contract_pricing_items; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_raw_services; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_mapping_audit; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: settlement_batches; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_payments; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_service_mappings; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_service_price_import_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: provider_services; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: settlement_batch_items; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: system_settings; Type: TABLE DATA; Schema: public; Owner: postgres
--

INSERT INTO public.system_settings VALUES (1, 'LOGO_URL', '', 'STRING', 'رابط شعار النظام. اتركه فارغاً للشعار الافتراضي.', 'UI', true, '', NULL, true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (2, 'FONT_FAMILY', 'Tajawal', 'STRING', 'نوع الخط الأساسي للنظام.', 'UI', true, 'Tajawal', 'allowed:Tajawal,Cairo,Almarai,Noto Naskh Arabic', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (3, 'FONT_SIZE_BASE', '14', 'INTEGER', 'حجم الخط الأساسي بالبكسل.', 'UI', true, '14', 'min:12,max:18', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (4, 'SYSTEM_NAME_AR', 'نظام واعد الطبي', 'STRING', 'اسم النظام باللغة العربية — يظهر في العنوان والتقارير.', 'UI', true, 'نظام واعد الطبي', 'maxlength:60', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (5, 'SYSTEM_NAME_EN', 'TBA WAAD System', 'STRING', 'System name in English — appears in reports and API responses.', 'UI', true, 'TBA WAAD System', 'maxlength:60', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (6, 'BENEFICIARY_NUMBER_FORMAT', 'PREFIX_SEQUENCE', 'STRING', 'صيغة ترقيم المستفيدين: PREFIX_SEQUENCE | YEAR_SEQUENCE | SEQUENTIAL.', 'MEMBERS', true, 'PREFIX_SEQUENCE', 'allowed:PREFIX_SEQUENCE,YEAR_SEQUENCE,SEQUENTIAL', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (7, 'BENEFICIARY_NUMBER_PREFIX', 'MEM', 'STRING', 'البادئة في رقم المستفيد (مع PREFIX_SEQUENCE).', 'MEMBERS', true, 'MEM', 'maxlength:10', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (8, 'BENEFICIARY_NUMBER_DIGITS', '6', 'INTEGER', 'عدد أرقام الجزء التسلسلي في رقم المستفيد.', 'MEMBERS', true, '6', 'min:4,max:10', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (9, 'ELIGIBILITY_STRICT_MODE', 'false', 'BOOLEAN', 'الوضع الصارم: رفض تلقائي لأي طلب خارج نطاق التغطية.', 'ELIGIBILITY', true, 'false', NULL, true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (10, 'WAITING_PERIOD_DAYS_DEFAULT', '30', 'INTEGER', 'فترة الانتظار الافتراضية بالأيام عند إضافة مستفيد لوثيقة.', 'ELIGIBILITY', true, '30', 'min:0,max:365', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);
INSERT INTO public.system_settings VALUES (11, 'ELIGIBILITY_GRACE_PERIOD_DAYS', '7', 'INTEGER', 'فترة السماح بالأيام بعد انتهاء صلاحية الوثيقة.', 'ELIGIBILITY', true, '7', 'min:0,max:30', true, '2026-03-07 21:50:51.505907', '2026-03-07 21:50:51.505907', NULL);


--
-- Data for Name: user_audit_log; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: user_login_attempts; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: visits; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Data for Name: visit_attachments; Type: TABLE DATA; Schema: public; Owner: postgres
--



--
-- Name: account_transactions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.account_transactions_id_seq', 1, false);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.audit_logs_id_seq', 1, false);


--
-- Name: benefit_policy_rules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.benefit_policy_rules_id_seq', 1, false);


--
-- Name: benefit_policy_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.benefit_policy_seq', 1, false);


--
-- Name: claim_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_attachments_id_seq', 1, false);


--
-- Name: claim_audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_audit_logs_id_seq', 1, false);


--
-- Name: claim_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_history_id_seq', 1, false);


--
-- Name: claim_lines_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_lines_id_seq', 1, false);


--
-- Name: claim_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.claim_seq', 1, false);


--
-- Name: cpt_codes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.cpt_codes_id_seq', 1, false);


--
-- Name: eligibility_checks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.eligibility_checks_id_seq', 1, false);


--
-- Name: email_verification_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.email_verification_tokens_id_seq', 1, false);


--
-- Name: employer_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employer_seq', 1, false);


--
-- Name: ent_service_alias_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ent_service_alias_seq', 1, false);


--
-- Name: feature_flags_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.feature_flags_id_seq', 3, true);


--
-- Name: icd_codes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.icd_codes_id_seq', 1, false);


--
-- Name: legacy_provider_contracts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.legacy_provider_contracts_id_seq', 1, false);


--
-- Name: medical_category_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_category_seq', 1, false);


--
-- Name: medical_reviewer_providers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_reviewer_providers_id_seq', 1, false);


--
-- Name: medical_service_category_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_service_category_seq', 1, false);


--
-- Name: medical_service_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_service_seq', 1, false);


--
-- Name: medical_specialties_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_specialties_id_seq', 1, false);


--
-- Name: member_attributes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_attributes_id_seq', 1, false);


--
-- Name: member_deductibles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_deductibles_id_seq', 1, false);


--
-- Name: member_import_errors_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_import_errors_id_seq', 1, false);


--
-- Name: member_import_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_import_logs_id_seq', 1, false);


--
-- Name: member_policy_assignments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_policy_assignments_id_seq', 1, false);


--
-- Name: member_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.member_seq', 1, false);


--
-- Name: module_access_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.module_access_id_seq', 1, false);


--
-- Name: network_providers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.network_providers_id_seq', 1, false);


--
-- Name: password_reset_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.password_reset_tokens_id_seq', 1, false);


--
-- Name: pdf_company_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pdf_company_settings_id_seq', 1, false);


--
-- Name: pre_authorization_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorization_attachments_id_seq', 1, false);


--
-- Name: pre_authorization_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorization_audit_id_seq', 1, false);


--
-- Name: pre_authorizations_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.pre_authorizations_id_seq', 1, false);


--
-- Name: preauth_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.preauth_seq', 1, false);


--
-- Name: preauthorization_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.preauthorization_requests_id_seq', 1, false);


--
-- Name: provider_accounts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_accounts_id_seq', 1, false);


--
-- Name: provider_admin_documents_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_admin_documents_id_seq', 1, false);


--
-- Name: provider_allowed_employers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_allowed_employers_id_seq', 1, false);


--
-- Name: provider_contract_pricing_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_contract_pricing_items_id_seq', 1, false);


--
-- Name: provider_contract_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_contract_seq', 1, false);


--
-- Name: provider_mapping_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_mapping_audit_id_seq', 1, false);


--
-- Name: provider_payments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_payments_id_seq', 1, false);


--
-- Name: provider_raw_services_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_raw_services_id_seq', 1, false);


--
-- Name: provider_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_seq', 1, false);


--
-- Name: provider_service_mappings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_service_mappings_id_seq', 1, false);


--
-- Name: provider_service_price_import_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_service_price_import_logs_id_seq', 1, false);


--
-- Name: provider_services_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.provider_services_id_seq', 1, false);


--
-- Name: settlement_batch_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_batch_items_id_seq', 1, false);


--
-- Name: settlement_batch_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_batch_seq', 1, false);


--
-- Name: settlement_payment_reference_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settlement_payment_reference_seq', 10001, false);


--
-- Name: system_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.system_settings_id_seq', 11, true);


--
-- Name: user_audit_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_audit_log_id_seq', 1, false);


--
-- Name: user_login_attempts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_login_attempts_id_seq', 1, false);


--
-- Name: user_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.user_seq', 1, false);


--
-- Name: visit_attachments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.visit_attachments_id_seq', 1, false);


--
-- Name: visits_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.visits_id_seq', 1, false);


--
-- PostgreSQL database dump complete
--

\unrestrict rJwKtMrnRaNB7xTkD5H7WANojUNnjmNtqs7mdbtLu8oKCAlolxnfds7FrVtHQww

