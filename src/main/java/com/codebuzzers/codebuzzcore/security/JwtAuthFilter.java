package com.codebuzzers.codebuzzcore.security;

import com.codebuzzers.codebuzzcore.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthUserLoader loader;

    public JwtAuthFilter(JwtService jwtService, AuthUserLoader loader) {
        this.jwtService = jwtService;
        this.loader = loader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                authenticate(header.substring(7).trim());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token) {
        Map<String, Object> claims;
        try {
            claims = jwtService.parse(token);
        } catch (RuntimeException ex) {
            return;
        }
        if (!"access".equals(String.valueOf(claims.get("typ")))) {
            return;
        }
        Object subject = claims.get("sub");
        if (subject == null) {
            return;
        }
        Object viewTenant = claims.get("viewTenant");
        AuthenticatedUser user = loader.load(String.valueOf(subject), viewTenant == null ? null : String.valueOf(viewTenant));
        if (user == null) {
            return;
        }
        TenantContext.set(new TenantContext.Ctx(
                user.tenantId(), user.tenantPublicId(), user.userId(), user.userPublicId(),
                user.timezone(), user.currency(), user.locale(), user.permissions(), user.roles(),
                user.supportView(), user.actorUserId()));
        var authorities = user.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
