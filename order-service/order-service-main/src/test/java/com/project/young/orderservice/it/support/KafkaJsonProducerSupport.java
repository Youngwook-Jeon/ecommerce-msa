package com.project.young.orderservice.it.support;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class KafkaJsonProducerSupport {

    private KafkaJsonProducerSupport() {
    }

    public static void send(String bootstrapServers, String topic, String key, String json) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, json)).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending Kafka record", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to send Kafka record to " + topic, ex);
        }
    }

    public static String paymentCompletedJson(UUID orderId, String userId, String amount) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "id": "%s",
                  "event_id": "%s",
                  "payment_id": "%s",
                  "order_id": "%s",
                  "user_id": "%s",
                  "amount": "%s",
                  "currency": "USD",
                  "occurred_at": "2026-07-16T00:00:00Z",
                  "published_at": null,
                  "created_at": "2026-07-16T00:00:00Z"
                }
                """.formatted(eventId, eventId, UUID.randomUUID(), orderId, userId, amount);
    }

    public static String paymentFailedJson(UUID orderId, String userId, String amount, String reason) {
        UUID eventId = UUID.randomUUID();
        return """
                {
                  "id": "%s",
                  "event_id": "%s",
                  "payment_id": "%s",
                  "order_id": "%s",
                  "user_id": "%s",
                  "amount": "%s",
                  "failure_reason": "%s",
                  "occurred_at": "2026-07-16T00:00:00Z",
                  "published_at": null,
                  "created_at": "2026-07-16T00:00:00Z"
                }
                """.formatted(eventId, eventId, UUID.randomUUID(), orderId, userId, amount, reason);
    }

    public static Duration awaitTimeout() {
        return Duration.ofSeconds(20);
    }
}
