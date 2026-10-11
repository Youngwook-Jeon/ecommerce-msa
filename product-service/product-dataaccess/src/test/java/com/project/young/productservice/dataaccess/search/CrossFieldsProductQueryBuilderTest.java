package com.project.young.productservice.dataaccess.search;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;

import static org.assertj.core.api.Assertions.assertThat;

class CrossFieldsProductQueryBuilderTest {

    @Test
    void matchesAllTokensAcrossWeightedFieldsAndBoostsNamePhrase() {
        var query = new CrossFieldsProductQueryBuilder().buildTextQuery("방수 이어폰").bool();
        assertThat(query.must()).hasSize(1);
        var text = query.must().getFirst().multiMatch();
        assertThat(text.query()).isEqualTo("방수 이어폰");
        assertThat(text.fields()).containsExactly("name^4", "brand^2", "description");
        assertThat(text.type()).isEqualTo(TextQueryType.CrossFields);
        assertThat(text.operator()).isEqualTo(Operator.And);
        assertThat(query.should()).hasSize(1);
        var phrase = query.should().getFirst().matchPhrase();
        assertThat(phrase.field()).isEqualTo("name");
        assertThat(phrase.query()).isEqualTo("방수 이어폰");
        assertThat(phrase.boost()).isEqualTo(3.0f);
    }
}
