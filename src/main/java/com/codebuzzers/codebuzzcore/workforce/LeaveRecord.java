package com.codebuzzers.codebuzzcore.workforce;

import com.codebuzzers.codebuzzcore.common.PublicEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leave_records")
public class LeaveRecord extends PublicEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type", nullable = false, length = 32)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Working days in the leave (the weekly off is not counted). */
    @Column(nullable = false)
    private int days;

    @Column(length = 500)
    private String reason;

    /** PENDING, APPROVED, REJECTED or CANCELLED. */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
