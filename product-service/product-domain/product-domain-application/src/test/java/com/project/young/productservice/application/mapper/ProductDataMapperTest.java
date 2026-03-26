package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductDataMapperTest {

    private ProductDataMapper productDataMapper;

    @BeforeEach
    void setUp() {
        productDataMapper = new ProductDataMapper();
    }

    @Test
    @DisplayName("toCreateProductResult: Product가 null이면 NullPointerException")
    void toCreateProductResult_NullProduct_ThrowsNpe() {
        assertThatThrownBy(() -> productDataMapper.toCreateProductResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Product cannot be null");
    }

    @Test
    @DisplayName("toCreateProductResult: Product ID가 null이면 NullPointerException")
    void toCreateProductResult_NullProductId_ThrowsNpe() {
        Product productWithoutId = Product.builder()
                .categoryId(null)
                .name("상품")
                .description("상품에 대한 상세 설명입니다. 충분히 깁니다.")
                .basePrice(new Money(new BigDecimal("10000")))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .brand("브랜드")
                .mainImageUrl("https://example.com/image.jpg")
                .build();

        assertThatThrownBy(() -> productDataMapper.toCreateProductResult(productWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Product ID cannot be null");
    }

    @Test
    @DisplayName("toCreateProductResult: 정상 매핑")
    void toCreateProductResult_Success() {
        UUID id = UUID.randomUUID();
        Product product = Product.reconstitute(
                new ProductId(id),
                null,
                "상품",
                "상품에 대한 상세 설명입니다. 충분히 깁니다.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        CreateProductResult result = productDataMapper.toCreateProductResult(product);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("상품");
    }

    @Test
    @DisplayName("toUpdateProductResult: Product가 null이면 NullPointerException")
    void toUpdateProductResult_NullProduct_ThrowsNpe() {
        assertThatThrownBy(() -> productDataMapper.toUpdateProductResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Product cannot be null");
    }

    @Test
    @DisplayName("toUpdateProductResult: Product ID가 null이면 NullPointerException")
    void toUpdateProductResult_NullProductId_ThrowsNpe() {
        Product productWithoutId = Product.builder()
                .categoryId(null)
                .name("상품")
                .description("상품에 대한 상세 설명입니다. 충분히 깁니다.")
                .basePrice(new Money(new BigDecimal("10000")))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .brand("브랜드")
                .mainImageUrl("https://example.com/image.jpg")
                .build();

        assertThatThrownBy(() -> productDataMapper.toUpdateProductResult(productWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Product ID cannot be null");
    }

    @Test
    @DisplayName("toUpdateProductResult: categoryId 없으면 null로 매핑")
    void toUpdateProductResult_WithoutCategoryId_Success() {
        UUID id = UUID.randomUUID();
        Product product = Product.reconstitute(
                new ProductId(id),
                null,
                "상품",
                "상품에 대한 상세 설명입니다. 충분히 깁니다.",
                new Money(new BigDecimal("10000")),
                ProductStatus.ACTIVE,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        UpdateProductResult result = productDataMapper.toUpdateProductResult(product);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.categoryId()).isNull();
        assertThat(result.name()).isEqualTo("상품");
        assertThat(result.description()).isEqualTo("상품에 대한 상세 설명입니다. 충분히 깁니다.");
        assertThat(result.basePrice()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(result.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(result.conditionType()).isEqualTo(ConditionType.NEW);
    }

    @Test
    @DisplayName("toUpdateProductResult: categoryId 있으면 값으로 매핑")
    void toUpdateProductResult_WithCategoryId_Success() {
        UUID id = UUID.randomUUID();
        CategoryId categoryId = new CategoryId(10L);

        Product product = Product.reconstitute(
                new ProductId(id),
                categoryId,
                "상품",
                "상품에 대한 상세 설명입니다. 충분히 깁니다.",
                new Money(new BigDecimal("10000")),
                ProductStatus.INACTIVE,
                ConditionType.USED,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        UpdateProductResult result = productDataMapper.toUpdateProductResult(product);

        assertThat(result.categoryId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(result.conditionType()).isEqualTo(ConditionType.USED);
    }

    @Test
    @DisplayName("toDeleteProductResult: Product가 null이면 NullPointerException")
    void toDeleteProductResult_NullProduct_ThrowsNpe() {
        assertThatThrownBy(() -> productDataMapper.toDeleteProductResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Product cannot be null");
    }

    @Test
    @DisplayName("toDeleteProductResult: 정상 매핑")
    void toDeleteProductResult_Success() {
        UUID id = UUID.randomUUID();
        Product product = Product.reconstitute(
                new ProductId(id),
                null,
                "삭제대상",
                "상품에 대한 상세 설명입니다. 충분히 깁니다.",
                new Money(new BigDecimal("10000")),
                ProductStatus.DELETED,
                ConditionType.NEW,
                "브랜드",
                "https://example.com/image.jpg",
                new ArrayList<>(),
                new ArrayList<>()
        );

        DeleteProductResult result = productDataMapper.toDeleteProductResult(product);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("삭제대상");
    }

    @Test
    @DisplayName("toProduct: command가 null이면 NullPointerException")
    void toProduct_NullCommand_ThrowsNpe() {
        assertThatThrownBy(() -> productDataMapper.toProduct(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("CreateProductCommand cannot be null");
    }

    @Test
    @DisplayName("toProduct: categoryId가 있으면 Product에 반영")
    void toProduct_WithCategoryId_Success() {
        CreateProductCommand command = CreateProductCommand.builder()
                .name("와이드핏 데님")
                .description("브랜드A의 와이드핏 데님 상세 설명입니다.")
                .basePrice(new BigDecimal("99000"))
                .brand("브랜드A")
                .mainImageUrl("https://example.com/image.jpg")
                .categoryId(1L)
                .conditionType(ConditionType.NEW)
                .status(ProductStatus.ACTIVE)
                .build();

        CategoryId categoryId = new CategoryId(1L);

        Product product = productDataMapper.toProduct(command, categoryId);

        assertThat(product).isNotNull();
        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isEqualTo("와이드핏 데님");
        assertThat(product.getCategoryId()).contains(categoryId);
        assertThat(product.getBasePrice()).isEqualTo(new Money(new BigDecimal("99000")));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getConditionType()).isEqualTo(ConditionType.NEW);
    }

    @Test
    @DisplayName("toProduct: categoryId가 null이면 카테고리 없는 상품으로 매핑")
    void toProduct_WithoutCategoryId_Success() {
        CreateProductCommand command = CreateProductCommand.builder()
                .name("카테고리없음")
                .description("상품에 대한 상세 설명입니다. 충분히 깁니다.")
                .basePrice(new BigDecimal("50000"))
                .brand("브랜드B")
                .mainImageUrl("https://example.com/image2.jpg")
                .categoryId(null)
                .conditionType(ConditionType.REFURBISHED)
                .status(ProductStatus.INACTIVE)
                .build();

        Product product = productDataMapper.toProduct(command, null);

        assertThat(product.getCategoryId()).isEmpty();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(product.getConditionType()).isEqualTo(ConditionType.REFURBISHED);
    }
}