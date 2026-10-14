/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A portable GraphQL response containing only selected JSON values and safe
 * errors. Engine, JDBC and IDE objects never belong in this transport model.
 *
 * @author anahata
 */
@JsonSerialize(using = GraphQLExecutionInfo.ResponseSerializer.class)
@Getter
@RequiredArgsConstructor
public class GraphQLExecutionInfo implements Serializable {

    /**
     * Java serialization identity, independent of the transient engine.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Selected response data; nullable when validation prevents execution.
     */
    private final Map<String, Object> data;

    /**
     * Safe errors, including partial field failures without stack traces.
     */
    private final List<GraphQLErrorInfo> errors;

    /**
     * A compact UI hint; the structured data/errors remain the source of truth.
     */
    @Override
    public String toString() {
        return "GraphQL response: data=" + (data == null ? "null" : data.keySet())
                + "; errors=" + errors.size() + ". Inspect both data and errors.";
    }

    /**
     * Local transport serializer preserving selected nulls even when the shared
     * mapper omits null map values. No serialization policy of other tools
     * changes.
     */
    public static final class ResponseSerializer extends StdSerializer<GraphQLExecutionInfo> {

        public ResponseSerializer() {
            super(GraphQLExecutionInfo.class);
        }

        @Override
        public void serialize(GraphQLExecutionInfo response, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeStartObject();
            generator.writeFieldName("data");
            writeSelected(response.data, generator, provider);
            generator.writeFieldName("errors");
            provider.defaultSerializeValue(response.errors, generator);
            generator.writeEndObject();
        }

        static void writeSelected(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value == null) {
                generator.writeNull();
            } else if (value instanceof Map<?, ?> map) {
                generator.writeStartObject();
                for (var entry : map.entrySet()) {
                    generator.writeFieldName((String) entry.getKey());
                    writeSelected(entry.getValue(), generator, provider);
                }
                generator.writeEndObject();
            } else if (value instanceof List<?> list) {
                generator.writeStartArray();
                for (Object item : list) {
                    writeSelected(item, generator, provider);
                }
                generator.writeEndArray();
            } else {
                provider.defaultSerializeValue(value, generator);
            }
        }
    }
}
