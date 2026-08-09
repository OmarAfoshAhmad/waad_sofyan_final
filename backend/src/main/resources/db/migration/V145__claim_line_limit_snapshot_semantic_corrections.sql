-- ============================================================
-- finance-02.3: correct claim_line_limit_snapshots semantics BEFORE any
-- resolver code depends on it. V144 is already applied/committed, so this
-- fixes it forward rather than editing it in place.
--
-- Three real defects caught in review:
--
-- 1. binding_available_before was named as if only the binding row carries
--    "available before". Every row represents an independent limit and must
--    carry ITS OWN available-before value, regardless of whether it binds.
--    Renamed to available_before; the arithmetic identity is unchanged in
--    shape (available_after = GREATEST(0, available_before - limit_consumption)),
--    just against the corrected column name.
--
-- 2. is_binding can legitimately be true on more than one row for the same
--    line, when two or more limits tie at the same available amount. The
--    column itself needs no schema change -- only its old "exactly one"
--    comment/assumption, corrected here and in the entity.
--
-- 3. PREAUTH_RESERVATION is not a source of an effective limit VALUE -- a
--    reservation reduces availability (available = effectiveLimit -
--    consumed - reserved), it does not set the limit itself. Removed from
--    source_type's allowed values. Reservation bookkeeping gets its own
--    reference (reserved_before already exists) when the reservation model
--    is built; no placeholder column added now since nothing writes it yet.
-- ============================================================

ALTER TABLE claim_line_limit_snapshots
    RENAME COLUMN binding_available_before TO available_before;

ALTER TABLE claim_line_limit_snapshots
    DROP CONSTRAINT chk_limit_snapshot_available_after;

ALTER TABLE claim_line_limit_snapshots
    ADD CONSTRAINT chk_limit_snapshot_available_after CHECK (
        available_after = GREATEST(0, available_before - limit_consumption)
    );

ALTER TABLE claim_line_limit_snapshots
    DROP CONSTRAINT chk_limit_snapshot_source_type;

ALTER TABLE claim_line_limit_snapshots
    ADD CONSTRAINT chk_limit_snapshot_source_type CHECK (source_type IN
        ('POLICY_DEFAULT', 'EMPLOYER_OVERRIDE', 'MEMBER_OVERRIDE'));

COMMENT ON COLUMN claim_line_limit_snapshots.available_before IS
    'This limit''s own available amount before this line''s consumption -- carried '
    'on every applicable-limit row, not only the binding one. Do not confuse with '
    'ClaimLine.binding_available_limit, which is the single effective ceiling that '
    'actually governed the line''s result.';

COMMENT ON COLUMN claim_line_limit_snapshots.is_binding IS
    'True for every row (across this line''s applicable-limit rows) whose '
    'available_before equals the line''s bindingAvailableLimit. Usually exactly '
    'one row, but ties are legitimate (e.g. a service bucket and its parent '
    'group bucket both at 500) and produce more than one binding row; zero rows '
    'are binding when limit_mode is UNLIMITED.';

COMMENT ON COLUMN claim_lines.limit_mode IS
    'WaadFinancialEngine.LimitMode. LIMITED rows populate binding_available_limit/'
    'limit_consumption/binding_remaining_limit (inside_limit is always populated in '
    'both modes: it equals settlement_base when UNLIMITED). UNLIMITED rows leave '
    'binding_available_limit/limit_consumption/binding_remaining_limit NULL '
    '("not applicable" -- never zero, which would mean an exhausted limit instead '
    'of no limit at all).';
