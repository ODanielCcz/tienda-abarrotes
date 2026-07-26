\set ON_ERROR_STOP on
\echo 'Running database tests...'
\ir 000_test_helpers.sql
\ir 001_schema_and_constraints.sql
\ir 002_transactions_and_cost.sql
\ir 003_audit_append_only.sql
\ir 004_idempotency_and_offline.sql
\echo 'All SQL tests passed.'
