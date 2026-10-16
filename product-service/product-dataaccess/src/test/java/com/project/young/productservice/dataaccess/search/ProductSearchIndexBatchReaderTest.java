package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;

import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.mapper.OpenSearchProductDocumentMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductSearchIndexBatchReaderTest {

    @Test
    void mapsEntireBatchAndUsesKeysetInsteadOfOffsetForNextBatch() {
        var repository = mock(ProductJpaRepository.class);
        var mapper = mock(OpenSearchProductDocumentMapper.class);
        var revisions = mock(ProductCatalogRevisionStore.class);
        var reader = new ProductSearchIndexBatchReader(repository, mapper, revisions);
        var entity = new ProductEntity();
        entity.setId(UUID.randomUUID());
        when(revisions.read(List.of(entity.getId()))).thenReturn(java.util.Map.of(entity.getId(), 1L));
        var document = ProductSearchInitialLoaderTest.document();
        var after = UUID.randomUUID();
        when(repository.findFirstSearchIndexBatch(PageRequest.of(0, 500))).thenReturn(List.of(entity));
        when(repository.findNextSearchIndexBatch(after, PageRequest.of(0, 500))).thenReturn(List.of(entity));
        when(mapper.toDocument(entity, 1)).thenReturn(document);
        assertThat(reader.read(null, 500)).containsExactly(document);
        assertThat(reader.read(after, 500)).containsExactly(document);
        verify(mapper, times(2)).toDocument(entity, 1);
    }
}
