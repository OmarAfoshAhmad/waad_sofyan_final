-- The provider-account ledger is permanent financial evidence. Corrections are
-- new compensating entries; no application path may rewrite or delete history.
CREATE OR REPLACE FUNCTION prevent_account_transaction_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'account_transactions is append-only; % is not allowed on row %',
        TG_OP, OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_transactions_no_update
    BEFORE UPDATE ON account_transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_account_transaction_mutation();

CREATE TRIGGER trg_account_transactions_no_delete
    BEFORE DELETE ON account_transactions
    FOR EACH ROW EXECUTE FUNCTION prevent_account_transaction_mutation();
