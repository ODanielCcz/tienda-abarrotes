\set ON_ERROR_STOP on

-- Performance-only bootstrap. shared_preload_libraries is configured by the
-- tienda-perf Compose overlay; this command merely exposes its statistics view.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
