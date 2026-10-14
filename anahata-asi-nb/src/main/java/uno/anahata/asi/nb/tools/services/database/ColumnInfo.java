/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a column in a database table.
 * <p>
 * Encapsulates the physical structure of a column, including its SQL type,
 * and nullability, which are essential for schema
 * understanding.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class ColumnInfo {

    private String columnName;
    private String dataType;
    private Integer columnSize;
    private Boolean nullable;
    private Boolean autoIncrement;

}
