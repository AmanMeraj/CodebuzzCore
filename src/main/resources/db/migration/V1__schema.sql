CREATE TABLE tenants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(32) NOT NULL,
    country_code CHAR(2) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    default_team_id BIGINT NULL,
    assignment_timeout_minutes INT NOT NULL DEFAULT 15,
    lead_retention_days INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenants_public_id (public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    timezone VARCHAR(64) NULL,
    locale VARCHAR(10) NULL,
    on_duty TINYINT(1) NOT NULL DEFAULT 1,
    platform_staff TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_public_id (public_id),
    UNIQUE KEY uk_users_email (email),
    KEY ix_users_tenant_status (tenant_id, status),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    system_role TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_roles_scope_name ON roles ((IFNULL(tenant_id, 0)), name);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    manager_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_teams_public_id (public_id),
    KEY ix_teams_tenant_status (tenant_id, status),
    CONSTRAINT fk_teams_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_teams_manager FOREIGN KEY (manager_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_team_members_team_user (team_id, user_id),
    KEY ix_team_members_tenant_user (tenant_id, user_id),
    CONSTRAINT fk_tm_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_id BIGINT NULL,
    ip_hash CHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY ix_refresh_user (user_id, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reset_hash (token_hash),
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255) NOT NULL,
    max_users INT NOT NULL,
    max_ad_accounts INT NOT NULL,
    max_leads_per_month INT NOT NULL,
    active TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plans_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE plan_features (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    feature_code VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_feature (plan_id, feature_code),
    CONSTRAINT fk_pf_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_period_start DATE NOT NULL,
    current_period_end DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_tenant (tenant_id),
    CONSTRAINT fk_sub_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_limits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    max_users INT NOT NULL,
    max_ad_accounts INT NOT NULL,
    max_leads_per_month INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_limits_tenant (tenant_id),
    CONSTRAINT fk_limits_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usage_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    metric VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_period (tenant_id, period_start, metric),
    CONSTRAINT fk_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    meta_user_id VARCHAR(64) NULL,
    meta_user_name VARCHAR(255) NULL,
    encrypted_access_token TEXT NULL,
    token_expires_at DATETIME(6) NULL,
    granted_scopes VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL,
    health_message VARCHAR(255) NULL,
    connected_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    disconnected_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_conn_public (public_id),
    KEY ix_meta_conn_tenant_status (tenant_id, status),
    KEY ix_meta_conn_tenant_user (tenant_id, meta_user_id),
    CONSTRAINT fk_meta_conn_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_meta_conn_user FOREIGN KEY (connected_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_pages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    meta_page_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    encrypted_page_token TEXT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_pages_tenant_page (tenant_id, meta_page_id),
    KEY ix_meta_pages_tenant_status (tenant_id, status),
    CONSTRAINT fk_pages_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_pages_conn FOREIGN KEY (connection_id) REFERENCES meta_connections (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_ad_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    meta_ad_account_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency_code CHAR(3) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_ads_tenant_account (tenant_id, meta_ad_account_id),
    KEY ix_meta_ads_tenant_status (tenant_id, status),
    CONSTRAINT fk_adacct_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_adacct_conn FOREIGN KEY (connection_id) REFERENCES meta_connections (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_forms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    meta_form_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_forms_tenant_form (tenant_id, meta_form_id),
    CONSTRAINT fk_forms_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_forms_page FOREIGN KEY (page_id) REFERENCES meta_pages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_form_fields (
    id BIGINT NOT NULL AUTO_INCREMENT,
    form_id BIGINT NOT NULL,
    meta_field_key VARCHAR(128) NOT NULL,
    label VARCHAR(255) NOT NULL,
    field_type VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_field (form_id, meta_field_key),
    CONSTRAINT fk_fields_form FOREIGN KEY (form_id) REFERENCES meta_forms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_field_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    form_id BIGINT NOT NULL,
    source_field VARCHAR(128) NOT NULL,
    target_field VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_mapping (form_id, source_field),
    CONSTRAINT fk_map_form FOREIGN KEY (form_id) REFERENCES meta_forms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ad_account_id BIGINT NOT NULL,
    meta_campaign_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(64) NULL,
    objective VARCHAR(64) NULL,
    start_time DATETIME(6) NULL,
    end_time DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_campaign_tenant_meta (tenant_id, meta_campaign_id),
    CONSTRAINT fk_camp_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_camp_account FOREIGN KEY (ad_account_id) REFERENCES meta_ad_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_ad_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    meta_adset_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_adset_tenant_meta (tenant_id, meta_adset_id),
    CONSTRAINT fk_adset_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_adset_camp FOREIGN KEY (campaign_id) REFERENCES meta_campaigns (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meta_ads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ad_set_id BIGINT NOT NULL,
    meta_ad_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ad_tenant_meta (tenant_id, meta_ad_id),
    CONSTRAINT fk_ad_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_ad_adset FOREIGN KEY (ad_set_id) REFERENCES meta_ad_sets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campaign_insights (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    meta_object_id VARCHAR(64) NOT NULL,
    insight_date DATE NOT NULL,
    spend DECIMAL(18,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    impressions BIGINT NOT NULL,
    clicks BIGINT NOT NULL,
    leads BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_insight (tenant_id, object_type, meta_object_id, insight_date),
    KEY ix_insight_tenant_date (tenant_id, insight_date),
    CONSTRAINT fk_insight_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE loss_reasons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    active TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loss_public (public_id),
    KEY ix_loss_tenant (tenant_id, active),
    CONSTRAINT fk_loss_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE leads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    meta_lead_id VARCHAR(100) NOT NULL,
    meta_page_id VARCHAR(100) NULL,
    meta_form_id VARCHAR(100) NULL,
    meta_ad_account_id VARCHAR(100) NULL,
    meta_campaign_id VARCHAR(100) NULL,
    meta_adset_id VARCHAR(100) NULL,
    meta_ad_id VARCHAR(100) NULL,
    name VARCHAR(255) NULL,
    phone_normalized VARCHAR(32) NULL,
    email_normalized VARCHAR(320) NULL,
    status VARCHAR(32) NOT NULL,
    assigned_user_id BIGINT NULL,
    assigned_team_id BIGINT NULL,
    first_response_at DATETIME(6) NULL,
    last_contact_at DATETIME(6) NULL,
    is_test TINYINT(1) NOT NULL DEFAULT 0,
    is_spam TINYINT(1) NOT NULL DEFAULT 0,
    over_quota TINYINT(1) NOT NULL DEFAULT 0,
    raw_payload JSON NULL,
    meta_created_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leads_public (public_id),
    UNIQUE KEY uk_leads_tenant_meta (tenant_id, meta_lead_id),
    KEY ix_leads_tenant_created (tenant_id, created_at),
    KEY ix_leads_tenant_status_created (tenant_id, status, created_at),
    KEY ix_leads_tenant_assignee_status (tenant_id, assigned_user_id, status),
    KEY ix_leads_tenant_phone (tenant_id, phone_normalized),
    CONSTRAINT fk_leads_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_leads_user FOREIGN KEY (assigned_user_id) REFERENCES users (id),
    CONSTRAINT fk_leads_team FOREIGN KEY (assigned_team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    last_assigned_user_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_state (tenant_id, team_id),
    CONSTRAINT fk_as_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_as_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    from_user_id BIGINT NULL,
    to_user_id BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    attempt_no INT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    response_deadline DATETIME(6) NULL,
    responded_at DATETIME(6) NULL,
    timed_out TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_assign_lead (tenant_id, lead_id, assigned_at),
    KEY ix_assign_user (tenant_id, to_user_id, assigned_at),
    KEY ix_assign_deadline (response_deadline, responded_at, timed_out),
    CONSTRAINT fk_la_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_la_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_la_to FOREIGN KEY (to_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_activities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    metadata_json JSON NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_activity_lead (tenant_id, lead_id, occurred_at),
    KEY ix_activity_user (tenant_id, user_id, occurred_at),
    CONSTRAINT fk_act_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_act_lead FOREIGN KEY (lead_id) REFERENCES leads (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_notes_lead (tenant_id, lead_id, created_at),
    CONSTRAINT fk_notes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_notes_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    assigned_user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tasks_public (public_id),
    KEY ix_tasks_assignee (tenant_id, assigned_user_id, status, due_at),
    CONSTRAINT fk_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tasks_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (assigned_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_status_lead (tenant_id, lead_id, changed_at),
    CONSTRAINT fk_lsh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_lsh_lead FOREIGN KEY (lead_id) REFERENCES leads (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_sales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    won_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_lead (lead_id),
    CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_sales_lead FOREIGN KEY (lead_id) REFERENCES leads (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lead_losses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    reason_id BIGINT NOT NULL,
    notes VARCHAR(500) NULL,
    lost_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loss_lead (lead_id),
    CONSTRAINT fk_ll_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_ll_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_ll_reason FOREIGN KEY (reason_id) REFERENCES loss_reasons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    provider VARCHAR(32) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    received_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_event (provider, event_key),
    KEY ix_webhook_status (status, received_at),
    CONSTRAINT fk_wh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    webhook_event_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    attempted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_wh_attempt (webhook_event_id, attempt_no),
    CONSTRAINT fk_wha_event FOREIGN KEY (webhook_event_id) REFERENCES webhook_events (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE background_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    job_type VARCHAR(64) NOT NULL,
    job_key VARCHAR(160) NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    available_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    locked_at DATETIME(6) NULL,
    locked_by VARCHAR(64) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_key (job_key),
    KEY ix_jobs_ready (status, available_at),
    CONSTRAINT fk_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    error VARCHAR(500) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_job_attempts (job_id, attempt_no),
    CONSTRAINT fk_ja_job FOREIGN KEY (job_id) REFERENCES background_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idempotency_keys (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    response_json JSON NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem (tenant_id, idempotency_key),
    CONSTRAINT fk_idem_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    dedupe_key VARCHAR(160) NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_dedupe (dedupe_key),
    KEY ix_outbox_ready (status, available_at),
    CONSTRAINT fk_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    deep_link VARCHAR(255) NULL,
    dedupe_key VARCHAR(160) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedupe (dedupe_key),
    KEY ix_notifications_user (tenant_id, user_id, created_at),
    CONSTRAINT fk_notif_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    push_enabled TINYINT(1) NOT NULL DEFAULT 1,
    lead_assigned TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pref_user (user_id),
    CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_token (token),
    KEY ix_devices_user (user_id, status),
    CONSTRAINT fk_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NULL,
    actor_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    source VARCHAR(32) NOT NULL,
    ip_hash CHAR(64) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_tenant_created (tenant_id, created_at),
    KEY ix_audit_entity (tenant_id, entity_type, entity_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE support_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    subject VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_public (public_id),
    KEY ix_ticket_tenant (tenant_id, status, created_at),
    CONSTRAINT fk_ticket_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_ticket_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
