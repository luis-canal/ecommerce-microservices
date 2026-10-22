package br.edu.atitus.commons.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

public class EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final ApplicationEventPublisher spring;
    private final RabbitTemplate rabbit;
    private final String exchange;

    public EventPublisher(ApplicationEventPublisher spring, RabbitTemplate rabbit,
                          @Value("${events.exchange:ecommerce.events}") String exchange) {
        this.spring = spring;
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    public <T> void publish(String type, T payload) {
        spring.publishEvent(new EventEnvelope<>(UUID.randomUUID(), type, 1, Instant.now(), payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void send(EventEnvelope<?> event) {
        try {
            rabbit.convertAndSend(exchange, event.type(), event);
        } catch (Exception exception) {
            log.error("Falha ao publicar evento {}", event.type(), exception);
        }
    }
}