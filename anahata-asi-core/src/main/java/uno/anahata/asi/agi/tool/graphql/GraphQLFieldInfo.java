/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * A selected schema field and its engine-coerced arguments, used exclusively
 * before execution. This is not a public result or a generic selection planner.
 *
 * @author anahata
 */
@Getter
public final class GraphQLFieldInfo {

    private final String parentType;
    private final String fieldName;
    private final String responseName;
    private final Map<String, Object> arguments;

    /**
     * Creates an immutable field snapshot preserving nullable arguments.
     */
    public GraphQLFieldInfo(String parentType, String fieldName, String responseName, Map<String, Object> arguments) {
        this.parentType = parentType;
        this.fieldName = fieldName;
        this.responseName = responseName;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
