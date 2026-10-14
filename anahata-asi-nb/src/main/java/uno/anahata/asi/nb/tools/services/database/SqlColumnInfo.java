/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Selected result-column attributes. Index is JDBC's one-based position.
 */
@Data
@Builder
public class SqlColumnInfo {

    private int index;
    private String label;
    private String name;
    private Integer jdbcType;
    private String typeName;
    private Boolean nullable;
}
