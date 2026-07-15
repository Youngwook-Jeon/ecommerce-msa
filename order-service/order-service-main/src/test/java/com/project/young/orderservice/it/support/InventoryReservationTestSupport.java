package com.project.young.orderservice.it.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public final class InventoryReservationTestSupport {

    private static final String RESERVE_URL = "http://inventory.test/internal/inventory/reservations";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InventoryReservationTestSupport() {
    }

    public static void stubReserveSuccess(MockRestServiceServer server) {
        stubReserveSuccess(server, ExpectedCount.manyTimes());
    }

    public static void stubReserveSuccess(MockRestServiceServer server, ExpectedCount expectedCount) {
        server.expect(expectedCount, requestTo(RESERVE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(echoReserveSuccess());
    }

    public static void stubReleaseSuccess(MockRestServiceServer server) {
        stubReleaseSuccess(server, ExpectedCount.manyTimes());
    }

    public static void stubReleaseSuccess(MockRestServiceServer server, ExpectedCount expectedCount) {
        server.expect(expectedCount, requestTo(org.hamcrest.Matchers.startsWith(
                        "http://inventory.test/internal/inventory/reservations/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    /**
     * Echoes request checkoutId/lines so OrderApplicationService response validation passes.
     */
    private static ResponseCreator echoReserveSuccess() {
        return request -> {
            JsonNode body = OBJECT_MAPPER.readTree(request.getBody());
            String checkoutId = body.path("checkoutId").asText();
            Instant expiresAt = Instant.now().plusSeconds(900);

            String linesJson = StreamSupport.stream(body.path("lines").spliterator(), false)
                    .map(line -> """
                            {
                              "reservationId": "%s",
                              "productVariantId": "%s",
                              "quantity": %d,
                              "status": "ACTIVE"
                            }
                            """.formatted(
                            UUID.randomUUID(),
                            line.path("productVariantId").asText(),
                            line.path("quantity").asInt()
                    ))
                    .collect(Collectors.joining(","));

            String responseBody = """
                    {
                      "checkoutId": "%s",
                      "expiresAt": "%s",
                      "reusedExisting": false,
                      "lines": [%s]
                    }
                    """.formatted(checkoutId, expiresAt, linesJson);

            return withSuccess(responseBody, MediaType.APPLICATION_JSON).createResponse(request);
        };
    }
}
