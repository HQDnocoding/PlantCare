package com.backend.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdFilter Tests")
class CorrelationIdFilterTest {

    private CorrelationIdFilter correlationIdFilter;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
    }

    @Nested
    @DisplayName("Filter order")
    class FilterOrderTests {

        @Test
        @DisplayName("Should have highest precedence order")
        void getOrder_returnsHighestPrecedence() {
            // When
            int order = correlationIdFilter.getOrder();

            // Then
            assertThat(order).isEqualTo(Integer.MIN_VALUE);
        }
    }
}