package com.project.young.orderservice.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Relays {@code orders.order_outbox} INSERT through Debezium Connect to {@code order.created}.
 */
@Testcontainers
class OrderOutboxDebeziumIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("ecodb_order")
            .withUsername("user")
            .withPassword("password")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withCommand(
                    "postgres",
                    "-c", "wal_level=logical",
                    "-c", "max_replication_slots=4",
                    "-c", "max_wal_senders=4"
            );

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092");

    @Container
    static GenericContainer<?> connect = new GenericContainer<>(DockerImageName.parse("quay.io/debezium/connect:3.0"))
            .withNetwork(NETWORK)
            .withExposedPorts(8083)
            .withEnv("BOOTSTRAP_SERVERS", "kafka:19092")
            .withEnv("GROUP_ID", "order-outbox-it-connect")
            .withEnv("CONFIG_STORAGE_TOPIC", "it_connect_configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "it_connect_offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "it_connect_statuses")
            .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
            .withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("VALUE_CONVERTER_SCHEMAS_ENABLE", "false")
            .waitingFor(Wait.forHttp("/connectors").forPort(8083).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2))
            .dependsOn(kafka, postgres);

    @BeforeAll
    static void migrateAndRegisterConnector() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("orders")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE PUBLICATION dbz_order_outbox_pub FOR TABLE orders.order_outbox");
        }

        String connectorJson = """
                {
                  "name": "order-created-outbox-connector",
                  "config": {
                    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                    "tasks.max": "1",
                    "database.hostname": "postgres",
                    "database.port": "5432",
                    "database.user": "user",
                    "database.password": "password",
                    "database.dbname": "ecodb_order",
                    "topic.prefix": "cdc.ecodb_order",
                    "schema.include.list": "orders",
                    "table.include.list": "orders.order_outbox",
                    "plugin.name": "pgoutput",
                    "slot.name": "order_created_outbox_slot",
                    "publication.name": "dbz_order_outbox_pub",
                    "publication.autocreate.mode": "disabled",
                    "snapshot.mode": "no_data",
                    "skipped.operations": "u,d,t",
                    "decimal.handling.mode": "string",
                    "time.precision.mode": "adaptive_time_microseconds",
                    "message.key.columns": "orders.order_outbox:order_id",
                    "transforms": "route,unwrap",
                    "transforms.route.type": "org.apache.kafka.connect.transforms.RegexRouter",
                    "transforms.route.regex": ".*order_outbox",
                    "transforms.route.replacement": "order.created",
                    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
                    "transforms.unwrap.drop.tombstones": "true",
                    "transforms.unwrap.delete.handling.mode": "none",
                    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
                    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
                    "value.converter.schemas.enable": "false"
                  }
                }
                """;

        String connectUrl = "http://" + connect.getHost() + ":" + connect.getMappedPort(8083);
        HttpRequest create = HttpRequest.newBuilder()
                .uri(URI.create(connectUrl + "/connectors"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(connectorJson))
                .build();
        HttpResponse<String> created = HTTP_CLIENT.send(create, HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode())
                .withFailMessage("Register connector failed: %s", created.body())
                .isIn(201, 409);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(connectUrl + "/connectors/order-created-outbox-connector/status"))
                    .GET()
                    .build();
            HttpResponse<String> status = HTTP_CLIENT.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(status.statusCode()).isEqualTo(200);
            JsonNode body = OBJECT_MAPPER.readTree(status.body());
            assertThat(body.path("connector").path("state").asText())
                    .withFailMessage("Connector not running: %s", body)
                    .isEqualTo("RUNNING");
            JsonNode tasks = body.path("tasks");
            assertThat(tasks.isArray() && !tasks.isEmpty()).isTrue();
            assertThat(tasks.get(0).path("state").asText())
                    .withFailMessage("Connector task not running: %s", body)
                    .isEqualTo("RUNNING");
        });
    }

    @Test
    @DisplayName("order_outbox INSERT가 Debezium을 거쳐 order.created JSON으로 발행된다")
    @Timeout(120)
    void outboxInsert_isRelayedToOrderCreatedTopic() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO orders.orders (
                        id, user_id, status, subtotal_amount, shipping_amount, total_amount,
                        shipping_recipient_name, shipping_phone, shipping_address_line1,
                        shipping_city, shipping_postal_code, shipping_country_code)
                    VALUES (
                        '%s', 'user-1', 'PENDING_PAYMENT', 49.99, 0, 49.99,
                        'Kim Young', '01012345678', '123 Main St',
                        'Seoul', '04524', 'KR')
                    """.formatted(orderId));
            statement.execute("""
                    INSERT INTO orders.order_outbox (
                        event_id, order_id, user_id, total_amount, currency, occurred_at)
                    VALUES (
                        '%s', '%s', 'user-1', 49.99, 'USD', TIMESTAMPTZ '2026-07-16T00:00:00Z')
                    """.formatted(eventId, orderId));
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-outbox-debezium-it");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order.created"));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().pollInterval(Duration.ofSeconds(1))
                    .atMost(Duration.ofSeconds(60))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                        assertThat(received).isNotEmpty();
                        ConsumerRecord<String, String> record = received.getFirst();
                        assertThat(record.key()).contains(orderId.toString());
                        JsonNode payload = OBJECT_MAPPER.readTree(record.value());
                        assertThat(payload.path("order_id").asText()).isEqualTo(orderId.toString());
                        assertThat(payload.path("event_id").asText()).isEqualTo(eventId.toString());
                        assertThat(payload.path("user_id").asText()).isEqualTo("user-1");
                        assertThat(payload.path("total_amount").asText()).startsWith("49.99");
                        assertThat(payload.path("currency").asText()).isEqualTo("USD");
                    });
        }
    }
}
