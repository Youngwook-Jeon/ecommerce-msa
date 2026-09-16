package com.project.young.saga.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the production relay boundary: Order outbox -> Debezium -> Kafka -> Payment consumer.
 */
@Testcontainers
class RefundOutboxToPaymentConsumerIT {

    private static final Network NETWORK = Network.newNetwork();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("e2e").withUsername("e2e").withPassword("e2e")
            .withNetwork(NETWORK).withNetworkAliases("postgres")
            .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=4", "-c", "max_wal_senders=4");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
            .withNetwork(NETWORK).withNetworkAliases("kafka").withListener("kafka:19092");

    @Container
    static GenericContainer<?> connect = new GenericContainer<>(DockerImageName.parse("quay.io/debezium/connect:3.0"))
            .withNetwork(NETWORK).withExposedPorts(8083)
            .withEnv("BOOTSTRAP_SERVERS", "kafka:19092").withEnv("GROUP_ID", "refund-e2e-connect")
            .withEnv("CONFIG_STORAGE_TOPIC", "refund_e2e_configs").withEnv("OFFSET_STORAGE_TOPIC", "refund_e2e_offsets").withEnv("STATUS_STORAGE_TOPIC", "refund_e2e_statuses")
            .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1").withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1").withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter").withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter").withEnv("VALUE_CONVERTER_SCHEMAS_ENABLE", "false")
            .waitingFor(Wait.forHttp("/connectors").forPort(8083)).dependsOn(kafka, postgres);

    @Container
    static GenericContainer<?> order = app("order-service", "../order-service/order-service-main/target/order-service-main-0.0.1-SNAPSHOT.jar", 9003, "orders");

    @Container
    static GenericContainer<?> payment = app("payment-service", "../payment-service/payment-service-main/target/payment-service-main-0.0.1-SNAPSHOT.jar", 9004, "payments");

    private static GenericContainer<?> app(String name, String jar, int port, String schema) {
        ImageFromDockerfile image = new ImageFromDockerfile(name + "-e2e", true)
                .withFileFromPath("app.jar", Path.of(jar))
                .withDockerfileFromBuilder(builder -> builder.from("eclipse-temurin:21-jre").copy("app.jar", "/app.jar").entryPoint("java", "-jar", "/app.jar").build());
        return new GenericContainer<>(image).withNetwork(NETWORK).withExposedPorts(port).dependsOn(postgres, kafka)
                .withEnv("SERVER_PORT", String.valueOf(port)).withEnv("SCHEMA_NAME", schema)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/e2e?currentSchema=" + schema)
                .withEnv("SPRING_DATASOURCE_USERNAME", "e2e").withEnv("SPRING_DATASOURCE_PASSWORD", "e2e")
                .withEnv("KAFKA_CONFIG_BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("ORDER_SERVICE_SAGA_EVENTS_ENABLED", "true").withEnv("PAYMENT_SERVICE_SAGA_EVENTS_ENABLED", "true")
                .waitingFor(Wait.forLogMessage(".*Started .*ServiceMain.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
    }

    @BeforeAll
    static void registerConnector() throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "e2e", "e2e"); Statement statement = connection.createStatement()) {
            statement.execute("CREATE PUBLICATION refund_e2e_pub FOR TABLE orders.refund_requested_outbox");
        }
        String config = """
                {"name":"refund-e2e","config":{"connector.class":"io.debezium.connector.postgresql.PostgresConnector","database.hostname":"postgres","database.port":"5432","database.user":"e2e","database.password":"e2e","database.dbname":"e2e","topic.prefix":"refund.e2e","plugin.name":"pgoutput","slot.name":"refund_e2e_slot","publication.name":"refund_e2e_pub","publication.autocreate.mode":"disabled","snapshot.mode":"no_data","table.include.list":"orders.refund_requested_outbox","message.key.columns":"orders.refund_requested_outbox:payment_id","transforms":"route,unwrap","transforms.route.type":"org.apache.kafka.connect.transforms.RegexRouter","transforms.route.regex":".*refund_requested_outbox","transforms.route.replacement":"payment.refund.requested","transforms.unwrap.type":"io.debezium.transforms.ExtractNewRecordState","transforms.unwrap.drop.tombstones":"true","key.converter":"org.apache.kafka.connect.storage.StringConverter","value.converter":"org.apache.kafka.connect.json.JsonConverter","value.converter.schemas.enable":"false"}}
                """;
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create("http://" + connect.getHost() + ":" + connect.getMappedPort(8083) + "/connectors")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(config)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpResponse<String> status = HTTP.send(HttpRequest.newBuilder(URI.create(connectUrl("/connectors/refund-e2e/status")))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(status.statusCode()).isEqualTo(200);
            assertThat(status.body()).contains("\"state\":\"RUNNING\"");
        });
    }

    @Test
    void orderRefundOutbox_isRelayedAndProcessedByPaymentConsumer() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID compensationEventId = UUID.randomUUID();
        try (KafkaConsumer<String, String> observer = observer()) {
            observer.subscribe(List.of("payment.refund.requested"));
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                observer.poll(Duration.ofMillis(100));
                return !observer.assignment().isEmpty();
            });
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "e2e", "e2e"); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO payments.payments (id, order_id, user_id, amount, currency, status) VALUES ('" + paymentId + "','" + orderId + "','user-1',100,'USD','COMPLETED')");
            statement.execute("INSERT INTO orders.refund_requested_outbox (id, compensation_event_id, payment_id, order_id, user_id, reason, occurred_at) VALUES ('" + UUID.randomUUID() + "','" + compensationEventId + "','" + paymentId + "','" + orderId + "','user-1','e2e',CURRENT_TIMESTAMP)");
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                observer.poll(Duration.ofMillis(500)).records("payment.refund.requested"))
                .anySatisfy(record -> assertThat(record.value()).contains(compensationEventId.toString())));
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "e2e", "e2e"); Statement statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM payments.payment_refund_compensations WHERE compensation_event_id = '" + compensationEventId + "'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        });
        }
    }

    private static KafkaConsumer<String, String> observer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "refund-e2e-observer-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }

    private static String connectUrl(String path) {
        return "http://" + connect.getHost() + ":" + connect.getMappedPort(8083) + path;
    }
}
