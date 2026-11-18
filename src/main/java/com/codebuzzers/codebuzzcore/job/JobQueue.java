package com.codebuzzers.codebuzzcore.job;

import com.codebuzzers.codebuzzcore.common.Codes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JobQueue {

    private final BackgroundJobRepository jobs;

    public JobQueue(BackgroundJobRepository jobs) {
        this.jobs = jobs;
    }

    public void enqueue(Long tenantId, String jobType, String jobKey, String payloadJson) {
        if (jobKey != null && jobs.findByJobKey(jobKey).isPresent()) {
            return;
        }
        BackgroundJob job = new BackgroundJob();
        job.setTenantId(tenantId);
        job.setJobType(jobType);
        job.setJobKey(jobKey);
        job.setPayloadJson(payloadJson == null ? "{}" : payloadJson);
        job.setStatus(Codes.JobStatus.PENDING);
        job.setAvailableAt(Instant.now());
        job.setAttempts(0);
        try {
            jobs.save(job);
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent worker already queued this key.
        }
    }
}
