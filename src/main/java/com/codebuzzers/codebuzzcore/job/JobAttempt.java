package com.codebuzzers.codebuzzcore.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job_attempts")
public class JobAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false) private Long jobId;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(length = 500) private String error;
    @Column(name = "started_at", nullable = false) private java.time.Instant startedAt;
    @Column(name = "finished_at") private java.time.Instant finishedAt;

}
