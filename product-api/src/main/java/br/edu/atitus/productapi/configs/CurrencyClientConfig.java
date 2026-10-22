package br.edu.atitus.productapi.configs;


import br.edu.atitus.productapi.clients.CurrencyClient;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class CurrencyClientConfig {

    @Bean
    @LoadBalanced 
    RestClient.Builder getLoadBalancedBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    CurrencyClient currencyClient(RestClient.Builder builder,
                                  @Value("${internal.api-key}") String internalKey) {
        return getClient(builder, "http://currency-api", internalKey);
    }

    CurrencyClient getClient(RestClient.Builder builder, String baseUrl) {
        return getClient(builder, baseUrl, null);
    }

    CurrencyClient getClient(RestClient.Builder builder, String baseUrl, String internalKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        RestClient.Builder configuredBuilder = builder.clone()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory);
        if (internalKey != null) {
            configuredBuilder.requestInterceptor((request, body, execution) -> {
                request.getHeaders().set("X-Internal-Key", internalKey);
                return execution.execute(request, body);
            });
        }
        RestClient restClient = configuredBuilder.build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);

        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(CurrencyClient.class);
    }
}