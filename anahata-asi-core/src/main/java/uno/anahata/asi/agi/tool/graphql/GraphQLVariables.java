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
 * A normal Java map preserving the GraphQL difference between null and omitted
 * variables when a tool call is serialized. No global mapper policy changes.
 */
@JsonSerialize(using = GraphQLVariables.VariablesSerializer.class)
public final class GraphQLVariables extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    /**
     * Initializes the backing map when the ordinary Kryo MapSerializer restores
     * it.
     */
    public GraphQLVariables() {
        super();
    }

    public GraphQLVariables(Map<String, Object> variables) {
        super(variables);
    }

    /**
     * Writes nested JSON values, including nulls, only for this variable map.
     */
    public static final class VariablesSerializer extends StdSerializer<GraphQLVariables> {

        public VariablesSerializer() {
            super(GraphQLVariables.class);
        }

        @Override
        public void serialize(GraphQLVariables variables, JsonGenerator generator, SerializerProvider provider) throws IOException {
            GraphQLExecutionInfo.ResponseSerializer.writeSelected(variables, generator, provider);
        }
    }
}
