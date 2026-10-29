/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.util.Collection;
import java.util.Map;

/**
 * Authored, content-free rejection at the Java argument-binding boundary.
 * The diagnostic describes representation rather than reproducing variables,
 * SQL, driver messages, or provider content. It applies before invoking the
 * GraphQL handler, so its effect marker is specifically {@code NOT_STARTED}.
 * It must not be used to describe failures after handler execution starts.
 */
public final class GraphQLArgumentBindingException extends IllegalArgumentException {

    /**
     * Creates a safe diagnostic for a rejected variable representation.
     *
     * @param actual rejected JSON-style value; only its broad type is reported
     */
    public GraphQLArgumentBindingException(Object actual) {
        super("{\"phase\":\"ARGUMENT_BINDING\",\"category\":\"InvalidArgumentType\","
                + "\"argument\":\"variables\",\"expected\":\"object\",\"received\":\""
                + valueType(actual) + "\",\"effects\":\"NOT_STARTED\"}");
    }

    /**
     * Requires the canonical JSON object without parsing strings as another format.
     * An absent or null variables argument remains supported by the tool contract;
     * explicit null entries within a supplied object remain unchanged.
     *
     * @param value non-null argument to be converted to the GraphQL variable map
     * @throws GraphQLArgumentBindingException if the argument is not an object map
     */
    public static void requireObject(Object value) {
        if (!(value instanceof Map<?, ?> map)
                || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new GraphQLArgumentBindingException(value);
        }
    }

    /**
     * Classifies only JSON-level types; class names and object text are not exposed.
     *
     * @param value rejected value
     * @return one stable representation type
     */
    private static String valueType(Object value) {
        if (value == null) { return "null"; }
        if (value instanceof CharSequence || value instanceof Character) { return "string"; }
        if (value instanceof Number) { return "number"; }
        if (value instanceof Boolean) { return "boolean"; }
        if (value instanceof Collection<?> || value.getClass().isArray()) { return "array"; }
        if (value instanceof Map<?, ?>) { return "object"; }
        return "unknown";
    }
}