package com.codebuzzers.codebuzzcore.workforce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** Agent profile, leave workflow and team detail for the Business app. */
@RestController
@RequestMapping("/api/v1")
public class WorkforceController {

    private final WorkforceService workforce;

    public WorkforceController(WorkforceService workforce) {
        this.workforce = workforce;
    }

    @GetMapping("/users/{id}/profile")
    public Map<String, Object> profile(@PathVariable String id,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return workforce.profile(id, start, end);
    }

    @PostMapping("/users/{id}/leaves")
    public Map<String, Object> createLeave(@PathVariable String id, @Valid @RequestBody LeaveBody request) {
        return workforce.createLeave(id, request.type(), request.startDate(), request.endDate(), request.reason());
    }

    @PostMapping("/leaves/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id) {
        return workforce.decideLeave(id, true);
    }

    @PostMapping("/leaves/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id) {
        return workforce.decideLeave(id, false);
    }

    @PostMapping("/leaves/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        return workforce.cancelLeave(id);
    }

    @GetMapping("/teams/{id}")
    public Map<String, Object> team(@PathVariable String id,
                                    @RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return workforce.teamDetail(id, start, end);
    }

    @PatchMapping("/teams/{id}")
    public Map<String, Object> updateTeam(@PathVariable String id, @RequestBody TeamPatch request) {
        return workforce.updateTeam(id, request.name(), request.managerUserId());
    }

    public record LeaveBody(@NotBlank String type, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            @Size(max = 500) String reason) {
    }

    /** managerUserId: null leaves the manager alone, an empty string clears it. */
    public record TeamPatch(@Size(max = 150) String name, String managerUserId) {
    }
}
