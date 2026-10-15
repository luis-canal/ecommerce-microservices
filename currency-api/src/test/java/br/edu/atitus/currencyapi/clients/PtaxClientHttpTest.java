package br.edu.atitus.currencyapi.clients;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PtaxClientHttpTest {

    @Test
    void sendsODataAliasesAndMapsThePtaxResponse() throws IOException {
        var requestTarget = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaDia", exchange -> {
            requestTarget.set(exchange.getRequestURI().toString());
            byte[] body = "{\"value\":[{\"cotacaoCompra\":4.9,\"cotacaoVenda\":5.1,\"dataHoraCotacao\":\"2024-01-02 13:00:00.000\",\"tipoBoletim\":\"Fechamento\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            var restClient = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()
                            + "/olinda/servico/PTAX/versao/v1/odata")
                    .build();
            var client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                    .build()
                    .createClient(PtaxClient.class);

            var response = client.getQuotes("USD", "01-02-2024");

            assertEquals(1, response.value().size());
            assertEquals("5.1", response.value().getFirst().cotacaoVenda().toPlainString());
            assertTrue(requestTarget.get().contains("CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)"));
            assertTrue(requestTarget.get().contains("@moeda='USD'"));
            assertTrue(requestTarget.get().contains("@dataCotacao='01-02-2024'"));
            assertTrue(requestTarget.get().contains("$format=json"));
        } finally {
            server.stop(0);
        }
    }
}
