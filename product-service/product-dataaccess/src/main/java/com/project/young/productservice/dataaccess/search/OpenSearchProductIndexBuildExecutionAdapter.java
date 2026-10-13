package com.project.young.productservice.dataaccess.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildExecutionPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexBuildWorkPort.Work;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

import java.io.StringReader;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettings;
import com.project.young.productservice.dataaccess.config.ProductSearchIndexMappingProperties;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.LongConsumer;

public class OpenSearchProductIndexBuildExecutionAdapter implements ProductSearchIndexBuildExecutionPort {
    private final OpenSearchClient client;
    private final ObjectMapper json;
    private final ProductSearchInitialLoader loader;
    private final ProductSearchIndexVerificationReader reader;
    private final OpenSearchProductQueryBuilder queries;
    private final OpenSearchProductProperties properties;
    private final ProductSearchIndexMappingProperties mappingProperties;

    public OpenSearchProductIndexBuildExecutionAdapter(OpenSearchClient client, ObjectMapper json,
                                                       ProductSearchInitialLoader loader, ProductSearchIndexVerificationReader reader,
                                                       OpenSearchProductQueryBuilder queries, OpenSearchProductProperties properties,
                                                       ProductSearchIndexMappingProperties mappingProperties) {
        this.client = client;
        this.json = json;
        this.loader = loader;
        this.reader = reader;
        this.queries = queries;
        this.properties = properties;
        this.mappingProperties = mappingProperties;
    }

    @Override
    public void create(Work work) {
        try (var input = mappingProperties.resource().getInputStream()) {
            byte[] bytes = input.readAllBytes();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (work.version().mappingVersion() != 1 || !hash.equals(work.version().mappingHash())) {
                throw new IllegalStateException("Registered index mapping no longer matches this worker");
            }
            var definition = json.readTree(bytes);
            var response = client.indices().create(c -> c.index(work.version().indexName())
                    .settings(parse(definition.get("settings").toString(), IndexSettings._DESERIALIZER))
                    .mappings(parse(definition.get("mappings").toString(), TypeMapping._DESERIALIZER)));
            if (!response.acknowledged() || !Boolean.TRUE.equals(response.shardsAcknowledged())) {
                throw new IllegalStateException("Index creation was not fully acknowledged");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProductSearchIndexingException("Index creation failed", exception);
        }
    }

    @Override
    public void load(Work work, Runnable checkpoint, LongConsumer completedBatch) {
        loader.load(work.version().indexName(), work.job().batchSize(), checkpoint, completedBatch);
    }

    @Override
    public long verify(Work work, Runnable checkpoint) {
        try {
            String index = work.version().indexName();
            checkpoint.run();
            var refreshed = client.indices().refresh(r -> r.index(index));
            if (refreshed.shards().failed() > 0) {
                throw new IllegalStateException("Index refresh failed");
            }
            checkpoint.run();
            var count = client.count(c -> c.index(index));
            if (count.shards().failed() > 0) {
                throw new IllegalStateException("Index count failed");
            }
            var source = reader.read();
            var targetProperties = new OpenSearchProductProperties(properties.endpoint(), index,
                    properties.connectTimeoutMillis(), properties.responseTimeoutMillis(),
                    properties.queryTimeoutSeconds(), properties.requestBudgetMillis());
            var search = new OpenSearchProductSearchAdapter(client, queries, targetProperties);
            checkpoint.run();
            var browse = search.search(new ProductSearchCriteria(null, null, List.of(), null, null, PublicProductSort.NEWEST, 0, 1));
            if (browse.totalElements() != source.visibleCount() || !samePrice(browse.minPrice(), source.min())
                    || !samePrice(browse.maxPrice(), source.max())) {
                throw new IllegalStateException("Public catalog visibility or price validation failed");
            }
            var sample = source.sample();
            if (sample != null) {
                checkpoint.run();
                var stored = client.get(g -> g.index(index).id(sample.id()), OpenSearchProductDocument.class).source();
                if (stored == null || !sample.name().equals(stored.name()) || sample.categoryId() != stored.categoryId()
                        || !sample.categoryName().equals(stored.categoryName()) || !samePrice(sample.basePrice(), stored.basePrice())
                        || !java.util.Objects.equals(sample.brandKey(), stored.brandKey())
                        || !java.util.Objects.equals(sample.description(), stored.description())) {
                    throw new IllegalStateException("Catalog sample document validation failed");
                }
                checkpoint.run();
                var filtered = search.search(new ProductSearchCriteria(null, sample.categoryId(),
                        sample.brandKey() == null ? List.of() : List.of(sample.brandKey()), sample.basePrice(), sample.basePrice(),
                        PublicProductSort.NEWEST, 0, 1));
                if (filtered.totalElements() == 0
                        || (sample.brandKey() != null && filtered.brands().stream().noneMatch(b -> b.value().equals(sample.brandKey())))) {
                    throw new IllegalStateException("Category, brand or price filter validation failed");
                }
                if (sample.description() != null && !sample.description().isBlank()) {
                    checkpoint.run();
                    var tokens = client.indices().analyze(a -> a.index(index).field("description").text(sample.description())).tokens();
                    if (!tokens.isEmpty()) {
                        String token = tokens.getFirst().token();
                        checkpoint.run();
                        var text = client.count(c -> c.index(index).query(q -> q.bool(b -> b
                                .filter(f -> f.term(t -> t.field("id").value(FieldValue.of(sample.id()))))
                                .must(m -> m.match(t -> t.field("description").query(FieldValue.of(token)))))));
                        if (text.shards().failed() > 0 || text.count() != 1) {
                            throw new IllegalStateException("Description search validation failed");
                        }
                    }
                }
            }
            checkpoint.run();
            return count.count();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProductSearchIndexingException("Index verification failed", exception);
        }
    }

    private <T> T parse(String body, JsonpDeserializer<T> deserializer) {
        var mapper = new JacksonJsonpMapper(json);
        try (var parser = mapper.jsonProvider().createParser(new StringReader(body))) {
            return deserializer.deserialize(parser, mapper);
        }
    }

    private static boolean samePrice(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
