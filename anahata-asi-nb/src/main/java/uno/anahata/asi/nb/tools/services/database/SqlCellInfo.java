/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * One positional cell in a bounded SQL result row.
 * <p>
 * The one-based column index preserves duplicate labels without merging
 * values into a label-keyed map. Produced values are portable scalars;
 * JDBC objects and LOB handles are converted before this DTO is returned.
 * Text and binary values may be shortened, so this representation is not a
 * lossless database-value export. Resources used to obtain values remain
 * owned and released by the command resolver, not by this DTO.
 * </p>
 * <p>
 * Selecting only cell positions does not read values. In that case this
 * DTO's default value and conversion flags do not describe the SQL cell.
 * </p>
 */
@Data
@Builder
public class SqlCellInfo {
    /**
     * One-based JDBC result-column position, matching {@link SqlColumnInfo#index};
     * independent of duplicate names or aliases.
     */
    private int columnIndex;
    /**
     * Converted string, number, boolean or SQL NULL when value extraction was requested. Null
     * may also be the unpopulated DTO default when only positions were selected.
     * Temporal/UUID values and non-finite floating-point values are represented as text;
     * binary data is encoded as described by {@link #encoding}.
     */
    private Object value;
    /**
     * Whether conversion omitted text or binary content. Text is bounded to 4096 Java UTF-16
     * code units and binary to 4096 bytes before encoding. False can also be the unpopulated
     * default when no value-related field was selected.
     */
    private boolean truncated;
    /**
     * {@code "base64"} for converted binary data; {@code null} for other values or when value
     * extraction was not requested. Decode before interpreting the retained bytes; consult
     * {@link #truncated} for omitted content.
     */
    private String encoding;
}
