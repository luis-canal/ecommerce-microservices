package br.edu.atitus.productapi.configs;


import br.edu.atitus.productapi.clients.CurrencyClient;

import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    CurrencyClient getClient(RestClient.Builder builder) {
        RestClient restClient = builder.baseUrl("http://currency-apiae").build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);

        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(CurrencyClient.class);
    }
}