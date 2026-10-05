package dev.EfraGroup.formulaRacing.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemComponentParserTest {

    @Test
    @DisplayName("Parses a simple block state property")
    void parsesSimpleProperty() {
        Map<String, Object> props = ItemComponentParser.parseProps("level=2");
        assertEquals(1, props.size());
        assertEquals(2, props.get("level"));
    }

    @Test
    @DisplayName("Parses multiple properties in order")
    void parsesMultipleProperties() {
        Map<String, Object> props = ItemComponentParser.parseProps("level=2,waterlogged=true,facing=north");
        assertEquals(3, props.size());
        assertEquals(2, props.get("level"));
        assertEquals(Boolean.TRUE, props.get("waterlogged"));
        assertEquals("north", props.get("facing"));
    }

    @Test
    @DisplayName("Parses a quoted component value keeping the quotes out")
    void parsesQuotedValue() {
        Map<String, Object> props = ItemComponentParser.parseProps("minecraft:item_model=\"1\"");
        assertEquals("1", props.get("minecraft:item_model"));
    }

    @Test
    @DisplayName("Parses a quoted value with spaces and nested braces")
    void parsesComplexQuotedValue() {
        Map<String, Object> props = ItemComponentParser.parseProps(
            "minecraft:custom_name='{\"text\":\"Hello World\"}'"
        );
        assertEquals("{\"text\":\"Hello World\"}", props.get("minecraft:custom_name"));
    }

    @Test
    @DisplayName("Parses a nested SNBT compound")
    void parsesNestedCompound() {
        Map<String, Object> props = ItemComponentParser.parseProps(
            "minecraft:enchantments={levels:[{id:\"minecraft:sharpness\",level:5}]}"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> ench = (Map<String, Object>) props.get("minecraft:enchantments");
        assertTrue(ench.get("levels") instanceof List);
    }

    @Test
    @DisplayName("Parses a nested list of compounds")
    void parsesListOfCompounds() {
        Map<String, Object> props = ItemComponentParser.parseProps(
            "minecraft:enchantments={levels:[{id:\"minecraft:sharpness\",level:5}]}"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> ench = (Map<String, Object>) props.get("minecraft:enchantments");
        assertTrue(ench.get("levels") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> levels = (List<Object>) ench.get("levels");
        assertEquals(1, levels.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) levels.get(0);
        assertEquals("minecraft:sharpness", first.get("id"));
        assertEquals(5, first.get("level"));
    }

    @Test
    @DisplayName("Parses typed SNBT numbers keeping their suffix")
    void parsesTypedNumbers() {
        Map<String, Object> props = ItemComponentParser.parseProps("a=1b,b=2s,c=3l,d=4.5f,e=6.5d");
        assertEquals((byte) 1, props.get("a"));
        assertEquals((short) 2, props.get("b"));
        assertEquals(3L, props.get("c"));
        assertEquals(4.5f, props.get("d"));
        assertEquals(6.5d, props.get("e"));
    }

    @Test
    @DisplayName("Parses a bare string value")
    void parsesBareString() {
        Map<String, Object> props = ItemComponentParser.parseProps("minecraft:custom_name=Hello");
        assertEquals("Hello", props.get("minecraft:custom_name"));
    }

    @Test
    @DisplayName("Empty content yields no properties")
    void parsesEmptyContent() {
        assertTrue(ItemComponentParser.parseProps("").isEmpty());
        assertTrue(ItemComponentParser.parseProps(null).isEmpty());
        assertTrue(ItemComponentParser.parseProps("   ").isEmpty());
    }

    @Test
    @DisplayName("Rejects an unterminated string")
    void rejectsUnterminatedString() {
        assertThrows(ItemComponentParser.ParseException.class, () -> {
            ItemComponentParser.parseProps("minecraft:item_model=\"1");
        });
    }

    @Test
    @DisplayName("Rejects a missing value")
    void rejectsMissingValue() {
        assertThrows(ItemComponentParser.ParseException.class, () -> {
            ItemComponentParser.parseProps("level=");
        });
    }

    @Test
    @DisplayName("Rejects a malformed key")
    void rejectsMalformedKey() {
        assertThrows(ItemComponentParser.ParseException.class, () -> {
            ItemComponentParser.parseProps("=2");
        });
    }
}