INSERT INTO permissions (id, code, description) VALUES
    (1, 'LEAD_VIEW_OWN', 'View leads assigned to the current user'),
    (2, 'LEAD_VIEW_TEAM', 'View leads assigned to the current user''s teams'),
    (3, 'LEAD_VIEW_COMPANY', 'View every lead in the tenant'),
    (4, 'LEAD_UPDATE', 'Update lead status, notes, and call outcomes'),
    (5, 'LEAD_ASSIGN', 'Assign and reassign leads'),
    (6, 'LEAD_EXPORT', 'Export lead data'),
    (7, 'TASK_MANAGE', 'Create and complete follow-up tasks'),
    (8, 'META_MANAGE', 'Connect Meta and select pages or ad accounts'),
    (9, 'CAMPAIGN_VIEW', 'View cached campaigns and insights'),
    (10, 'USER_MANAGE', 'Invite and disable users'),
    (11, 'TEAM_MANAGE', 'Create teams and memberships'),
    (12, 'BILLING_VIEW', 'View the plan, features, and usage'),
    (13, 'AUDIT_VIEW', 'View the tenant audit log'),
    (14, 'REPORT_VIEW', 'View performance reports'),
    (15, 'SUPPORT_VIEW', 'Create and view own-company support tickets'),
    (16, 'SUPPORT_MANAGE', 'View diagnostics and close tickets'),
    (17, 'PLATFORM_TENANT_MANAGE', 'CodeBuzzers platform staff only');

INSERT INTO roles (id, tenant_id, name, system_role, created_at, updated_at) VALUES
    (1, NULL, 'OWNER', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2, NULL, 'MANAGER', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3, NULL, 'EMPLOYEE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (4, NULL, 'VIEWER', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (5, NULL, 'PLATFORM', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions WHERE code <> 'PLATFORM_TENANT_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT 2, id FROM permissions WHERE code IN
    ('LEAD_VIEW_TEAM', 'LEAD_UPDATE', 'LEAD_ASSIGN', 'TASK_MANAGE', 'CAMPAIGN_VIEW', 'TEAM_MANAGE', 'REPORT_VIEW', 'SUPPORT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 3, id FROM permissions WHERE code IN
    ('LEAD_VIEW_OWN', 'LEAD_UPDATE', 'TASK_MANAGE', 'SUPPORT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, id FROM permissions WHERE code IN
    ('LEAD_VIEW_COMPANY', 'CAMPAIGN_VIEW', 'BILLING_VIEW', 'REPORT_VIEW', 'SUPPORT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 5, id FROM permissions WHERE code = 'PLATFORM_TENANT_MANAGE';

INSERT INTO plans (id, code, name, description, max_users, max_ad_accounts, max_leads_per_month, active) VALUES
    (1, 'BASIC', 'Basic', 'Pilot plan for a single team', 5, 1, 500, 1),
    (2, 'PRO', 'Pro', 'Growing sales teams', 25, 5, 5000, 1),
    (3, 'ENTERPRISE', 'Enterprise', 'Higher limits for larger companies', 200, 50, 100000, 1);

INSERT INTO plan_features (plan_id, feature_code, enabled)
SELECT p.id, f.code, 1
FROM plans p
JOIN (
    SELECT 'lead_ingestion' AS code
    UNION ALL SELECT 'round_robin'
    UNION ALL SELECT 'tasks'
    UNION ALL SELECT 'performance'
    UNION ALL SELECT 'campaign_analytics'
    UNION ALL SELECT 'csv_export'
) f;

ALTER TABLE permissions AUTO_INCREMENT = 100;
ALTER TABLE roles AUTO_INCREMENT = 100;
ALTER TABLE plans AUTO_INCREMENT = 100;
