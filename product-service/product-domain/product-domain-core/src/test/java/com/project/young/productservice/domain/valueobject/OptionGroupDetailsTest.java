package com.project.young.productservice.domain.valueobject;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.domain.exception.OptionDomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionGroupDetailsTest {
    private final OptionGroupId id = new OptionGroupId(UUID.randomUUID());

    @Test
    void information_changes_preserve_identity_and_original_state() {
        var original = new OptionGroupDetails(id, "RAM", "Memory", OptionStatus.ACTIVE, 7L);

        var updated = original.rename("RAM_CAPACITY").changeDisplayName("Memory capacity").changeStatus(OptionStatus.INACTIVE);

        assertThat(updated).isEqualTo(new OptionGroupDetails(id, "RAM_CAPACITY", "Memory capacity", OptionStatus.INACTIVE, 7L));
        assertThat(original).isEqualTo(new OptionGroupDetails(id, "RAM", "Memory", OptionStatus.ACTIVE, 7L));
    }

    @Test
    void deleted_group_details_reject_changes() {
        var deleted = new OptionGroupDetails(id, "RAM", "Memory", OptionStatus.DELETED, 0L);
        assertThatThrownBy(() -> deleted.rename("NEW_RAM")).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> deleted.changeDisplayName("New memory")).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> deleted.changeStatus(OptionStatus.ACTIVE)).isInstanceOf(OptionDomainException.class);
    }

    @Test
    void information_changes_enforce_existing_group_validation_rules() {
        var details = new OptionGroupDetails(id, "RAM", "Memory", OptionStatus.ACTIVE, 0L);
        assertThatThrownBy(() -> details.rename(" ")).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> details.changeDisplayName("x".repeat(101))).isInstanceOf(OptionDomainException.class);
        assertThatThrownBy(() -> details.changeStatus(null)).isInstanceOf(OptionDomainException.class);
    }
}
