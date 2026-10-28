/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores JSON-compatible GraphQL variable values without omitting nulls.
 * The local Jackson serializer preserves the distinction between an absent
 * variable and an explicitly null variable, without changing the shared
 * mapper's policy for ordinary maps.
 *
 * <p>This is a mutable {@link LinkedHashMap}, not an immutable snapshot or a
 * thread-safe container. Constructors do not validate JSON compatibility.
 * The map-copy constructor copies entries only; nested values remain shared.
 * Use {@link GraphQLToolkit#copyVariables(Object)} when conversion of incoming
 * JSON-style values is required.</p>
 */
@JsonSerialize(using = GraphQLVariables.VariablesSerializer.class)
public final class GraphQLVariables extends LinkedHashMap<String, Object> {

    /**
     * Serialization identifier for this map specialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an empty mutable variable map.
     * The no-argument constructor also permits initialization by the ordinary
     * Kryo map serializer used for persistence of the current model.
     */
    public GraphQLVariables() {
        super();
    }

    /**
     * Creates a mutable map containing the supplied entries in iteration order.
     * Nested maps and lists are not copied, and explicit null values are retained.
     *
     * @param variables non-null source map of variable names and values
     * @throws NullPointerException if variables is null
     */
    public GraphQLVariables(Map<String, Object> variables) {
        super(variables);
    }

    /**
     * Serializes this map's JSON-style values, retaining null entries recursively.
     * It is selected only for {@link GraphQLVariables}; ordinary maps keep their
     * existing mapper policy. Map keys must be strings and values must be suitable
     * for the configured Jackson provider.
     */
    public static final class VariablesSerializer extends StdSerializer<GraphQLVariables> {

        /**
         * Creates the serializer for the concrete variable-map type.
         */
        public VariablesSerializer() {
            super(GraphQLVariables.class);
        }

        /**
         * Writes the variable map with explicit nulls in nested maps and lists.
         *
         * @param variables variable map to serialize
         * @param generator destination JSON generator, owned by the caller
         * @param provider Jackson provider for values other than maps, lists or null
         * @throws IOException if the generator or a delegated serializer cannot write
         */
        @Override
        public void serialize(GraphQLVariables variables, JsonGenerator generator, SerializerProvider provider) throws IOException {
            GraphQLExecutionInfo.ResponseSerializer.writeSelected(variables, generator, provider);
        }
    }
}
