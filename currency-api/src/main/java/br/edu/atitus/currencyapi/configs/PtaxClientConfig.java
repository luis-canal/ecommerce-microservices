package br.edu.atitus.currencyapi.configs;

import br.edu.atitus.currencyapi.clients.PtaxClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class PtaxClientConfig {

    @Bean
    PtaxClient ptaxClient(
            RestClient.Builder builder,
            RestClientBuilderConfigurer configurer,
            @Value("${app.currency.bcb.base-url}") String baseUrl,
            @Value("${app.currency.bcb.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.currency.bcb.read-timeout:5s}") Duration readTimeout
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        var restClient = configurer.configure(builder.clone())
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        var adapter = RestClientAdapter.create(restClient);
        var proxyFactory = HttpServiceProxyFactory.builderFor(adapter).build();
        return proxyFactory.createClient(PtaxClient.class);
    }
}
