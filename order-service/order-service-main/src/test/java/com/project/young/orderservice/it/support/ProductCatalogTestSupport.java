package com.project.young.orderservice.it.support;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public final class ProductCatalogTestSupport {

    private static final String CATALOG_SEARCH_URL = "http://catalog.test/public/catalog/cart-lines/search";

    private ProductCatalogTestSupport() {
    }

    public static void stubCatalogLines(MockRestServiceServer server, CatalogLineStub... lines) {
        stubCatalogLines(server, ExpectedCount.manyTimes(), lines);
    }

    public static void stubCatalogLines(
            MockRestServiceServer server,
            ExpectedCount expectedCount,
            CatalogLineStub... lines
    ) {
        String linesJson = Arrays.stream(lines)
                .map(ProductCatalogTestSupport::toLineJson)
                .collect(Collectors.joining(","));

        server.expect(expectedCount, requestTo(CATALOG_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"lines":[%s]}
                        """.formatted(linesJson), MediaType.APPLICATION_JSON));
    }

    private static String toLineJson(CatalogLineStub line) {
        return """
                {
                  "productId": "%s",
                  "productVariantId": "%s",
                  "productName": "%s",
                  "brand": "Brand",
                  "sku": "SKU-1",
                  "imageUrl": "https://img.test/product",
                  "unitPrice": %s,
                  "purchasable": %s,
                  "stockQuantity": %d,
                  "variantOptions": []
                }
                """.formatted(
                line.productId(),
                line.variantId(),
                line.productName(),
                line.unitPrice().toPlainString(),
                line.purchasable(),
                line.stockQuantity()
        );
    }

    public record CatalogLineStub(
            UUID productId,
            UUID variantId,
            String productName,
            BigDecimal unitPrice,
            int stockQuantity,
            boolean purchasable
    ) {
        public static CatalogLineStub available(
                UUID productId,
                UUID variantId,
                String productName,
                BigDecimal unitPrice,
                int stockQuantity
        ) {
            return new CatalogLineStub(productId, variantId, productName, unitPrice, stockQuantity, true);
        }
    }
}
