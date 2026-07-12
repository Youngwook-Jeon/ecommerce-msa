package com.project.young.orderservice.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippingAddressTest {

    @Test
    @DisplayName("유효한 배송지를 생성한다")
    void createsValidAddress() {
        ShippingAddress address = new ShippingAddress(
                "Kim Young",
                "01012345678",
                "123 Main St",
                "Apt 4B",
                "Seoul",
                "04524",
                "KR"
        );

        assertThat(address.recipientName()).isEqualTo("Kim Young");
        assertThat(address.addressLine2()).isEqualTo("Apt 4B");
        assertThat(address.countryCode()).isEqualTo("KR");
    }

    @Test
    @DisplayName("recipientName이 blank이면 예외")
    void blankRecipientName_throws() {
        assertThatThrownBy(() -> new ShippingAddress(
                " ",
                "01012345678",
                "123 Main St",
                null,
                "Seoul",
                "04524",
                "KR"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipientName");
    }

    @Test
    @DisplayName("countryCode가 2글자가 아니면 예외")
    void invalidCountryCode_throws() {
        assertThatThrownBy(() -> new ShippingAddress(
                "Kim Young",
                "01012345678",
                "123 Main St",
                null,
                "Seoul",
                "04524",
                "KOR"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countryCode");
    }
}
