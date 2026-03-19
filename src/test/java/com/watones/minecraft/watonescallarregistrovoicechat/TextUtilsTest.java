package com.watones.minecraft.watonescallarregistrovoicechat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextUtilsTest {

    @Test
    void parseColorSupportsHexAndIntegerValues() {
        assertEquals(0xFF0044, TextUtils.parseColor("#FF0044"));
        assertEquals(255, TextUtils.parseColor("255"));
    }

    @Test
    void parseColorReturnsNullForInvalidInput() {
        assertNull(TextUtils.parseColor(null));
        assertNull(TextUtils.parseColor(""));
        assertNull(TextUtils.parseColor("nope"));
    }

    @Test
    void applyTemplateReplacesKnownPlaceholders() {
        String template = "{action}:{target}:{missing}";
        String result = TextUtils.applyTemplate(template, Map.of(
                "action", "MUTE",
                "target", "Steve"
        ));

        assertEquals("MUTE:Steve:{missing}", result);
    }

    @Test
    void toJsonEscapesControlCharacters() {
        String value = "\"line\"\n\tend";
        assertEquals("\"\\\"line\\\"\\n\\tend\"", TextUtils.toJson(value));
    }
}
