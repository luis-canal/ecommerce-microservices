package br.edu.atitus.commons.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class EventPublisherTest {
    @Test
    void publishEmitsVersionedEnvelopeThroughSpringEvents() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        EventPublisher publisher = new EventPublisher(spring, rabbit, "ecommerce.events");

        publisher.publish("order.created", "payload");

        org.mockito.ArgumentCaptor<EventEnvelope> captor = org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
        verify(spring).publishEvent(captor.capture());
        EventEnvelope<?> event = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("order.created", event.type());
        org.junit.jupiter.api.Assertions.assertEquals(1, event.version());
        org.junit.jupiter.api.Assertions.assertNotNull(event.eventId());
        org.junit.jupiter.api.Assertions.assertNotNull(event.occurredAt());
    }

    @Test
    void brokerFailureDoesNotEscapePublisherListener() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("broker unavailable"))
            .when(rabbit).convertAndSend("ecommerce.events", "order.created", "payload");
        EventPublisher publisher = new EventPublisher(spring, rabbit, "ecommerce.events");
        EventEnvelope<String> event = new EventEnvelope<>(UUID.randomUUID(), "order.created", 1,
                Instant.now(), "payload");

        assertDoesNotThrow(() -> publisher.send(event));
    }
}