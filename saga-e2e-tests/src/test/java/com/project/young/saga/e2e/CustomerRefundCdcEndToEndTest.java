package com.project.young.saga.e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** HTTP request -> Order outbox -> CDC -> Payment -> result outbox -> CDC -> Order -> HTTP read. */
@Testcontainers
class CustomerRefundCdcEndToEndTest {

    private static final Path BACKEND = backendRoot();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Network NETWORK = Network.newNetwork();
    private static final SagaTestIdentityProvider IDENTITY = new SagaTestIdentityProvider();
    private static final String USER_ID = "user-customer-refund-e2e";
    private static final List<String> TOPICS = List.of(
            "customer.refund.requested", "customer.refund.completed", "customer.refund.failed");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("e2e").withUsername("e2e").withPassword("e2e")
            .withNetwork(NETWORK).withNetworkAliases("postgres")
            .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=8",
                    "-c", "max_wal_senders=8");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
            .withNetwork(NETWORK).withNetworkAliases("kafka").withListener("kafka:19092");

    @Container
    static final GenericContainer<?> PAYMENT = app("payment", "payments", 9004)
            .withEnv("PAYMENT_SERVICE_PROVIDER", "stub");

    @Container
    static final GenericContainer<?> ORDER = app("order", "orders", 9003)
            .withEnv("PAYMENT_SERVICE_URL", "http://payment:9004").dependsOn(PAYMENT);

    @Container
    static final GenericContainer<?> CONNECT = connect();

    @BeforeAll
    static void registerProductionConnectors() throws Exception {
        execute("CREATE PUBLICATION customer_refund_e2e_pub FOR TABLE "
                + "orders.customer_refund_requested_outbox, payments.payment_outbox");
        for (String outcome : List.of("requested", "completed", "failed")) {
            registerConnector(outcome, false);
        }
    }

    @AfterAll
    static void stopIdentityProvider() {
        IDENTITY.close();
    }

    @ParameterizedTest(name = "customer refund round trip, lateFailure={0}")
    @ValueSource(booleans = {false, true})
    void customerRefund_roundTripsThroughCdcAndIgnoresReplayedResults(boolean lateFailure) throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        seedCompletedPurchase(orderId, paymentId);
        String token = IDENTITY.customerToken(USER_ID);

        try (KafkaConsumer<String, String> observer = observer()) {
            observer.subscribe(TOPICS);
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                observer.poll(Duration.ofMillis(100));
                return observer.assignment().size() >= TOPICS.size();
            });
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            HttpResponse<String> requested = customerRequest("/orders/" + orderId + "/refunds", token,
                    "{\"reason\":\"Customer requested a full refund\"}");
            assertThat(requested.statusCode()).as(requested.body()).isEqualTo(202);
            JsonNode response = JSON.readTree(requested.body());
            UUID refundId = UUID.fromString(response.path("refundId").asText());
            assertThat(requested.headers().firstValue("Location")).hasValueSatisfying(
                    location -> assertThat(location).endsWith("/refunds/" + refundId));

            ConsumerRecord<String, String> requestEvent = awaitRecord(observer, received,
                    "customer.refund.requested", refundId, 1);
            assertThat(JSON.readTree(requestEvent.value()).path("payment_id").asText())
                    .isEqualTo(paymentId.toString());
            ConsumerRecord<String, String> completedEvent = awaitRecord(observer, received,
                    "customer.refund.completed", refundId, 1);
            JsonNode completed = JSON.readTree(completedEvent.value());
            assertThat(completed.path("event_type").asText()).isEqualTo("CUSTOMER_REFUND_COMPLETED");
            assertThat(completed.path("result_version").asLong()).isEqualTo(1);
            assertThat(completed.path("refund_completed_at").asText()).isNotBlank();
            awaitRefund(token, refundId, "COMPLETED", 1);
            assertSingleRefund(orderId, paymentId, refundId);

            if (lateFailure) {
                // Fixture at the persisted Payment-result boundary, not a simulated Stripe API/webhook.
                seedLateFailureOutbox(orderId, paymentId, refundId);
                ConsumerRecord<String, String> failedEvent = awaitRecord(observer, received,
                        "customer.refund.failed", refundId, 1);
                JsonNode failed = JSON.readTree(failedEvent.value());
                assertThat(failed.path("event_type").asText()).isEqualTo("CUSTOMER_REFUND_FAILED");
                assertThat(failed.path("failed_after_completion").asBoolean()).isTrue();
                assertThat(failed.path("result_version").asLong()).isEqualTo(2);
                awaitRefund(token, refundId, "FAILED_AFTER_COMPLETION", 2);
            }

            // A fresh snapshot/slot redelivers the same durable events through CDC, without a test Kafka producer.
            String requestReplay = registerConnector("requested", true);
            ConsumerRecord<String, String> replayedRequest = awaitRecord(observer, received,
                    "customer.refund.requested", refundId, 2);
            awaitCommitted("payment-service-customer-refund-requested", replayedRequest);
            String resultReplay = registerConnector("completed", true);
            ConsumerRecord<String, String> replayedResult = awaitRecord(observer, received,
                    "customer.refund.completed", refundId, 2);
            awaitCommitted("order-service-customer-refund-completed", replayedResult);
            assertSingleRefund(orderId, paymentId, refundId);
            awaitRefund(token, refundId, lateFailure ? "FAILED_AFTER_COMPLETION" : "COMPLETED", lateFailure ? 2 : 1);
            assertThat(PAYMENT.getLogs()).containsOnlyOnce("Accepted stub refund for payment " + paymentId
                    + " with idempotency key " + refundId);
            for (String connector : List.of(requestReplay, resultReplay)) {
                HttpResponse<String> deleted = send(connectUrl("/connectors/" + connector),
                        "DELETE", null, null);
                assertThat(deleted.statusCode()).isEqualTo(204);
            }
        }
    }

    private static GenericContainer<?> app(String service, String schema, int port) {
        Path jar = BACKEND.resolve(service + "-service/" + service
                + "-service-main/target/" + service + "-service-main-0.0.1-SNAPSHOT.jar");
        ImageFromDockerfile image = new ImageFromDockerfile("customer-refund-" + service + "-e2e", true)
                .withFileFromPath("app.jar", jar)
                .withDockerfileFromBuilder(builder -> builder.from("eclipse-temurin:21-jre")
                        .copy("app.jar", "/app.jar").entryPoint("java", "-jar", "/app.jar").build());
        return new GenericContainer<>(image).withNetwork(NETWORK).withNetworkAliases(service)
                .withExposedPorts(port).dependsOn(POSTGRES, KAFKA)
                .withEnv("SERVER_PORT", Integer.toString(port)).withEnv("SCHEMA_NAME", schema)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/e2e?currentSchema=" + schema)
                .withEnv("SPRING_DATASOURCE_USERNAME", "e2e").withEnv("SPRING_DATASOURCE_PASSWORD", "e2e")
                .withEnv("KAFKA_CONFIG_BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("ISSUER_URI", IDENTITY.issuer())
                .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI", IDENTITY.issuer() + "/jwks")
                .withEnv("ORDER_SERVICE_SAGA_EVENTS_ENABLED", "true")
                .withEnv("PAYMENT_SERVICE_SAGA_EVENTS_ENABLED", "true")
                .withEnv("SPRING_JPA_SHOW_SQL", "false")
                .waitingFor(Wait.forLogMessage(".*Started .*ServiceMain.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    private static GenericContainer<?> connect() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("quay.io/debezium/connect:3.0"))
                .withNetwork(NETWORK).withExposedPorts(8083).dependsOn(ORDER, PAYMENT)
                .withEnv("BOOTSTRAP_SERVERS", "kafka:19092").withEnv("GROUP_ID", "customer-refund-e2e-connect")
                .withEnv("CONFIG_STORAGE_TOPIC", "customer_refund_e2e_configs")
                .withEnv("OFFSET_STORAGE_TOPIC", "customer_refund_e2e_offsets")
                .withEnv("STATUS_STORAGE_TOPIC", "customer_refund_e2e_statuses")
                .withEnv("CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("STATUS_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("ENABLE_DEBEZIUM_SCRIPTING", "true")
                .waitingFor(Wait.forHttp("/connectors").forPort(8083)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        for (String jar : List.of("debezium-scripting-3.0.8.Final.jar", "groovy-4.0.24.jar",
                "groovy-jsr223-4.0.24.jar", "groovy-json-4.0.24.jar")) {
            container.withCopyFileToContainer(MountableFile.forHostPath(
                    BACKEND.resolve("saga-e2e-tests/target/connect-scripting/" + jar)),
                    "/kafka/external_libs/debezium-scripting/" + jar);
        }
        return container;
    }

    private static String registerConnector(String outcome, boolean replay) throws Exception {
        ObjectNode definition = (ObjectNode) JSON.readTree(Files.readString(BACKEND.resolve(
                "deployment/docker/connectors/customer-refund-" + outcome + "-outbox-connector.json")));
        String suffix = outcome + (replay ? "-replay-" + UUID.randomUUID().toString().substring(0, 8) : "");
        String name = "customer-refund-" + suffix;
        definition.put("name", name);
        ObjectNode config = (ObjectNode) definition.path("config");
        config.put("database.hostname", "postgres").put("database.dbname", "e2e")
                .put("database.user", "e2e").put("database.password", "e2e")
                .put("publication.name", "customer_refund_e2e_pub")
                .put("topic.prefix", "customer.refund.e2e." + suffix)
                .put("slot.name", "customer_refund_" + suffix.replace('-', '_'))
                .put("heartbeat.interval.ms", "1000");
        if (replay) {
            config.put("snapshot.mode", "initial");
        }
        HttpResponse<String> created = send(connectUrl("/connectors"), "POST", JSON.writeValueAsString(definition), null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            HttpResponse<String> response = send(connectUrl("/connectors/" + name + "/status"), "GET", null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode status = JSON.readTree(response.body());
            assertThat(status.path("connector").path("state").asText()).as(response.body()).isEqualTo("RUNNING");
            assertThat(status.path("tasks").size()).as(response.body()).isPositive();
            for (JsonNode task : status.path("tasks")) {
                assertThat(task.path("state").asText()).as(response.body()).isEqualTo("RUNNING");
            }
        });
        if (!replay) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(scalar(
                    "SELECT COUNT(*) FROM pg_replication_slots s JOIN pg_stat_replication r ON r.pid = s.active_pid "
                            + "WHERE s.slot_name = ? AND r.state = 'streaming'", config.path("slot.name").asText()))
                    .isEqualTo(1));
        }
        return name;
    }

    private static ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> observer,
            List<ConsumerRecord<String, String>> received, String topic, UUID refundId, int count) {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            observer.poll(Duration.ofMillis(200)).forEach(received::add);
            assertThat(matching(received, topic, refundId)).hasSizeGreaterThanOrEqualTo(count);
        });
        return matching(received, topic, refundId).get(count - 1);
    }

    private static List<ConsumerRecord<String, String>> matching(List<ConsumerRecord<String, String>> records,
            String topic, UUID refundId) {
        return records.stream().filter(record -> record.topic().equals(topic)
                && record.value().contains(refundId.toString())).toList();
    }

    private static void awaitCommitted(String group, ConsumerRecord<String, String> record) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(properties)) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS);
                var offset = offsets.get(new TopicPartition(record.topic(), record.partition()));
                assertThat(offset).isNotNull();
                assertThat(offset.offset()).isGreaterThan(record.offset());
            });
        }
    }

    private static void awaitRefund(String token, UUID refundId, String status, long version) {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            HttpResponse<String> response = customerRequest("/refunds/" + refundId, token, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            JsonNode refund = JSON.readTree(response.body());
            assertThat(refund.path("status").asText()).isEqualTo(status);
            assertThat(refund.path("resultVersion").asLong()).isEqualTo(version);
            assertThat(refund.path("completedAt").asText()).isNotBlank();
            if (version == 2) {
                assertThat(refund.path("failedAt").asText()).isNotBlank();
            }
        });
    }

    private static void assertSingleRefund(UUID orderId, UUID paymentId, UUID refundId) throws Exception {
        assertThat(scalar("SELECT COUNT(*) FROM orders.customer_refund_requested_outbox WHERE refund_id = ?", refundId))
                .isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM payments.customer_refund_processings WHERE refund_id = ?", refundId))
                .isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM payments.payment_outbox WHERE refund_id = ? "
                + "AND event_type = 'CUSTOMER_REFUND_COMPLETED'", refundId)).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM payments.payment_refund_claims WHERE payment_id = ? "
                + "AND request_id = ? AND request_kind = 'CUSTOMER' AND provider_refund_id = ?", paymentId, refundId,
                "stub_refund_" + refundId)).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM orders.orders WHERE id = ? AND status = 'CONFIRMED'", orderId))
                .isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM payments.payment_refund_compensations WHERE payment_id = ?", paymentId))
                .isZero();
        assertThat(scalar("SELECT COUNT(*) FROM payments.customer_refund_dlts WHERE payload LIKE ?", "%" + refundId + "%"))
                .isZero();
        assertThat(scalar("SELECT COUNT(*) FROM orders.customer_refund_dlts WHERE payload LIKE ?", "%" + refundId + "%"))
                .isZero();
    }

    private static void seedCompletedPurchase(UUID orderId, UUID paymentId) throws Exception {
        execute("INSERT INTO orders.orders (id,user_id,status,subtotal_amount,total_amount,shipping_recipient_name,"
                + "shipping_phone,shipping_address_line1,shipping_city,shipping_postal_code,shipping_country_code) "
                + "VALUES (?,?,'CONFIRMED',100,100,'Customer','01012345678','123 Main St','Seoul','04524','KR')",
                orderId, USER_ID);
        execute("INSERT INTO orders.order_lines (order_id,product_id,product_variant_id,product_name,sku,unit_price,quantity) "
                + "VALUES (?,?,?,'Product','SKU-E2E',100,1)", orderId, UUID.randomUUID(), UUID.randomUUID());
        execute("INSERT INTO payments.payments (id,order_id,user_id,amount,currency,status) "
                + "VALUES (?,?,?,100,'USD','COMPLETED')", paymentId, orderId, USER_ID);
    }

    private static void seedLateFailureOutbox(UUID orderId, UUID paymentId, UUID refundId) throws Exception {
        execute("INSERT INTO payments.payment_outbox (event_id,payment_id,order_id,user_id,event_type,amount,currency,"
                + "refund_id,failure_reason,occurred_at,result_version,failed_after_completion,refund_completed_at,refund_failed_at) "
                + "SELECT ?,?,?,?,'CUSTOMER_REFUND_FAILED',100,'USD',?,'Bank rejected refund',CURRENT_TIMESTAMP,2,true,"
                + "provider_refund_succeeded_at,CURRENT_TIMESTAMP FROM payments.payment_refund_claims WHERE payment_id = ?",
                UUID.randomUUID(), paymentId, orderId, USER_ID, refundId, paymentId);
    }

    private static void execute(String sql, Object... values) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private static long scalar(String sql, Object... values) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object[] values) throws Exception {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "e2e", "e2e");
    }

    private static HttpResponse<String> customerRequest(String path, String token, String body) throws Exception {
        return send("http://" + ORDER.getHost() + ":" + ORDER.getMappedPort(9003) + path,
                body == null ? "GET" : "POST", body, token);
    }

    private static HttpResponse<String> send(String url, String method, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return HTTP.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String connectUrl(String path) {
        return "http://" + CONNECT.getHost() + ":" + CONNECT.getMappedPort(8083) + path;
    }

    private static KafkaConsumer<String, String> observer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "customer-refund-observer-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(properties);
    }

    private static Path backendRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("deployment/docker/connectors"))) {
                return path;
            }
            if (Files.isDirectory(path.resolve("ecommerce-msa/deployment/docker/connectors"))) {
                return path.resolve("ecommerce-msa");
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Could not locate backend reactor root");
    }
}
