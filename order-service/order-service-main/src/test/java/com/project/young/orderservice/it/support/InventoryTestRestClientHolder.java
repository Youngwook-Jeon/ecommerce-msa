package com.project.young.orderservice.it.support;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public final class InventoryTestRestClientHolder {

    private final RestClient restClient;
    private final MockRestServiceServer mockServer;

    private InventoryTestRestClientHolder(RestClient restClient, MockRestServiceServer mockServer) {
        this.restClient = restClient;
        this.mockServer = mockServer;
    }

    public static InventoryTestRestClientHolder create() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new InventoryTestRestClientHolder(builder.build(), server);
    }

    public RestClient restClient() {
        return restClient;
    }

    public MockRestServiceServer mockServer() {
        return mockServer;
    }
}
