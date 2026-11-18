-- Attendance comes from duty sessions: every on-duty / off-duty toggle opens or closes one.
CREATE TABLE duty_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_duty_user_started (tenant_id, user_id, started_at),
    KEY ix_duty_open (user_id, ended_at),
    CONSTRAINT fk_duty_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_duty_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE leave_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    leave_type VARCHAR(32) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days INT NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL,
    requested_by BIGINT NOT NULL,
    decided_by BIGINT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_public_id (public_id),
    KEY ix_leave_user_start (tenant_id, user_id, start_date),
    KEY ix_leave_status (tenant_id, status, start_date),
    CONSTRAINT fk_leave_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_leave_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users who are on duty right now get an open session, so their next off-duty toggle is recorded.
INSERT INTO duty_sessions (tenant_id, user_id, started_at)
SELECT tenant_id, id, UTC_TIMESTAMP(6) FROM users WHERE on_duty = 1 AND status = 'ACTIVE';
