package com.project.young.productservice.dataaccess.catalog;

import com.project.young.productservice.application.exception.ProductVersionConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class ProductCatalogRevisionStoreTest {
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private ProductCatalogRevisionStore revisions;

    @Test
    @SuppressWarnings("unchecked")
    void advance_matchingVersion_returnsNextRevision() {
        doReturn(List.of(8L)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertThat(revisions.advance(UUID.randomUUID(), 7)).isEqualTo(8);
    }

    @Test
    void advance_conflictingVersion_rejectsWithoutRetry() {
        assertThatThrownBy(() -> revisions.advance(UUID.randomUUID(), 7)).isInstanceOf(ProductVersionConflictException.class);
    }

    @Test
    void advance_invalidExpectedVersion_isRejected() {
        assertThatThrownBy(() -> revisions.advance(UUID.randomUUID(), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
