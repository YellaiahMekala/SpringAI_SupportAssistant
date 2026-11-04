-- Synthetic demo data (profile "demo" only). Repeatable migration: safe to re-run, never overwrites existing rows.
-- The subjects match the user ids in docker/keycloak/support-realm.json.

INSERT INTO customers (keycloak_subject, display_name) VALUES
    ('11111111-1111-4111-8111-111111111111', 'Alice Demo'),
    ('22222222-2222-4222-8222-222222222222', 'Bob Demo')
ON CONFLICT (keycloak_subject) DO NOTHING;

INSERT INTO orders (customer_id, order_ref, status, promised_date)
SELECT c.id, v.order_ref, v.status, v.promised_date::date
FROM (VALUES
    ('11111111-1111-4111-8111-111111111111', 'ORD-1001', 'DELAYED', '2026-11-10'),
    ('11111111-1111-4111-8111-111111111111', 'ORD-1002', 'SHIPPED', '2026-11-08'),
    ('22222222-2222-4222-8222-222222222222', 'ORD-2001', 'PLACED',  '2026-11-12')
) AS v(subject, order_ref, status, promised_date)
JOIN customers c ON c.keycloak_subject = v.subject
ON CONFLICT (order_ref) DO NOTHING;

INSERT INTO order_events (order_id, event_type, occurred_at)
SELECT o.id, v.event_type, v.occurred_at::timestamptz
FROM (VALUES
    ('ORD-1001', 'PLACED',        '2026-11-02T10:00:00Z'),
    ('ORD-1001', 'SHIPPED',       '2026-11-04T09:30:00Z'),
    ('ORD-1001', 'DELAYED',       '2026-11-07T14:15:00Z'),
    ('ORD-1002', 'PLACED',        '2026-11-03T08:00:00Z'),
    ('ORD-1002', 'SHIPPED',       '2026-11-05T12:00:00Z'),
    ('ORD-2001', 'PLACED',        '2026-11-06T16:45:00Z')
) AS v(order_ref, event_type, occurred_at)
JOIN orders o ON o.order_ref = v.order_ref
WHERE NOT EXISTS (
    SELECT 1 FROM order_events e WHERE e.order_id = o.id AND e.event_type = v.event_type
);
