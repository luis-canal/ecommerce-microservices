package br.edu.atitus.commons.config;

import br.edu.atitus.commons.events.EventPublisher;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@ConditionalOnBean(RabbitTemplate.class)
public class EventCommonsConfiguration {
    @Bean
    @ConditionalOnMissingBean
    TopicExchange ecommerceEventsExchange(
            @org.springframework.beans.factory.annotation.Value("${events.exchange:ecommerce.events}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter ecommerceJacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    EventPublisher eventPublisher(ApplicationEventPublisher spring, RabbitTemplate rabbit,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${events.exchange:ecommerce.events}") String exchange) {
        return new EventPublisher(spring, rabbit, exchange);
    }
}