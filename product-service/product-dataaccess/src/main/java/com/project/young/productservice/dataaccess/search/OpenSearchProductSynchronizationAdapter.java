package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.application.port.output.ProductSearchIndexAliasPort;
import com.project.young.productservice.dataaccess.config.OpenSearchProductProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
public class OpenSearchProductSynchronizationAdapter implements ProductSearchSynchronizationPort {
    private final ProductSearchSynchronizationReader documents;
    private final ProductSearchSynchronizationTargetReader targets;
    private final OpenSearchProductBulkWriter writer;
    private final ProductSearchIndexAliasPort aliases;
    private final OpenSearchProductProperties properties;

    public OpenSearchProductSynchronizationAdapter(ProductSearchSynchronizationReader documents, ProductSearchSynchronizationTargetReader targets,
                                                   OpenSearchProductBulkWriter writer, ProductSearchIndexAliasPort aliases, OpenSearchProductProperties properties) {
        this.documents = documents;
        this.targets = targets;
        this.writer = writer;
        this.aliases = aliases;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void synchronize(UUID productId, Long eventRevision) {
        if (productId == null || (eventRevision != null && eventRevision < 1)) {
            throw new IllegalArgumentException("Product ID and a positive optional revision are required");
        }
        var target = targets.read();
        var document = documents.read(productId, eventRevision);
        if (!aliases.readAlias(properties.index()).matches(target.indexName())) {
            throw new ProductSearchSynchronizationDeferredException("Read alias and active metadata differ");
        }
        writer.verifyTarget(target.indexName());
        writer.write(target.indexName(), List.of(document), () -> {
            if (!target.equals(targets.read())) {
                throw new ProductSearchSynchronizationDeferredException("Active index changed during synchronization");
            }
        });
        if (!target.equals(targets.read()) || !aliases.readAlias(properties.index()).matches(target.indexName())) {
            throw new ProductSearchSynchronizationDeferredException("Active index changed after synchronization");
        }
        log.info("Product search synchronized: productId={}, revision={}, indexVersionId={}, index={}, documentReady={}",
                productId, document.catalogRevision(), target.id(), target.indexName(), document.documentReady());
    }
}
