/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * A bounded metadata page that does not require materializing all JDBC rows.
 * The total is optional: JDBC table discovery only reads one look-ahead row.
 *
 * @param <T> the concrete metadata item type
 * @author anahata
 */
@Data
@Builder
public class MetadataPageInfo<T> {

    /**
     * Items returned for this page only.
     */
    private List<T> items;
    /**
     * Zero-based starting position in the driver's iteration order.
     */
    private int offset;
    /**
     * Validated requested maximum number of items.
     */
    private int limit;
    /**
     * Whether a look-ahead row exists beyond this page.
     */
    private boolean hasNextPage;
    /**
     * Efficiently available count, or null when a full scan would be needed.
     */
    private Integer totalCount;
}
