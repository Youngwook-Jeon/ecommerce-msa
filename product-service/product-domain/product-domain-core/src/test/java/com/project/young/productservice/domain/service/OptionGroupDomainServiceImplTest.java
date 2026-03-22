package com.project.young.productservice.domain.service;

import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionGroupDomainServiceImplTest {

    @Mock
    private OptionGroupRepository optionGroupRepository;

    @InjectMocks
    private OptionGroupDomainServiceImpl optionGroupDomainService;

    @Nested
    @DisplayName("isValidOptionGroupName")
    class IsValidOptionGroupNameTests {

        @Test
        @DisplayName("name이 null이면 OptionDomainException")
        void nullName_Throws() {
            assertThatThrownBy(() -> optionGroupDomainService.isValidOptionGroupName(null))
                    .isInstanceOf(OptionDomainException.class)
                    .hasMessageContaining("Option group name must not be null");

            verifyNoInteractions(optionGroupRepository);
        }

        @Test
        @DisplayName("동일 이름이 이미 존재하면 false")
        void nameExists_ReturnsFalse() {
            when(optionGroupRepository.existsByName("COLOR")).thenReturn(true);

            assertThat(optionGroupDomainService.isValidOptionGroupName("COLOR")).isFalse();

            verify(optionGroupRepository).existsByName("COLOR");
        }

        @Test
        @DisplayName("이름이 없으면 true")
        void nameNotExists_ReturnsTrue() {
            when(optionGroupRepository.existsByName("SIZE")).thenReturn(false);

            assertThat(optionGroupDomainService.isValidOptionGroupName("SIZE")).isTrue();

            verify(optionGroupRepository).existsByName("SIZE");
        }
    }
}
