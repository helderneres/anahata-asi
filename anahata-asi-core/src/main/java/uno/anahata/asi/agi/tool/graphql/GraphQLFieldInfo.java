/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Describes one selected field for validation before resolver execution.
 * The names identify the schema field and its response alias; the arguments
 * have already been coerced by graphql-java. This is an internal validation
 * snapshot, not a public result or a generic execution plan.
 *
 * <p>The argument map is copied and made unmodifiable at its outer level.
 * Nested values are shared and are not guaranteed to be immutable.</p>
 *
 * @author anahata
 */
@Getter
public final class GraphQLFieldInfo {

    /**
     * Name of the containing schema type, or an empty name when unavailable.
     */
    private final String parentType;
    /**
     * Schema field name, independent of any response alias.
     */
    private final String fieldName;
    /**
     * Response key: the alias when present, otherwise the field name.
     */
    private final String responseName;
    /**
     * Unmodifiable outer snapshot of engine-coerced arguments, including nulls.
     */
    private final Map<String, Object> arguments;

    /**
     * Creates a field description with a shallow copy of its arguments.
     * Names and nested argument values are retained without validation or copying.
     *
     * @param parentType name of the containing schema type
     * @param fieldName name declared in the schema
     * @param responseName response alias or unaliased field name
     * @param arguments non-null coerced arguments; null values are preserved
     * @throws NullPointerException if arguments is null
     */
    public GraphQLFieldInfo(String parentType, String fieldName, String responseName, Map<String, Object> arguments) {
        this.parentType = parentType;
        this.fieldName = fieldName;
        this.responseName = responseName;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
