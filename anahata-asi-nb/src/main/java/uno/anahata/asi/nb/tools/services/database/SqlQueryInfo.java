/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Selected, bounded output from one explicit SQL query command.
 * <p>
 * SQL executes even when only {@code success} is requested. Result selection
 * controls metadata acquisition, cursor traversal and cell extraction. Lists
 * not requested remain {@code null}; requested lists can be empty. The
 * resolver closes its result set and statement before delivering the result
 * without closing the connection managed by NetBeans.
 * </p>
 * <p>
 * This object describes at most one bounded traversal, not a complete result
 * or a shared transaction. Its success flag does not assert read-only SQL,
 * commit, rollback or success of other GraphQL commands. Failures can occur
 * after SQL has started and are reported through GraphQL errors.
 * </p>
 */
@Data
@Builder
public class SqlQueryInfo {

    /**
     * Whether SQL execution and requested acquisition completed normally in the producer. It
     * does not establish transaction outcome or absence of SQL side effects.
     */
    private boolean success;
    /**
     * Selected result-column metadata in JDBC order, or {@code null} when not requested. An
     * empty list is distinct from an unselected list.
     */
    private List<SqlColumnInfo> columns;
    /**
     * Selected rows admitted by the limit, or {@code null} when rows were not requested. An
     * empty selected list indicates no rows were admitted.
     */
    private List<SqlRowInfo> rows;
    /**
     * Number of rows admitted by bounded traversal, excluding the look-ahead row; {@code
     * null} when no row, count or continuation field required traversal. It is not the total
     * number of matching database rows.
     */
    private Integer returnedRowCount;
    /**
     * Validated maximum admitted rows for this command. The current GraphQL contract defaults
     * to 100 and accepts 1 through 1000; other request budgets can reject a broad selection
     * earlier.
     */
    private int limit;
    /**
     * Whether traversal found one additional row beyond {@link #limit}; {@code null} when
     * continuation was not established because traversal was unnecessary.
     */
    private Boolean hasNextPage;
    /**
     * Total matching database-row count, currently always {@code null}; the resolver does not
     * run a count query or scan the full result to determine it.
     */
    private Integer totalCount;
    /**
     * Elapsed whole milliseconds from immediately before statement creation through requested
     * output acquisition, sampled before resource closure. This is not the duration of the
     * complete GraphQL document; null is possible in an unpopulated DTO.
     */
    private Long elapsedTimeMs;
}
