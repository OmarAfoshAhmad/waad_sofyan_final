-- V69: Fix members.id auto-generation for JPA IDENTITY
--
-- Root cause:
-- members.id has no default, so inserts from Member entity fail with:
-- "null value in column \"id\" of relation \"members\""

CREATE SEQUENCE IF NOT EXISTS members_id_seq;

SELECT setval(
    'members_id_seq',
    COALESCE((SELECT MAX(id) FROM members), 0) + 1,
    false
);

ALTER TABLE members
    ALTER COLUMN id SET DEFAULT nextval('members_id_seq');
