package com.codebuzzers.codebuzzcore.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request.companyName(), request.countryCode(), request.timezone(), request.currencyCode(),
                request.ownerName(), request.email(), request.password());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return accounts.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@Valid @RequestBody RefreshRequest request) {
        return accounts.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) RefreshRequest request) {
        accounts.logout(request == null ? null : request.refreshToken());
        return Map.of("ok", true);
    }

    @PostMapping("/password/change")
    public Map<String, Object> change(@Valid @RequestBody PasswordChangeRequest request) {
        accounts.changePassword(request.currentPassword(), request.newPassword());
        return Map.of("ok", true);
    }

    @PostMapping("/password/forgot")
    public Map<String, Object> forgot(@Valid @RequestBody ForgotRequest request) {
        accounts.forgotPassword(request.email());
        return Map.of("ok", true);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> reset(@Valid @RequestBody ResetRequest request) {
        accounts.resetPassword(request.token(), request.newPassword());
        return Map.of("ok", true);
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @NotBlank String timezone,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(max = 150) String ownerName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password
    ) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 72) String newPassword) {
    }

    public record ForgotRequest(@NotBlank @Email String email) {
    }

    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String newPassword) {
    }
}
