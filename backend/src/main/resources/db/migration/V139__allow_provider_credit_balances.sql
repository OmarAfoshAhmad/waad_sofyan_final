-- A real provider transfer may exceed today's approved liability. The difference
-- is a company credit with the provider and must remain visible as a negative
-- running_balance. V22's non-negative constraint silently contradicted the
-- accounting invariant running_balance = total_approved - total_paid.
--
-- No data rewrite is needed: existing balances are non-negative. All monetary
-- movements still pass through the locked account and its formula invariant.
ALTER TABLE provider_accounts
    DROP CONSTRAINT IF EXISTS chk_balance_non_negative;

-- The immutable ledger must record the same signed balance after the transfer;
-- otherwise the account could allow a credit while its audit entry could not.
ALTER TABLE account_transactions
    DROP CONSTRAINT IF EXISTS chk_transaction_balance_non_negative;
