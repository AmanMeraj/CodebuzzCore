package com.codebuzzers.codebuzzcore.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DirectoryController {

    private final AccountService accounts;

    public DirectoryController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return accounts.me();
    }

    @GetMapping("/company")
    public Map<String, Object> company() {
        return accounts.company();
    }

    @PatchMapping("/company")
    public Map<String, Object> updateCompany(@RequestBody CompanyPatch request) {
        return accounts.updateCompany(request.name(), request.timezone(), request.currencyCode(),
                request.assignmentTimeoutMinutes(), request.leadRetentionDays());
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return accounts.listUsers();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request) {
        return accounts.createUser(request.name(), request.email(), request.password(), request.role());
    }

    @PostMapping("/users/{id}/duty")
    public Map<String, Object> duty(@PathVariable String id, @RequestBody DutyRequest request) {
        return accounts.setDuty(id, request.onDuty());
    }

    @PostMapping("/users/{id}/disable")
    public Map<String, Object> disable(@PathVariable String id) {
        accounts.disableUser(id);
        return Map.of("ok", true);
    }

    @GetMapping("/teams")
    public List<Map<String, Object>> teams() {
        return accounts.listTeams();
    }

    @PostMapping("/teams")
    public Map<String, Object> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        return accounts.createTeam(request.name(), request.managerUserId());
    }

    @PostMapping("/teams/{id}/members")
    public Map<String, Object> addMember(@PathVariable String id, @RequestBody MemberRequest request) {
        accounts.addMember(id, request.userId());
        return Map.of("ok", true);
    }

    @PostMapping("/teams/{id}/members/{userId}/remove")
    public Map<String, Object> removeMember(@PathVariable String id, @PathVariable String userId) {
        accounts.removeMember(id, userId);
        return Map.of("ok", true);
    }

    @GetMapping("/platform/tenants")
    public List<Map<String, Object>> tenants() {
        return accounts.platformTenants();
    }

    @PostMapping("/platform/view-as")
    public Map<String, Object> viewAs(@RequestBody ViewAsRequest request) {
        return accounts.viewAs(request.tenantId());
    }

    public record CompanyPatch(String name, String timezone, String currencyCode, Integer assignmentTimeoutMinutes, Integer leadRetentionDays) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank String role
    ) {
    }

    public record DutyRequest(boolean onDuty) {
    }

    public record CreateTeamRequest(@NotBlank @Size(max = 150) String name, String managerUserId) {
    }

    public record MemberRequest(@NotBlank String userId) {
    }

    public record ViewAsRequest(@NotBlank String tenantId) {
    }
}
