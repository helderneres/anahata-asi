/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the result of a database DML or DDL statement.
 * <p>
 * Encapsulates the outcome of non-query operations (INSERT, UPDATE, DELETE,
 * CREATE, etc.), providing information about affected rows and execution metrics
 * in a structured, disconnected format.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class StatementResult {

    private String message;
    private int affectedRows;
    private long executionTimeMs;

}
