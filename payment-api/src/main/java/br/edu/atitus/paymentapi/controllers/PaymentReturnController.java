package br.edu.atitus.paymentapi.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Retorno do navegador após o Stripe Checkout. Público (sem JWT): apenas redireciona para o
 * deep link do app (ex.: oma://payment/success). Não altera nenhum status; a aprovação vem
 * somente do webhook.
 *
 * O destino é fixo pela configuração. Da requisição só seguem session_id e order_id, e apenas
 * quando têm o formato esperado; assim a rota não serve de redirecionamento aberto.
 */
@RestController
public class PaymentReturnController {

    private static final Pattern SESSION_ID = Pattern.compile("cs_[A-Za-z0-9_]{1,250}");
    private static final Pattern ORDER_ID = Pattern.compile("[1-9][0-9]{0,17}");

    private final String appSuccessUrl;
    private final String appCancelUrl;

    public PaymentReturnController(
            @Value("${payment.return.app-success-url:}") String appSuccessUrl,
            @Value("${payment.return.app-cancel-url:}") String appCancelUrl
    ) {
        if (appSuccessUrl == null || appSuccessUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_APP_SUCCESS_URL não configurado.");
        }
        if (appCancelUrl == null || appCancelUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_APP_CANCEL_URL não configurado.");
        }
        this.appSuccessUrl = appSuccessUrl;
        this.appCancelUrl = appCancelUrl;
    }

    @GetMapping("/payments/return/success")
    public ResponseEntity<Void> success(
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "order_id", required = false) String orderId
    ) {
        return redirect(appSuccessUrl, sessionId, orderId);
    }

    @GetMapping("/payments/return/cancel")
    public ResponseEntity<Void> cancel(
            @RequestParam(name = "order_id", required = false) String orderId
    ) {
        return redirect(appCancelUrl, null, orderId);
    }

    private static ResponseEntity<Void> redirect(String target, String sessionId, String orderId) {
        StringBuilder location = new StringBuilder(target);
        char separator = target.contains("?") ? '&' : '?';
        if (orderId != null && ORDER_ID.matcher(orderId).matches()) {
            location.append(separator).append("order_id=").append(orderId);
            separator = '&';
        }
        if (sessionId != null && SESSION_ID.matcher(sessionId).matches()) {
            location.append(separator).append("session_id=").append(sessionId);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
