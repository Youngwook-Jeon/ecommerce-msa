package com.project.young.productservice.dataaccess.search;

import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductSearchCategoryNameReaderTest {
    private final CategoryJpaRepository repository = mock(CategoryJpaRepository.class);
    private final ProductSearchCategoryNameReader reader = new ProductSearchCategoryNameReader(repository);

    @Test
    void emptyIds_skipsDatabaseQuery() {
        assertThat(reader.read(Set.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void ids_areResolvedByOneProjectionQuery() {
        var first = mock(CategoryJpaRepository.CategoryNameView.class);
        when(first.getId()).thenReturn(12L);
        when(first.getName()).thenReturn("현재 카테고리 이름");
        when(repository.findNamesByIdIn(Set.of(12L, 34L))).thenReturn(List.of(first));
        assertThat(reader.read(Set.of(12L, 34L))).isEqualTo(Map.of(12L, "현재 카테고리 이름"));
        verify(repository).findNamesByIdIn(Set.of(12L, 34L));
    }
}
