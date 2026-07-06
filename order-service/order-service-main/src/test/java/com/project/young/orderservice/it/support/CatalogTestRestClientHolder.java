package com.project.young.orderservice.it.support;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public final class CatalogTestRestClientHolder {

    private final RestClient restClient;
    private final MockRestServiceServer mockServer;

    private CatalogTestRestClientHolder(RestClient restClient, MockRestServiceServer mockServer) {
        this.restClient = restClient;
        this.mockServer = mockServer;
    }

    public static CatalogTestRestClientHolder create() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new CatalogTestRestClientHolder(builder.build(), server);
    }

    public RestClient restClient() {
        return restClient;
    }

    public MockRestServiceServer mockServer() {
        return mockServer;
    }
}
