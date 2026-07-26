BEGIN;

INSERT INTO audit.business_events (event_id, event_type, aggregate_type, reason)
VALUES ('00000000-0000-0000-0000-000000000301', 'TEST_EVENT', 'TEST', 'Prueba de inmutabilidad');

DO $$
BEGIN
    BEGIN
        UPDATE audit.business_events
        SET reason = 'No debe cambiar'
        WHERE event_id = '00000000-0000-0000-0000-000000000301';
        RAISE EXCEPTION 'audit update was accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'audit records are append-only' THEN
            RAISE;
        END IF;
    END;
END;
$$;

SELECT test_support.assert_true(
    (SELECT reason = 'Prueba de inmutabilidad' FROM audit.business_events WHERE event_id = '00000000-0000-0000-0000-000000000301'),
    'audit record must remain unchanged'
);

ROLLBACK;

