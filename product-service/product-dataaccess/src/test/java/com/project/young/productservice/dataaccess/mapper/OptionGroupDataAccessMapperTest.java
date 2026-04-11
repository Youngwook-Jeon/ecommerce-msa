package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OptionGroupDataAccessMapperTest {

    private final OptionGroupDataAccessMapper mapper = new OptionGroupDataAccessMapper();

    @Test
    @DisplayName("domainToEntity: 도메인을 엔티티로 올바르게 매핑한다")
    void domainToEntity_Success() {
        UUID groupId = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();

        OptionValue optionValue = OptionValue.reconstitute(
                new OptionValueId(valueId),
                "LARGE",
                "라지",
                2,
                OptionStatus.ACTIVE
        );
        OptionGroup domain = OptionGroup.reconstitute(
                new OptionGroupId(groupId),
                "SIZE",
                "사이즈",
                OptionStatus.INACTIVE,
                List.of(optionValue)
        );

        OptionGroupEntity entity = mapper.domainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(groupId);
        assertThat(entity.getName()).isEqualTo("SIZE");
        assertThat(entity.getDisplayName()).isEqualTo("사이즈");
        assertThat(entity.getStatus()).isEqualTo(OptionStatusEntity.INACTIVE);
        assertThat(entity.getOptionValues()).hasSize(1);
        OptionValueEntity valEntity = entity.getOptionValues().get(0);
        assertThat(valEntity.getId()).isEqualTo(valueId);
        assertThat(valEntity.getValue()).isEqualTo("LARGE");
        assertThat(valEntity.getOptionGroup()).isSameAs(entity);
    }

    @Test
    @DisplayName("domainToEntity: 도메인이 null이면 NullPointerException")
    void domainToEntity_NullDomain_ThrowsNpe() {
        assertThatThrownBy(() -> mapper.domainToEntity(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("domain must not be null");
    }

    @Test
    @DisplayName("domainToEntity: 도메인 ID가 null이면 NullPointerException")
    void domainToEntity_NullDomainId_ThrowsNpe() {
        OptionGroup domain = OptionGroup.builder()
                .name("COLOR")
                .displayName("색상")
                .build();

        assertThatThrownBy(() -> mapper.domainToEntity(domain))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("domain ID must not be null");
    }

    @Test
    @DisplayName("optionValueDomainToEntity: OptionValue 도메인을 엔티티로 매핑한다")
    void optionValueDomainToEntity_Success() {
        UUID valueId = UUID.randomUUID();
        OptionValue domain = OptionValue.reconstitute(
                new OptionValueId(valueId),
                "BLUE",
                "파랑",
                0,
                OptionStatus.DELETED
        );

        OptionValueEntity entity = mapper.optionValueDomainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(valueId);
        assertThat(entity.getValue()).isEqualTo("BLUE");
        assertThat(entity.getDisplayName()).isEqualTo("파랑");
        assertThat(entity.getSortOrder()).isZero();
        assertThat(entity.getStatus()).isEqualTo(OptionStatusEntity.DELETED);
    }

    @Test
    @DisplayName("toEntityStatus / toDomainStatus: ACTIVE ↔ ACTIVE")
    void statusRoundTrip_Active() {
        assertThat(mapper.toEntityStatus(OptionStatus.ACTIVE)).isEqualTo(OptionStatusEntity.ACTIVE);
        assertThat(mapper.toDomainStatus(OptionStatusEntity.ACTIVE)).isEqualTo(OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("toEntityStatus: null이면 NullPointerException")
    void toEntityStatus_Null_ThrowsNpe() {
        assertThatThrownBy(() -> mapper.toEntityStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("domainStatus must not be null");
    }

    @Test
    @DisplayName("toDomainStatus: null이면 NullPointerException")
    void toDomainStatus_Null_ThrowsNpe() {
        assertThatThrownBy(() -> mapper.toDomainStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entityStatus must not be null");
    }
}
