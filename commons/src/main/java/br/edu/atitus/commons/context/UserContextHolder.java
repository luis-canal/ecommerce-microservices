package br.edu.atitus.commons.context;

import br.edu.atitus.commons.web.UnauthorizedException;

public final class UserContextHolder {
    private static final ThreadLocal<UserContext> CTX = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        CTX.set(context);
    }

    public static UserContext get() {
        return CTX.get();
    }

    public static UserContext require() {
        UserContext context = get();
        if (context == null) {
            throw new UnauthorizedException("Contexto de usuário ausente");
        }
        return context;
    }

    public static void clear() {
        CTX.remove();
    }
}