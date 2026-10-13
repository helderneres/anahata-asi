/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.io.Serializable;
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
}
