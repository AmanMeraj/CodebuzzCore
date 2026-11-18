package com.codebuzzers.codebuzzcore.security;

import com.codebuzzers.codebuzzcore.common.ApiException;
import com.codebuzzers.codebuzzcore.common.TenantContext;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    public TenantContext.Ctx require(String permission) {
        TenantContext.Ctx ctx = TenantContext.require();
        if (!ctx.has(permission)) {
            throw ApiException.forbidden("Missing permission " + permission);
        }
        return ctx;
    }

    public TenantContext.Ctx requireAny(String... permissions) {
        TenantContext.Ctx ctx = TenantContext.require();
        for (String permission : permissions) {
            if (ctx.has(permission)) {
                return ctx;
            }
        }
        throw ApiException.forbidden("You cannot access this resource");
    }
}
