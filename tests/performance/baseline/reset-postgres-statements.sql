\set ON_ERROR_STOP on

-- Clears performance counters only; it does not modify application data.
SELECT pg_stat_statements_reset();
