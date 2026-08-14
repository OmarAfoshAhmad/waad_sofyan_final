-- ============================================================
-- V181: point the claim and attachment links at the pre-authorization table
-- the application actually uses.
--
-- Two pre-authorization tables exist. preauthorization_requests (V17) has no
-- JPA entity, no writer anywhere in the codebase, and no seed in any
-- migration. pre_authorizations is the live one: PreAuthorization maps it,
-- pre_authorization_lines is keyed to it, and PreAuthorizationService writes
-- it.
--
-- But two foreign keys still bind live tables to the dead one:
--
--   claims.pre_authorization_id                    -> preauthorization_requests
--   pre_authorization_attachments.pre_authorization_id -> preauthorization_requests
--
-- while Claim.preAuthorization is typed as PreAuthorization and
-- PreAuthorizationAttachment lives in the same module as the live entity. The
-- mapping and the constraint have disagreed since V19, and the effect is that
-- linking a claim -- or an attachment -- to a REAL pre-authorization has
-- never been possible. It went unnoticed because nothing wrote either link.
--
-- Conversion (approval -> claim) must write claims.pre_authorization_id, so
-- this is the blocker.
--
-- WHAT THIS MIGRATION DOES NOT DO
--
-- It moves no value. A production audit found every relevant count at zero,
-- so there is nothing to migrate -- and matching ids across two tables would
-- never have been evidence that two rows describe the same authorization
-- anyway.
--
-- It does not REMOVE pre_authorization_attachments.preauthorization_request_id
-- or its fk_preauth_att constraint. It only releases the column'"'"'s NOT NULL,
-- because that constraint is what makes the repaired link unreachable: every
-- attachment would otherwise still have to name a row in the empty dead
-- table. The column stays, its foreign key stays and still validates any
-- value written there. Deleting it belongs to retiring the dead model, which
-- is a separate decision from repairing a live link.
--
-- It does not drop preauthorization_requests. Fixing a link and deleting an
-- old model are different operations, and the second needs its own proof that
-- nothing depends on it.
-- ============================================================

-- ── 1. Refuse to run where the assumption does not hold ──────────────────
-- This migration is only safe because there is nothing to preserve. On any
-- environment where that is untrue, stop with a report rather than silently
-- repointing a constraint away from rows it was protecting.
DO $$
DECLARE
    legacy_rows      BIGINT;
    claim_links      BIGINT;
    attachment_links BIGINT;
    legacy_att_links BIGINT;
BEGIN
    SELECT COUNT(*) INTO legacy_rows FROM preauthorization_requests;
    SELECT COUNT(*) INTO claim_links FROM claims WHERE pre_authorization_id IS NOT NULL;
    SELECT COUNT(*) INTO attachment_links
    FROM pre_authorization_attachments WHERE pre_authorization_id IS NOT NULL;
    SELECT COUNT(*) INTO legacy_att_links
    FROM pre_authorization_attachments WHERE preauthorization_request_id IS NOT NULL;

    IF legacy_rows > 0 OR claim_links > 0 OR attachment_links > 0 OR legacy_att_links > 0 THEN
        RAISE EXCEPTION
            'V181 aborted: the legacy pre-authorization model still holds data '
            '(requests=%, claim links=%, attachment links=%). Repointing these keys '
            'would orphan those links, and matching ids across the two tables is not '
            'evidence that they describe the same authorization. Migrate them '
            'explicitly first. (legacy attachment links=%)',
            legacy_rows, claim_links, attachment_links, legacy_att_links;
    END IF;
END $$;

-- ── 2. The claim link ────────────────────────────────────────────────────
ALTER TABLE claims DROP CONSTRAINT IF EXISTS fk_claim_preauth;

ALTER TABLE claims
    ADD CONSTRAINT fk_claim_preauth
        FOREIGN KEY (pre_authorization_id)
        -- RESTRICT, matching every other financial reference in this schema: a
        -- pre-authorization that a claim was settled against is part of that
        -- claim's explanation and must not disappear from under it.
        REFERENCES pre_authorizations(id) ON DELETE RESTRICT;

-- ── 3. The attachment link ───────────────────────────────────────────────
ALTER TABLE pre_authorization_attachments
    DROP CONSTRAINT IF EXISTS fk_pre_authorization_attachments_request;

ALTER TABLE pre_authorization_attachments
    ADD CONSTRAINT fk_pre_authorization_attachments_request
        FOREIGN KEY (pre_authorization_id)
        REFERENCES pre_authorizations(id) ON DELETE RESTRICT;

-- ── 3b. Make the repaired link usable ────────────────────────────────────
-- Repointing the key is not enough on its own. preauthorization_request_id is
-- NOT NULL, so every attachment row must also name a row in the dead table --
-- which is empty and has no writer, and never will have one. The effect is
-- that attaching a file to a pre-authorization has been impossible, not
-- merely mislinked, which is why the table holds no rows at all.
--
-- Dropping the NOT NULL is what makes the corrected key reachable. It deletes
-- nothing: the column stays, fk_preauth_att stays, and a value written there
-- is still validated against the legacy table.
ALTER TABLE pre_authorization_attachments
    ALTER COLUMN preauthorization_request_id DROP NOT NULL;

COMMENT ON COLUMN pre_authorization_attachments.preauthorization_request_id IS
    'LEGACY, DEPRECATED. Belongs to the retired preauthorization_requests model, '
    'which has no entity and no writer. Nullable since V181 so the live link '
    '(pre_authorization_id -> pre_authorizations) can be used. Do not populate; '
    'removal belongs to the migration that retires the legacy model.';

-- ── 4. Prove the repair, and prove the restraint ─────────────────────────
DO $$
DECLARE
    claim_target      TEXT;
    attachment_target TEXT;
    legacy_target     TEXT;
BEGIN
    SELECT ccu.table_name INTO claim_target
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name
    WHERE tc.constraint_name = 'fk_claim_preauth';

    SELECT ccu.table_name INTO attachment_target
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name
    WHERE tc.constraint_name = 'fk_pre_authorization_attachments_request';

    -- The remnant must be untouched: this migration repairs, it does not tidy.
    SELECT ccu.table_name INTO legacy_target
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name
    WHERE tc.constraint_name = 'fk_preauth_att';

    IF claim_target IS DISTINCT FROM 'pre_authorizations' THEN
        RAISE EXCEPTION 'V181 aborted: fk_claim_preauth still points at %', claim_target;
    END IF;
    IF attachment_target IS DISTINCT FROM 'pre_authorizations' THEN
        RAISE EXCEPTION 'V181 aborted: fk_pre_authorization_attachments_request still points at %',
            attachment_target;
    END IF;
    IF legacy_target IS DISTINCT FROM 'preauthorization_requests' THEN
        RAISE EXCEPTION
            'V181 aborted: fk_preauth_att was altered. Retiring the legacy model is a '
            'separate migration with its own proof.';
    END IF;

    -- The remnant is released, not removed: still present, still constrained,
    -- simply no longer mandatory.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pre_authorization_attachments'
          AND column_name = 'preauthorization_request_id'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'V181 aborted: the legacy attachment column is still mandatory, so the '
            'repaired link remains unusable.';
    END IF;
END $$;
