package com.project.young.productservice.application.dto.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicProductSortTest {

    @Test
    void fromApiValue_defaultsToNewestWhenBlank() {
        assertThat(PublicProductSort.fromApiValue(null)).isEqualTo(PublicProductSort.NEWEST);
        assertThat(PublicProductSort.fromApiValue("  ")).isEqualTo(PublicProductSort.NEWEST);
    }

    @Test
    void fromApiValue_parsesKnownValues() {
        assertThat(PublicProductSort.fromApiValue("price_asc")).isEqualTo(PublicProductSort.PRICE_ASC);
        assertThat(PublicProductSort.fromApiValue("RELEVANCE")).isEqualTo(PublicProductSort.RELEVANCE);
    }

    @Test
    void fromApiValue_rejectsUnknown() {
        assertThatThrownBy(() -> PublicProductSort.fromApiValue("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
