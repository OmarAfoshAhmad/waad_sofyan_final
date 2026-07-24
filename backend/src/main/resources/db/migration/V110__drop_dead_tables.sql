-- ============================================================
-- V110: Drop confirmed-dead tables
-- ============================================================
-- Each table below has zero JPA @Entity mapping, zero repository,
-- zero service/controller reference anywhere in the codebase, and
-- zero rows in the live database. None is touched by any migration
-- after the one that created it (verified via full-repo grep before
-- this migration was written). The database is experimental, so no
-- data migration/backup step is required.
--
-- network_providers          — created V12__provider_contracts.sql, never mapped to an Entity
-- claim_history               — created V21__claim_sub_tables.sql, never mapped to an Entity
-- coverage_simulation_items   — created V69__add_simulation_and_exclusions.sql, never mapped; child of coverage_simulation_runs
-- coverage_simulation_runs    — created V69__add_simulation_and_exclusions.sql, never mapped
-- medical_semantic_rules      — created V70__create_medical_semantic_tables.sql, never mapped
-- medical_synonyms            — created V70__create_medical_semantic_tables.sql, never mapped
--
-- NOT dropped: legacy_provider_contracts — this one IS live, mapped by
-- com.waad.tba.modules.provider.entity.ProviderContract. An earlier
-- automated pass incorrectly flagged it as dead; verified otherwise
-- before writing this migration.

-- Child before parent (FK: coverage_simulation_items -> coverage_simulation_runs)
DROP TABLE IF EXISTS coverage_simulation_items;
DROP TABLE IF EXISTS coverage_simulation_runs;

DROP TABLE IF EXISTS network_providers;
DROP TABLE IF EXISTS claim_history;
DROP TABLE IF EXISTS medical_semantic_rules;
DROP TABLE IF EXISTS medical_synonyms;
