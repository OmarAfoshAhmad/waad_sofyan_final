-- An exceptional increase to one member's general ceiling.
--
-- The need is real and recurring: an employer asks for a named employee's
-- annual ceiling to be raised for a specific reason, or an insurer decides a
-- particular case warrants it. Until now the only way to do that was to edit
-- the benefit policy's annual limit, which raises the ceiling of every member
-- on that policy, or to move the member to a different policy, which rewrites
-- their whole cover to change one number.
--
-- Three properties this table exists to guarantee.
--
-- It is DATED, like every other fact about a member in this system. A ceiling
-- is read as of a date, so an uplift has a validity window and a claim from
-- last month resolves against the ceiling that applied last month. Revoking
-- one closes its window; it never deletes the row.
--
-- It is ADDITIVE and stays visible as such. The uplift is never folded into
-- the policy's annual limit. Every screen that shows a raised ceiling can
-- show what it was and what was added, because a number nobody can decompose
-- is a number nobody can check.
--
-- It CARRIES ITS REASON. Not as a nice-to-have: the whole act is an exception
-- to a rule, and an exception with no recorded reason is indistinguishable
-- from a mistake six months later.

CREATE TABLE IF NOT EXISTS member_general_limit_uplifts (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT      NOT NULL REFERENCES members (id),

    -- Always an increase. A reduction is a different act with a different
    -- risk -- it can invalidate cover a member has already relied on -- and
    -- is deliberately not expressible here.
    amount              NUMERIC(15, 2) NOT NULL CHECK (amount > 0),

    -- [effective_from, effective_to). Half-open, like every other period in
    -- this schema, so a window that ends on the day another begins does not
    -- overlap it by one day.
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    -- effective_to = effective_from is an EMPTY window, and deliberately
    -- allowed: it is how an uplift entered by mistake and cancelled the same
    -- day is recorded. The row stays with its reason and both usernames, and
    -- no date ever falls inside it, so it raised nobody's ceiling for a
    -- moment. Deleting the row instead would leave no trace that anyone had
    -- tried.
    CONSTRAINT chk_uplift_period CHECK (effective_to IS NULL OR effective_to >= effective_from),

    -- Where the exception came from. EMPLOYER_REQUEST names the employer that
    -- asked; SPECIAL_CONSIDERATION is the insurer's own decision and has no
    -- requesting employer.
    source              VARCHAR(32) NOT NULL
        CHECK (source IN ('EMPLOYER_REQUEST', 'SPECIAL_CONSIDERATION')),
    requested_by_employer_id BIGINT REFERENCES employers (id),
    CONSTRAINT chk_uplift_source_employer CHECK (
        (source = 'EMPLOYER_REQUEST' AND requested_by_employer_id IS NOT NULL)
        OR (source = 'SPECIAL_CONSIDERATION' AND requested_by_employer_id IS NULL)
    ),

    reason              TEXT        NOT NULL CHECK (btrim(reason) <> ''),

    granted_by_user_id  BIGINT,
    granted_by_username VARCHAR(100),
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Set together when an uplift is ended before its own end date. The row
    -- stays; only its window closes.
    revoked_at          TIMESTAMP,
    revoked_by_user_id  BIGINT,
    revoked_by_username VARCHAR(100),
    revoked_reason      TEXT,
    -- The IS NOT NULL is load-bearing, not belt and braces. Without it, a row
    -- with revoked_at set and revoked_reason null evaluates the second branch
    -- as (true AND NULL) = NULL, the whole CHECK as (false OR NULL) = NULL,
    -- and Postgres admits a NULL check. The constraint would have looked
    -- correct and refused nothing.
    CONSTRAINT chk_uplift_revocation CHECK (
        (revoked_at IS NULL AND revoked_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL AND btrim(revoked_reason) <> '')
    )
);

-- The read this table exists for: every uplift in force for these members on
-- this date, asked once per page rather than once per member.
CREATE INDEX IF NOT EXISTS idx_uplift_member_period
    ON member_general_limit_uplifts (member_id, effective_from, effective_to);

COMMENT ON TABLE member_general_limit_uplifts IS
    'استثناءات رفع السقف العام للمستفيد: مبلغ إضافي مؤرخ بسبب موثق، يُقرأ بتاريخ الخدمة ولا يُدمج في سقف الوثيقة';

-- The permission. Managing an exception to the ceiling is not the same act as
-- reading one, and neither MEMBER_LIMIT_VIEW nor MEMBER_EDIT_IDENTITY should
-- carry it: the first is what a provider holds to treat a patient, and the
-- second is for correcting a name.
INSERT INTO rbac_permissions (code, category, display_name_ar, sensitive)
VALUES ('MEMBER_LIMIT_UPLIFT_MANAGE', 'MEMBERS',
        'منح ورفع استثناءات السقف العام للمستفيد', true)
ON CONFLICT (code) DO NOTHING;

-- SUPER_ADMIN only by default. Raising one member's ceiling commits the
-- insurer's money, so it starts where the fewest people are and is handed out
-- one user at a time through rbac_user_permission_overrides -- which is
-- exactly the case a per-user grant exists for.
INSERT INTO rbac_role_permissions (role_code, permission_code, granted_by)
VALUES ('SUPER_ADMIN', 'MEMBER_LIMIT_UPLIFT_MANAGE', 'MIGRATION_V199')
ON CONFLICT (role_code, permission_code) DO NOTHING;
