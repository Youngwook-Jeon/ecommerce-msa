package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.ProductSearchResult;
import com.project.young.productservice.application.exception.ProductSearchCategoryNotFoundException;
import com.project.young.productservice.application.exception.ProductSearchRequestException;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private CategoryReadRepository categoryReadRepository;
    @Mock
    private ProductSearchPort searchPort;
    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchService(new ProductSearchQueryValidator(), categoryReadRepository, searchPort);
    }

    @Test
    void searchesAllCategoriesWithNormalizedCriteria() {
        when(searchPort.search(any())).thenReturn(result(25));
        var page = service.search(new ProductSearchQuery(" 무선\t이어폰 ", null, List.of("APPLE", "Apple"),
                null, null, null, null, null));
        var captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(searchPort).search(captor.capture());
        assertThat(captor.getValue().q()).isEqualTo("무선 이어폰");
        assertThat(captor.getValue().brands()).containsExactly("apple");
        assertThat(captor.getValue().sort()).isEqualTo(PublicProductSort.RELEVANCE);
        assertThat(page.totalPages()).isEqualTo(2);
        verifyNoInteractions(categoryReadRepository);
    }

    @Test
    void activeCategoryIsCheckedBeforeSearching() {
        when(categoryReadRepository.existsActiveById(12)).thenReturn(true);
        when(searchPort.search(any())).thenReturn(result(0));
        var page = service.search(query(12L, 0));
        assertThat(page.criteria().categoryId()).isEqualTo(12);
        assertThat(page.criteria().sort()).isEqualTo(PublicProductSort.NEWEST);
        verify(categoryReadRepository).existsActiveById(12);
    }

    @Test
    void missingOrInactiveCategoryDoesNotReachSearchEngine() {
        assertThatThrownBy(() -> service.search(query(12L, 0)))
                .isInstanceOf(ProductSearchCategoryNotFoundException.class);
        verifyNoInteractions(searchPort);
    }

    @Test
    void invalidRequestDoesNotReachEitherPort() {
        assertThatThrownBy(() -> service.search(query(12L, -1)))
                .isInstanceOf(ProductSearchRequestException.class);
        verifyNoInteractions(categoryReadRepository, searchPort);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "24,1", "25,2", "9223372036854775807,384307168202282326"})
    void calculatesTotalPagesWithoutOverflow(long total, long expectedPages) {
        when(searchPort.search(any())).thenReturn(result(total));
        assertThat(service.search(query(null, 0)).totalPages()).isEqualTo(expectedPages);
    }

    @Test
    void preservesEmptyOutOfRangePageAndGlobalTotals() {
        when(searchPort.search(any())).thenReturn(result(1));
        var page = service.search(query(null, 2));
        assertThat(page.result().content()).isEmpty();
        assertThat(page.result().totalElements()).isEqualTo(1);
        assertThat(page.criteria().page()).isEqualTo(2);
    }

    @Test
    void propagatesSearchFailureRatherThanReturningEmptyResults() {
        var failure = new ProductSearchUnavailableException("Index query timed out");
        when(searchPort.search(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.search(query(null, 0))).isSameAs(failure);
    }

    private static ProductSearchQuery query(Long categoryId, int page) {
        return new ProductSearchQuery(null, categoryId, List.of(), null, null, null, page, 24);
    }

    private static ProductSearchResult result(long total) {
        return new ProductSearchResult(List.of(), total, List.of(), false, null, null);
    }
}
