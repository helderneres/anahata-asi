/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * A bounded page of connection-discovery or table-metadata items.
 * <p>
 * Connection discovery paginates a filtered in-memory snapshot sorted by
 * its displayed name and can supply a total. JDBC table discovery skips
 * {@code offset} rows and reads at most {@code limit} returned rows plus
 * one look-ahead row; it does not scan all tables to populate the total.
 * Table order is the driver's iteration order and is not guaranteed stable
 * across changes to the catalogue or subsequent requests.
 * </p>
 * <p>
 * The GraphQL table resolver can leave items unmaterialized when only page
 * attributes are selected. Consequently, the DTO's empty item list alone is
 * not proof that the database contains no matching tables.
 * </p>
 *
 * @param <T> the metadata item type carried by this page
 * @author anahata
 */
@Data
@Builder
public class MetadataPageInfo<T> {

    /**
     * Items materialized for this page only. For JDBC tables the list may be empty when items
     * were not selected, even if matching rows exist.
     */
    private List<T> items;
    /**
     * Requested zero-based count of matching entries to skip. Retained unchanged even when it
     * lies beyond the available entries.
     */
    private int offset;
    /**
     * Validated requested maximum page size; the current GraphQL metadata page contract
     * defaults to 25 and accepts 1 through 100.
     */
    private int limit;
    /**
     * Whether another matching entry exists beyond the page, based on the in-memory snapshot
     * or one JDBC look-ahead row. The table resolver leaves this false when neither items nor
     * continuation was requested, without probing JDBC.
     */
    private boolean hasNextPage;
    /**
     * Count of all matching registered connections when efficiently known. JDBC table
     * discovery leaves it {@code null} rather than performing a full scan; null does not mean
     * zero.
     */
    private Integer totalCount;
}
