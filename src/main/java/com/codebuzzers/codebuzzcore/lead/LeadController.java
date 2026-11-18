package com.codebuzzers.codebuzzcore.lead;

import com.codebuzzers.codebuzzcore.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LeadController {

    private final LeadCommandService leads;

    public LeadController(LeadCommandService leads) {
        this.leads = leads;
    }

    @GetMapping("/leads")
    public PageResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return leads.list(status, page, size);
    }

    @GetMapping("/leads/export")
    public ResponseEntity<String> export(@RequestParam(required = false) String status) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leads.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(leads.exportCsv(status));
    }

    @GetMapping("/leads/loss-reasons")
    public List<Map<String, Object>> reasons() {
        return leads.lossReasons();
    }

    @PostMapping("/leads/loss-reasons")
    public Map<String, Object> addReason(@Valid @RequestBody ReasonRequest request) {
        return leads.addLossReason(request.name());
    }

    @GetMapping("/leads/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return leads.get(id);
    }

    @GetMapping("/leads/{id}/timeline")
    public List<Map<String, Object>> timeline(@PathVariable String id) {
        return leads.timeline(id);
    }

    @PostMapping("/leads/{id}/notes")
    public Map<String, Object> note(@PathVariable String id, @Valid @RequestBody NoteRequest request) {
        return leads.addNote(id, request.body());
    }

    @PostMapping("/leads/{id}/calls")
    public Map<String, Object> call(@PathVariable String id, @Valid @RequestBody CallRequest request) {
        return leads.recordCall(id, request.outcome(), request.notes(), request.followUpAt());
    }

    @PostMapping("/leads/{id}/won")
    public Map<String, Object> won(@PathVariable String id, @Valid @RequestBody WonRequest request) {
        return leads.markWon(id, request.amountMinor());
    }

    @PostMapping("/leads/{id}/lost")
    public Map<String, Object> lost(@PathVariable String id, @Valid @RequestBody LostRequest request) {
        return leads.markLost(id, request.reasonId(), request.notes());
    }

    @PostMapping("/leads/{id}/spam")
    public Map<String, Object> spam(@PathVariable String id) {
        return leads.markSpam(id);
    }

    @PostMapping("/leads/{id}/assign")
    public Map<String, Object> assign(@PathVariable String id, @Valid @RequestBody AssignRequest request) {
        return leads.assign(id, request.userId());
    }

    @PostMapping("/leads/{id}/anonymize")
    public Map<String, Object> anonymize(@PathVariable String id) {
        return leads.anonymize(id);
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks() {
        return leads.myTasks();
    }

    @PostMapping("/leads/{id}/tasks")
    public Map<String, Object> createTask(@PathVariable String id, @Valid @RequestBody TaskRequest request) {
        return leads.createTask(id, request.type(), request.dueAt());
    }

    @PostMapping("/tasks/{id}/complete")
    public Map<String, Object> complete(@PathVariable String id) {
        return leads.completeTask(id);
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return leads.performance(start, end);
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record CallRequest(@NotBlank String outcome, String notes, Instant followUpAt) {
    }

    public record WonRequest(@NotNull Long amountMinor) {
    }

    public record LostRequest(@NotBlank String reasonId, String notes) {
    }

    public record AssignRequest(@NotBlank String userId) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record TaskRequest(String type, @NotNull Instant dueAt) {
    }
}
