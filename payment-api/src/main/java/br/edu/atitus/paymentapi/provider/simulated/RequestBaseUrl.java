package br.edu.atitus.paymentapi.provider.simulated;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base ({@code esquema://host[:porta]}) da requisição em andamento. O host vem somente do cabeçalho
 * Host, que o Gateway preserva nas rotas de pagamento; X-Forwarded-* e Forwarded são ignorados.
 * Um valor fora do formato esperado é descartado.
 */
final class RequestBaseUrl {

    /** Nome DNS, IPv4 ou IPv6 entre colchetes, com porta opcional; sem usuário, caminho ou consulta. */
    private static final Pattern HOST = Pattern.compile(
            "(?:[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63})*|\\[[0-9A-Fa-f:.]{2,45}\\])(?::([0-9]{1,5}))?");

    private static final int MAX_HOST_LENGTH = 255;

    private RequestBaseUrl() {
    }

    /** Vazio fora de uma requisição HTTP ou quando o Host está ausente ou é inválido. */
    static Optional<String> current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String scheme = request.getScheme();
        String host = request.getHeader(HttpHeaders.HOST);
        if (!("http".equals(scheme) || "https".equals(scheme)) || !isValidHost(host)) {
            return Optional.empty();
        }
        return Optional.of(scheme + "://" + host);
    }

    private static boolean isValidHost(String host) {
        if (host == null || host.length() > MAX_HOST_LENGTH) {
            return false;
        }
        Matcher matcher = HOST.matcher(host);
        if (!matcher.matches()) {
            return false;
        }
        String port = matcher.group(1);
        return port == null || (Integer.parseInt(port) >= 1 && Integer.parseInt(port) <= 65535);
    }
}
