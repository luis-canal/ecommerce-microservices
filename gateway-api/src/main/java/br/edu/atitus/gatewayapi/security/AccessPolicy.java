package br.edu.atitus.gatewayapi.security;

import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
    private final boolean developmentDocsEnabled;

    @Autowired
    public AccessPolicy(Environment environment) {
        this(environment.matchesProfiles("dev"));
    }

    public AccessPolicy(boolean developmentDocsEnabled) {
        this.developmentDocsEnabled = developmentDocsEnabled;
    }

    public Access resolve(HttpMethod method, String path) {
        if (isPath(path, "/internal") || isPath(path, "/actuator")) {
            return Access.BLOCKED;
        }

        if (developmentDocsEnabled && (isPath(path, "/docs") || isPath(path, "/swagger-ui")
                || isPath(path, "/v3/api-docs") || isPath(path, "/webjars"))) {
            return Access.PUBLIC;
        }

        if (method == HttpMethod.POST && (path.equals("/auth/signup")
                || path.equals("/auth/signin") || path.equals("/auth/google"))) {
            return Access.PUBLIC;
        }
        if (method == HttpMethod.GET && (isPath(path, "/products")
                || isPath(path, "/categories") || isPath(path, "/currencies"))) {
            return Access.PUBLIC;
        }
        if (isPath(path, "/ws/admin")) {
            return Access.ADMIN;
        }
        return Access.AUTHENTICATED;
    }

    private static boolean isPath(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}