package com.project.young.productservice.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildJob;
import com.project.young.productservice.web.mapper.AdminProductSearchIndexResponseMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminProductSearchIndexContractTest {

    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidatorFactory() {
        factory.close();
    }

    @Test
    void buildRequest_withoutBatchSize_defaultsTo500AndRequiresWritePauseConfirmation() throws Exception {
        var request = new ObjectMapper().readValue("{\"catalogWritesPaused\":true}", AdminProductSearchIndexBuildRequest.class);
        assertThat(request.batchSize()).isEqualTo(500);
        assertThat(factory.getValidator().validate(request)).isEmpty();
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexBuildRequest(500, null))).isNotEmpty();
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexBuildRequest(500, false))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1001})
    void buildRequest_withInvalidBatchSize_isRejected(int size) {
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexBuildRequest(size, true))).isNotEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad key", "한글", "a/b"})
    void idempotencyHeader_withInvalidKey_isRejected(String key) {
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexRequestHeaders(key))).isNotEmpty();
    }

    @Test
    void activationRequest_forFirstIndex_acceptsNullExpectedVersionButRequiresPause() {
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexActivationRequest(null, true))).isEmpty();
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexActivationRequest(null, false))).isNotEmpty();
    }

    @Test
    void versionQuery_defaultsAndBounds_followContract() {
        var query = new AdminProductSearchIndexVersionQuery(null, null);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexVersionQuery(-1, 20))).isNotEmpty();
        assertThat(factory.getValidator().validate(new AdminProductSearchIndexVersionQuery(0, 101))).isNotEmpty();
    }

    @Test
    void queuedJobResponse_preservesNullableFieldsAndHidesIdempotencyKey() throws Exception {
        var job = ProductSearchIndexBuildJob.queue(UUID.randomUUID(), UUID.randomUUID(), "admin-sub", "request-secret", 500,
                Instant.parse("2026-10-12T00:00:00Z"));
        var mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var json = mapper.valueToTree(new AdminProductSearchIndexResponseMapper().toJobResponse(job));
        assertThat(json.path("status").asText()).isEqualTo("QUEUED");
        assertThat(json.path("progress").path("sourceProductCount").isNull()).isTrue();
        assertThat(json.path("startedAt").isNull()).isTrue();
        assertThat(json.path("finishedAt").isNull()).isTrue();
        assertThat(json.path("failure").isNull()).isTrue();
        assertThat(json.path("createdAt").asText()).isEqualTo("2026-10-12T00:00:00Z");
        assertThat(json.has("idempotencyKey")).isFalse();
    }

    @Test
    void failedJobResponse_includesSafeFailureCodeAndMessage() {
        var now = Instant.parse("2026-10-12T00:00:00Z");
        var job = ProductSearchIndexBuildJob.queue(UUID.randomUUID(), UUID.randomUUID(), "admin-sub", "request-1", 500, now)
                .fail("INVALID_DOCUMENT", "검색 문서 생성 실패", now);
        var response = new AdminProductSearchIndexResponseMapper().toJobResponse(job);
        assertThat(response.failure()).isEqualTo(new AdminProductSearchIndexBuildJobResponse.Failure("INVALID_DOCUMENT", "검색 문서 생성 실패"));
    }
}
