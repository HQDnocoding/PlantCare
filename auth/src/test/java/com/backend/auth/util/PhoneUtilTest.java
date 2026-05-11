package com.backend.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// Parameterized tests — one test method covers many input formats.
// Much cleaner than writing 6 separate test methods for the same logic.
class PhoneUtilTest {

    @ParameterizedTest(name = "input [{0}] should normalize to [{1}]")
    @CsvSource({
            // Vietnamese local format
            "0901234567,          +84901234567",
            // Already E.164
            "+84901234567,        +84901234567",
            // Country code without +
            "84901234567,         +84901234567",
            // With spaces — should be stripped
            "0901 234 567,        +84901234567",
            // With dashes — should be stripped
            "090-123-4567,        +84901234567",
    })
    @DisplayName("normalize() — all Vietnamese phone formats map to E.164")
    void normalize_variousFormats(String input, String expected) {
        assertThat(PhoneUtil.normalize(input.trim())).isEqualTo(expected.trim());
    }

    @ParameterizedTest(name = "null or unrecognized [{0}] returned as-is")
    @CsvSource({
            // International number not matching VN patterns — return as-is
            "+12025550123, +12025550123",
    })
    @DisplayName("normalize() — non-VN numbers returned unchanged")
    void normalize_international(String input, String expected) {
        assertThat(PhoneUtil.normalize(input.trim())).isEqualTo(expected.trim());
    }
}

