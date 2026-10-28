/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Outcome of one SQL command executed through JDBC update semantics.
 * <p>
 * The command runs even when its affected-row count is not selected. The
 * resolver releases its statement while preserving the IDE-owned connection.
 * A successful outcome is not a transaction commit and does not imply that
 * other commands succeeded or that earlier effects can be rolled back.
 * Execution or output failures are conveyed through GraphQL errors.
 * </p>
 */
@Data
@Builder
public class SqlStatementInfo {
    /**
     * Whether the update command completed normally in the producer; not proof of a
     * transaction commit or success of sibling commands.
     */
    private boolean success;
    /**
     * Driver-provided value from {@link java.sql.Statement#executeUpdate(String)}. Zero may
     * describe no affected rows or a statement such as DDL that does not report a row count.
     */
    private int affectedRows;
    /**
     * Elapsed whole milliseconds from immediately before statement creation through execution
     * and the post-execution budget check, sampled before statement closure. Null is possible
     * in an unpopulated DTO.
     */
    private Long elapsedTimeMs;
}
