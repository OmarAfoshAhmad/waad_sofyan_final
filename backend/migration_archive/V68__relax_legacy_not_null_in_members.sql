-- V68: Relax legacy NOT NULL constraints in members table that block unified member inserts
--
-- Root cause:
-- Unified Member entity writes modern columns (card_number, birth_date) and does not populate
-- legacy columns (member_card_id, date_of_birth). Legacy NOT NULL constraints on those columns
-- trigger 500 errors on POST /unified-members.

ALTER TABLE members
    ALTER COLUMN member_card_id DROP NOT NULL;

ALTER TABLE members
    ALTER COLUMN date_of_birth DROP NOT NULL;
