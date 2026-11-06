package br.edu.atitus.orderapi.configs;

import br.edu.atitus.orderapi.clients.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Configuration
public class InventoryClientConfig {

    @Bean
    InventoryClient inventoryClient(
            RestClient.Builder builder,
            @Value("${order.clients.inventory-api.base-url:http://inventory-api}") String baseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        RestClient restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(InventoryClient.class);
    }
}
