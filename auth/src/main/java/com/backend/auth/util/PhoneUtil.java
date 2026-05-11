package com.backend.auth.util;

// Utility to normalize Vietnamese phone numbers to E.164 format (+84XXXXXXXXX).
// This ensures "0901234567", "84901234567", "+84901234567" all map to the same
// DB record — prevents duplicate accounts from format differences.
public class PhoneUtil {

    private PhoneUtil() {}

    public static String normalize(String phone) {
        if (phone == null) return null;

        // Remove all spaces and dashes
        String cleaned = phone.replaceAll("[\\s\\-]", "");

        // Already in E.164 format
        if (cleaned.startsWith("+84")) {
            return cleaned;
        }

        // Starts with country code without +
        if (cleaned.startsWith("84") && cleaned.length() == 11) {
            return "+" + cleaned;
        }

        // Vietnamese local format: starts with 0
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            return "+84" + cleaned.substring(1);
        }

        // Return as-is for other formats (international numbers)
        return cleaned;
    }
}
