package br.edu.atitus.commons.web;

import br.edu.atitus.commons.context.UserContext;
import br.edu.atitus.commons.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public final class RequireRoleInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequireRole requirement = method.getMethodAnnotation(RequireRole.class);
        if (requirement == null) {
            requirement = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requirement == null) {
            return true;
        }

        UserContext context = UserContextHolder.get();
        if (context == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (!requirement.value().equals(context.type())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}