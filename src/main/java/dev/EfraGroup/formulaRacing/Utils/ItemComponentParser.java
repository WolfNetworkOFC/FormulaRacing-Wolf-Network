package dev.EfraGroup.formulaRacing.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds an {@link ItemStack} from the same bracket syntax vanilla {@code /give}
 * accepts, so a track icon can be described with any component the item really
 * supports.
 *
 * <p>Examples that must work:
 * <pre>
 *   light[level=2]
 *   acacia_button[minecraft:item_model="1"]
 *   minecraft:diamond_sword[minecraft:custom_name='{"text":"F1"}',minecraft:enchantments={levels:[{id:"minecraft:sharpness",level:5}]}]
 * </pre>
 *
 * <p>Syntax that the item does NOT support must fail loudly instead of being
 * silently dropped, which is what vanilla does too: {@code bee_nest[minecraft:bees=]}
 * is rejected by {@link Bukkit#getUnsafe()} because {@code bees} is a valid
 * component with an invalid value.
 *
 * <p>Keys are routed to one of two places, mirroring vanilla:
 * <ul>
 *   <li><b>Data components</b> ({@code minecraft:item_model}, {@code custom_data},
 *       {@code enchantments}, ...) go through the item JSON, so every component the
 *       server knows about is supported with no per-item code here.</li>
 *   <li><b>Block state properties</b> ({@code level}, {@code facing}, {@code half},
 *       {@code waterlogged}, ...) are applied to the item's {@link BlockData}. They
 *       are matched by looking for the matching setter on the block data type, so
 *       they only apply to blocks that actually have the property — which is why
 *       {@code light[level=2]} works but {@code stone[level=2]} does not.</li>
 * </ul>
 */
public final class ItemComponentParser {

    /** Thrown for malformed syntax or for props the item does not support. */
    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    private final String src;
    private int pos;

    private ItemComponentParser(String src) {
        this.src = src;
    }

    /**
     * Parses the comma-separated {@code key=value} pairs that live inside the
     * brackets, e.g. {@code level=2,custom_model_data=7}.
     *
     * @return ordered map of raw parsed values (String, Number, Boolean, Map, List)
     */
    public static Map<String, Object> parseProps(String content) {
        if (content == null) {
            return new LinkedHashMap<>();
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return new LinkedHashMap<>();
        }
        ItemComponentParser parser = new ItemComponentParser(trimmed);
        Map<String, Object> props = parser.readPairs('\0');
        parser.skipWhitespace();
        if (parser.pos < parser.src.length()) {
            throw new ParseException("Unexpected '" + parser.src.charAt(parser.pos) + "' at position " + parser.pos);
        }
        return props;
    }

    /**
     * Builds the icon. Display name and lore are applied last, on the same
     * {@link ItemMeta} pass as the block state, because setting a block state
     * resets item deviations on 1.20.5+ and would otherwise drop them.
     *
     * @param material    base material
     * @param amount      stack size (>= 1)
     * @param content     bracket contents without the brackets, may be null/empty
     * @param displayName optional display name
     * @param lore        optional lore
     * @throws ParseException if the syntax is malformed or a prop is unsupported
     */
    public static ItemStack build(
        Material material,
        int amount,
        String content,
        String displayName,
        List<String> lore
    ) {
        Map<String, Object> props = parseProps(content);
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        if (props.isEmpty()) {
            return item;
        }

        // Bare props (level=2, facing=north) are block state properties; namespaced
        // ones (minecraft:item_model=...) are data components. The two are applied by
        // different mechanisms, so they are split here.
        Map<String, Object> blockStateProps = new LinkedHashMap<>();
        Map<String, Object> componentProps = new LinkedHashMap<>();
        BlockData blockData = material.isBlock() ? material.createBlockData() : null;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isDataComponent(key)) {
                componentProps.put(key, value);
                continue;
            }
            // Validation: a block only supports the properties it actually has, so
            // stone[level=2] is rejected while light[level=2] is accepted.
            if (blockData == null || !hasBlockStateProperty(blockData, key)) {
                throw new ParseException(
                    "Item " + material.name() + " does not support property or component '" + key + "'"
                );
            }
            blockStateProps.put(key, value);
        }

        if (!blockStateProps.isEmpty()) {
            // BlockDataMeta#setBlockData is the API that actually persists block state
            // on an item. BlockStateMeta does not: the documented behaviour since
            // 1.20.5 is that setting a block state resets the item's data deviations,
            // and the level kept reverting to its default in the client tooltip.
            final List<Map.Entry<String, Object>> entries = new ArrayList<>(blockStateProps.entrySet());
            final ItemStack target = item;
            target.editMeta(m -> {
                if (m instanceof BlockDataMeta dataMeta) {
                    BlockData data = material.createBlockData();
                    for (Map.Entry<String, Object> entry : entries) {
                        Method setter = findBlockStateSetter(data, entry.getKey());
                        if (setter == null) {
                            throw new ParseException(
                                "Block " + material.name() + " does not support property '" + entry.getKey() + "'"
                            );
                        }
                        try {
                            setter.invoke(data, coerce(entry.getValue(), setter.getParameterTypes()[0]));
                        } catch (ReflectiveOperationException e) {
                            throw new ParseException(
                                "Invalid value for '" + entry.getKey() + "': " + e.getMessage()
                            );
                        }
                    }
                    dataMeta.setBlockData(data);
                } else if (m instanceof BlockStateMeta blockMeta) {
                    // Fallback for servers without BlockDataMeta: mutate the state's own
                    // data and write it back in the same meta pass.
                    org.bukkit.block.BlockState state = blockMeta.getBlockState();
                    BlockData data = state.getBlockData();
                    for (Map.Entry<String, Object> entry : entries) {
                        Method setter = findBlockStateSetter(data, entry.getKey());
                        if (setter == null) {
                            throw new ParseException(
                                "Block " + material.name() + " does not support property '" + entry.getKey() + "'"
                            );
                        }
                        try {
                            setter.invoke(data, coerce(entry.getValue(), setter.getParameterTypes()[0]));
                        } catch (ReflectiveOperationException e) {
                            throw new ParseException(
                                "Invalid value for '" + entry.getKey() + "': " + e.getMessage()
                            );
                        }
                    }
                    state.setBlockData(data);
                    blockMeta.setBlockState(state);
                }
            });
        }

        if (!componentProps.isEmpty()) {
            // Data components go to the vanilla item argument parser via
            // UnsafeValues#modifyItemStack — the same entry point /give uses.
            StringBuilder components = new StringBuilder();
            for (Map.Entry<String, Object> entry : componentProps.entrySet()) {
                if (components.length() > 0) {
                    components.append(',');
                }
                components.append(entry.getKey()).append('=').append(renderValue(entry.getValue()));
            }
            String vanillaArgs = "minecraft:" + material.name().toLowerCase(Locale.ROOT) + "[" + components + "]";
            ItemStack modified;
            try {
                modified = Bukkit.getUnsafe().modifyItemStack(item, vanillaArgs);
            } catch (RuntimeException e) {
                throw new ParseException("Invalid item component: " + e.getMessage());
            }
            if (modified == null) {
                throw new ParseException("Server rejected the item component data");
            }
            item = modified;
        }

        if (displayName != null || lore != null) {
            item.editMeta(m -> {
                if (displayName != null) {
                    m.setDisplayName(displayName);
                }
                if (lore != null) {
                    m.setLore(lore);
                }
            });
        }
        return item;
    }

    /** Renders a parsed value as SNBT, quoting strings so spaces survive. */
    private static String renderValue(Object value) {
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(e.getKey()).append('=').append(renderValue(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(renderValue(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return String.valueOf(value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object coerce(Object value, Class<?> target) {
        if (target.isEnum()) {
            String cleaned = String.valueOf(value);
            if (cleaned.startsWith("minecraft:")) {
                cleaned = cleaned.substring(10);
            }
            try {
                return Enum.valueOf((Class<? extends Enum>) target, cleaned.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid value '" + cleaned + "' for " + target.getSimpleName());
            }
        }
        if (target == String.class) {
            return String.valueOf(value);
        }
        if (value instanceof Number n) {
            if (target == byte.class || target == Byte.class) return n.byteValue();
            if (target == short.class || target == Short.class) return n.shortValue();
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == long.class || target == Long.class) return n.longValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
        }
        if ((target == boolean.class || target == Boolean.class) && value instanceof Boolean b) {
            return b;
        }
throw new ParseException("Value " + value + " does not fit " + target.getSimpleName());
    }

    /**
     * A key is treated as a data component when it is namespaced. Vanilla block
     * state properties are never namespaced, so this cleanly separates
     * {@code level} (block state) from {@code minecraft:item_model} (component).
     */
    private static boolean isDataComponent(String key) {
        return key.indexOf(':') >= 0;
    }

    private static boolean hasBlockStateProperty(BlockData data, String key) {
        return findBlockStateSetter(data, key) != null;
    }

    /** Finds {@code setLevel}/{@code setWaterlogged}-style setter on the block data type. */
    private static Method findBlockStateSetter(BlockData data, String key) {
        String setterName = "set" + capitalize(key);
        Class<?> type = data.getClass();
        while (type != null) {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 1
                    && method.getName().equals(setterName)
                    && isSupportedParam(method.getParameterTypes()[0])) {
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isSupportedParam(Class<?> type) {
        return type == boolean.class || type == Boolean.class
            || type == int.class || type == Integer.class
            || type == byte.class || type == Byte.class
            || type == short.class || type == Short.class
            || type == long.class || type == Long.class
            || type == float.class || type == Float.class
            || type == double.class || type == Double.class
            || type == String.class
            || type.isEnum();
    }

    private static String capitalize(String key) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // SNBT-ish reader
    // ------------------------------------------------------------------

    private Map<String, Object> readPairs(char terminator) {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) == terminator || src.charAt(pos) == ']') {
                return map;
            }
            String key = readKey();
            skipWhitespace();
            // Vanilla brackets use '=' (light[level=2]); nested SNBT compounds use
            // ':' (enchantments={levels:[...]}). Both are accepted.
            if (pos < src.length() && (src.charAt(pos) == '=' || src.charAt(pos) == ':')) {
                pos++;
            } else {
                throw new ParseException("Expected '=' at position " + pos);
            }
            Object value = readValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            return map;
        }
    }

    private String readKey() {
        skipWhitespace();
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                pos++;
            } else if (c == '.' || c == ':') {
                // ':' is both a namespace separator (minecraft:item_model=1) and
                // the key/value separator of a nested compound (levels:[...]). Only
                // treat it as part of the key when an '=' follows before the value
                // would start, otherwise it belongs to the caller.
                if (c == ':' && !hasEqualsBeforeValueEnd(pos + 1)) {
                    break;
                }
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new ParseException("Expected a property name at position " + pos);
        }
        return src.substring(start, pos);
    }

    /**
     * True when the text between {@code from} and the end of this key/value token
     * contains an '=', i.e. the ':' we just saw is a namespace separator rather
     * than the SNBT key/value separator.
     */
    private boolean hasEqualsBeforeValueEnd(int from) {
        for (int i = from; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '=') {
                return true;
            }
            // End of the token without an '=' before any delimiter: not a namespace.
            if (c == ',' || c == '}' || c == ']' || c == '{' || c == '[') {
                return false;
            }
        }
        return false;
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new ParseException("Unexpected end of input, expected a value");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            pos++;
            Map<String, Object> map = readPairs('}');
            expect('}');
            return map;
        }
        if (c == '[') {
            return readListOrArray();
        }
        if (c == '"' || c == '\'') {
            return readQuoted();
        }
        return readUnquotedNumberOrWord();
    }

    private List<Object> readListOrArray() {
        expect('[');
        // Typed array: [B;1b,2b] / [I;...] / [L;...]
        skipWhitespace();
        if (pos < src.length() && (src.charAt(pos) == 'B' || src.charAt(pos) == 'I' || src.charAt(pos) == 'L')) {
            int save = pos;
            char type = src.charAt(pos);
            pos++;
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ';') {
                pos++;
                List<Object> arr = new ArrayList<>();
                while (true) {
                    skipWhitespace();
                    if (pos >= src.length()) {
                        throw new ParseException("Unterminated array");
                    }
                    if (src.charAt(pos) == ']') {
                        pos++;
                        break;
                    }
                    arr.add(readValue());
                    skipWhitespace();
                    if (pos < src.length() && src.charAt(pos) == ',') {
                        pos++;
                    }
                }
                if (type == 'B') {
                    List<Object> bytes = new ArrayList<>(arr.size());
                    for (Object o : arr) {
                        bytes.add(o instanceof Number n ? n.byteValue() : o);
                    }
                    return bytes;
                }
                return arr;
            }
            pos = save;
        }
        List<Object> list = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new ParseException("Unterminated list");
            }
            if (src.charAt(pos) == ']') {
                pos++;
                break;
            }
            list.add(readValue());
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
            }
        }
        return list;
    }

    private String readQuoted() {
        char quote = src.charAt(pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    default -> sb.append('\\').append(next);
                }
                pos += 2;
                continue;
            }
            if (c == quote) {
                pos++;
                return sb.toString();
            }
            sb.append(c);
            pos++;
        }
        throw new ParseException("Unterminated string");
    }

    private Object readUnquotedNumberOrWord() {
        int start = pos;
        while (pos < src.length() && ",]}:".indexOf(src.charAt(pos)) < 0 && src.charAt(pos) != ' ') {
            pos++;
        }
        String raw = src.substring(start, pos);
        if (raw.isEmpty()) {
            throw new ParseException("Expected a value at position " + start);
        }
        return parseScalar(raw);
    }

    /** Parses an unquoted SNBT scalar: booleans, typed numbers or a bare string. */
    private static Object parseScalar(String raw) {
        String s = raw.trim();
        if (s.equals("true")) {
            return Boolean.TRUE;
        }
        if (s.equals("false")) {
            return Boolean.FALSE;
        }
        char suffix = s.length() > 1 ? Character.toLowerCase(s.charAt(s.length() - 1)) : '\0';
        String body = suffix == 'b' || suffix == 's' || suffix == 'l' || suffix == 'f' || suffix == 'd'
            ? s.substring(0, s.length() - 1)
            : s;
        try {
            if (suffix == 'b') {
                return Byte.parseByte(body);
            }
            if (suffix == 's') {
                return Short.parseShort(body);
            }
            if (suffix == 'l') {
                return Long.parseLong(body);
            }
            if (suffix == 'f') {
                return Float.parseFloat(body);
            }
            if (suffix == 'd' || (body.indexOf('.') >= 0 && suffix == '\0')) {
                return Double.parseDouble(body);
            }
            return Integer.parseInt(body);
        } catch (NumberFormatException ignored) {
            // Not a number: fall through and treat it as a plain string.
            return s;
        }
    }

    private void expect(char c) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new ParseException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }
}