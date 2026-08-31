\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

WITH database_stats AS (
    SELECT
        numbackends,
        xact_commit,
        xact_rollback,
        blks_read,
        blks_hit,
        temp_files,
        temp_bytes,
        deadlocks
    FROM pg_stat_database
    WHERE datname = current_database()
), activity_stats AS (
    SELECT
        count(*) FILTER (WHERE state = 'active') AS active_sessions,
        count(*) FILTER (WHERE wait_event IS NOT NULL) AS waiting_sessions,
        count(*) AS total_sessions
    FROM pg_stat_activity
    WHERE datname = current_database()
), lock_stats AS (
    SELECT
        count(*) FILTER (WHERE granted) AS granted_locks,
        count(*) FILTER (WHERE NOT granted) AS waiting_locks
    FROM pg_locks
    WHERE database = (SELECT oid FROM pg_database WHERE datname = current_database())
), top_statements AS (
    SELECT COALESCE(jsonb_agg(to_jsonb(statement_row)), '[]'::jsonb) AS rows
    FROM (
        SELECT
            queryid::text AS query_id,
            calls,
            round(total_exec_time::numeric, 3) AS total_exec_time_ms,
            round(mean_exec_time::numeric, 3) AS mean_exec_time_ms,
            rows,
            shared_blks_hit,
            shared_blks_read,
            temp_blks_read,
            temp_blks_written
        FROM pg_stat_statements
        WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
        ORDER BY total_exec_time DESC
        LIMIT 20
    ) AS statement_row
)
SELECT jsonb_build_object(
    'capturedAt', clock_timestamp(),
    'databaseName', current_database(),
    'databaseUser', current_user,
    'database', (SELECT to_jsonb(database_stats) FROM database_stats),
    'activity', (SELECT to_jsonb(activity_stats) FROM activity_stats),
    'locks', (SELECT to_jsonb(lock_stats) FROM lock_stats),
    'topStatements', (SELECT rows FROM top_statements)
)::text;
