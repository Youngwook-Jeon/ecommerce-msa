package com.project.young.productservice.dataaccess.search;

import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;

/** Matches keyword tokens across catalog fields and boosts phrases in product names. */
public class CrossFieldsProductQueryBuilder extends OpenSearchProductQueryBuilder {

    @Override
    protected Query buildTextQuery(String keyword) {
        return Query.of(q -> q.bool(b -> b
                .must(m -> m.multiMatch(text -> text.query(keyword).fields("name^4", "brand^2", "description")
                        .type(TextQueryType.CrossFields).operator(Operator.And)))
                .should(s -> s.matchPhrase(phrase -> phrase.field("name").query(keyword).boost(3.0f)))));
    }
}
