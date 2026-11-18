package com.codebuzzers.codebuzzcore.job;

import com.codebuzzers.codebuzzcore.campaign.CampaignSyncService;
import com.codebuzzers.codebuzzcore.common.AppProperties;
import com.codebuzzers.codebuzzcore.common.Codes;
import com.codebuzzers.codebuzzcore.common.Jsons;
import com.codebuzzers.codebuzzcore.identity.Tenant;
import com.codebuzzers.codebuzzcore.identity.TenantRepository;
import com.codebuzzers.codebuzzcore.lead.AssignmentService;
import com.codebuzzers.codebuzzcore.lead.Lead;
import com.codebuzzers.codebuzzcore.lead.LeadIngestionService;
import com.codebuzzers.codebuzzcore.lead.LeadNoteRepository;
import com.codebuzzers.codebuzzcore.lead.LeadRepository;
import com.codebuzzers.codebuzzcore.meta.MetaConnection;
import com.codebuzzers.codebuzzcore.meta.MetaConnectionRepository;
import com.codebuzzers.codebuzzcore.notification.OutboxEvent;
import com.codebuzzers.codebuzzcore.notification.OutboxEventRepository;
import com.codebuzzers.codebuzzcore.notification.PushDevice;
import com.codebuzzers.codebuzzcore.notification.PushDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class Workers {

    private static final Logger log = LoggerFactory.getLogger(Workers.class);
    private final String workerId = "worker-" + Long.toHexString(ThreadLocalRandom.current().nextLong());

    private final BackgroundJobRepository jobs;
    private final JobAttemptRepository attempts;
    private final OutboxEventRepository outbox;
    private final PushDeviceRepository devices;
    private final LeadIngestionService ingestion;
    private final CampaignSyncService campaignSync;
    private final AssignmentService assignments;
    private final MetaConnectionRepository connections;
    private final JobQueue queue;
    private final TenantRepository tenants;
    private final LeadRepository leads;
    private final LeadNoteRepository notes;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final RestClient fcm = RestClient.builder().baseUrl("https://fcm.googleapis.com").build();

    public Workers(BackgroundJobRepository jobs, JobAttemptRepository attempts, OutboxEventRepository outbox,
                   PushDeviceRepository devices, LeadIngestionService ingestion, CampaignSyncService campaignSync,
                   AssignmentService assignments, MetaConnectionRepository connections, JobQueue queue,
                   TenantRepository tenants, LeadRepository leads, LeadNoteRepository notes, AppProperties properties,
                   ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.outbox = outbox;
        this.devices = devices;
        this.ingestion = ingestion;
        this.campaignSync = campaignSync;
        this.assignments = assignments;
        this.connections = connections;
        this.queue = queue;
        this.tenants = tenants;
        this.leads = leads;
        this.notes = notes;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-delay-ms:2000}")
    public void pollJobs() {
        List<Long> ids = transactions.execute(status -> {
            List<Long> locked = jobs.lockPendingIds();
            if (!locked.isEmpty()) {
                jobs.claim(locked, Instant.now(), workerId);
            }
            return locked;
        });
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            processJob(id);
        }
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-delay-ms:2000}")
    public void pollOutbox() {
        List<Long> ids = transactions.execute(status -> {
            List<Long> locked = outbox.lockPendingIds();
            for (Long id : locked) {
                outbox.findById(id).ifPresent(event -> event.setStatus("PROCESSING"));
            }
            return locked;
        });
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            dispatch(id);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void reassignTimeouts() {
        try {
            assignments.timeoutSweep();
        } catch (Exception ex) {
            log.warn("Assignment timeout sweep failed: {}", ex.getClass().getSimpleName());
        }
    }

    /** Leads nobody could take at arrival (no one on duty, no default team yet, old data) are given out here. */
    @Scheduled(fixedDelay = 60_000)
    public void assignWaitingLeads() {
        try {
            assignments.assignWaiting();
        } catch (Exception ex) {
            log.warn("Waiting lead assignment failed: {}", ex.getClass().getSimpleName());
        }
    }

    /** Sync once on startup so a restarted dev server doesn't wait for the next :15 mark. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncCampaignsOnStartup() {
        // Unique key per start: the hourly key would silently skip this run if a job already ran this hour.
        for (MetaConnection connection : connections.findByStatus("ACTIVE")) {
            queue.enqueue(connection.getTenantId(), Codes.JobType.SYNC_CAMPAIGNS,
                    "sync:" + connection.getId() + ":start:" + System.currentTimeMillis(),
                    "{\"connectionId\":" + connection.getId() + "}");
        }
    }

    @Scheduled(cron = "0 15 * * * *")
    public void enqueueCampaignSync() {
        String hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(Instant.now());
        for (MetaConnection connection : connections.findByStatus("ACTIVE")) {
            queue.enqueue(connection.getTenantId(), Codes.JobType.SYNC_CAMPAIGNS,
                    "sync:" + connection.getId() + ":" + hour,
                    "{\"connectionId\":" + connection.getId() + "}");
        }
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void applyRetention() {
        for (Tenant tenant : tenants.findAll()) {
            if (tenant.getLeadRetentionDays() == null || tenant.getLeadRetentionDays() < 1) {
                continue;
            }
            Instant cutoff = Instant.now().minus(Duration.ofDays(tenant.getLeadRetentionDays()));
            leads.findAll().stream()
                    .filter(lead -> lead.getTenantId().equals(tenant.getId()))
                    .filter(lead -> lead.getCreatedAt() != null && lead.getCreatedAt().isBefore(cutoff))
                    .filter(lead -> lead.getName() == null || !"[redacted]".equals(lead.getName()))
                    .forEach(this::redact);
        }
    }

    private void processJob(Long id) {
        BackgroundJob job = jobs.findById(id).orElse(null);
        if (job == null) {
            return;
        }
        JobAttempt attempt = new JobAttempt();
        attempt.setJobId(job.getId());
        attempt.setAttemptNo(job.getAttempts());
        attempt.setStartedAt(Instant.now());
        attempts.save(attempt);
        try {
            JsonNode payload = objectMapper.readTree(job.getPayloadJson());
            if (Codes.JobType.INGEST_LEAD.equals(job.getJobType())) {
                ingestion.ingest(Long.parseLong(Jsons.text(payload.path("webhookEventId")) == null
                        ? payload.path("webhookEventId").asString()
                        : Jsons.text(payload.path("webhookEventId"))));
            } else if (Codes.JobType.SYNC_CAMPAIGNS.equals(job.getJobType())) {
                String connectionId = Jsons.text(payload.path("connectionId"));
                campaignSync.syncConnection(Long.parseLong(connectionId == null ? payload.path("connectionId").asString() : connectionId));
            } else {
                throw new PermanentFailure("unknown_job");
            }
            job.setStatus(Codes.JobStatus.DONE);
            job.setLastError(null);
        } catch (PermanentFailure ex) {
            job.setStatus(Codes.JobStatus.DEAD);
            job.setLastError(trim(ex.getMessage()));
            attempt.setError(job.getLastError());
        } catch (Exception ex) {
            String message = ex instanceof TransientFailure ? ex.getMessage() : "transient";
            attempt.setError(trim(message));
            if (job.getAttempts() >= properties.worker().maxAttempts()) {
                job.setStatus(Codes.JobStatus.DEAD);
            } else {
                job.setStatus(Codes.JobStatus.PENDING);
                job.setAvailableAt(Instant.now().plus(backoff(job.getAttempts())));
            }
            job.setLastError(trim(message));
            log.warn("Job {} failed: {}", job.getId(), job.getLastError());
        } finally {
            attempt.setFinishedAt(Instant.now());
            job.setLockedAt(null);
            job.setLockedBy(null);
            jobs.save(job);
            attempts.save(attempt);
        }
    }

    private void dispatch(Long id) {
        OutboxEvent event = outbox.findById(id).orElse(null);
        if (event == null) {
            return;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.getPayloadJson());
            long userId = payload.path("userId").asLong();
            String key = properties.fcm().serverKey();
            if (key == null || key.isBlank()) {
                event.setStatus("SENT");
                return;
            }
            List<PushDevice> targets = devices.findByUserIdAndStatus(userId, "ACTIVE");
            if (targets.isEmpty()) {
                event.setStatus("SENT");
                return;
            }
            boolean delivered = false;
            for (PushDevice device : targets) {
                delivered = send(device, payload, key) || delivered;
            }
            event.setStatus(delivered ? "SENT" : "PENDING");
            if (!delivered) {
                event.setAttempts(event.getAttempts() + 1);
                event.setAvailableAt(Instant.now().plus(backoff(event.getAttempts())));
                if (event.getAttempts() >= properties.worker().maxAttempts()) {
                    event.setStatus("DEAD");
                }
            }
        } catch (Exception ex) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(trim(ex.getClass().getSimpleName()));
            event.setStatus(event.getAttempts() >= properties.worker().maxAttempts() ? "DEAD" : "PENDING");
            event.setAvailableAt(Instant.now().plus(backoff(event.getAttempts())));
        } finally {
            outbox.save(event);
        }
    }

    private boolean send(PushDevice device, JsonNode payload, String key) {
        try {
            fcm.post()
                    .uri("/fcm/send")
                    .header("Authorization", "key=" + key)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "to", device.getToken(),
                            "notification", Map.of("title", text(payload, "title"), "body", text(payload, "body")),
                            "data", Map.of("deepLink", text(payload, "deepLink"))))
                    .retrieve()
                    .toBodilessEntity();
            device.setLastSeenAt(Instant.now());
            return true;
        } catch (Exception ex) {
            device.setStatus("DISABLED");
            return false;
        }
    }

    private void redact(Lead lead) {
        lead.setName("[redacted]");
        lead.setPhoneNormalized(null);
        lead.setEmailNormalized(null);
        lead.setRawPayload("{}");
        notes.findByTenantIdAndLeadIdOrderByCreatedAtAsc(lead.getTenantId(), lead.getId()).forEach(note -> note.setBody("[redacted]"));
        leads.save(lead);
    }

    private static Duration backoff(int attempt) {
        long seconds = Math.min(3600, (1L << Math.min(attempt, 8)) * 15L);
        return Duration.ofSeconds(seconds + ThreadLocalRandom.current().nextLong(5));
    }

    private static String trim(String value) {
        if (value == null) {
            return "error";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String text(JsonNode node, String field) {
        String value = Jsons.text(node.path(field));
        return value == null ? "" : value;
    }
}