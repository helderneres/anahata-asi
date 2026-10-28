/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * One column-pair component of a JDBC foreign-key relationship.
 * <p>
 * The primary-key side is referenced and the foreign-key side is referencing,
 * regardless of whether discovery used imported or exported keys. Composite
 * relationships produce multiple components; the sequence records their
 * column order. Preserve both complete table identities and constraint
 * information rather than grouping solely by a potentially absent name.
 * Copied numeric rules retain JDBC constants, not application-specific enums.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class TableRelationshipInfo {

    /**
     * Catalog of the referenced table from JDBC {@code PKTABLE_CAT}, possibly {@code null}.
     */
    private String primaryKeyCatalog;
    /**
     * Schema of the referenced table from JDBC {@code PKTABLE_SCHEM}, possibly {@code null}.
     */
    private String primaryKeySchema;
    /**
     * Identifier of the referenced table from JDBC {@code PKTABLE_NAME}.
     */
    private String primaryKeyTable;
    /**
     * Referenced column identifier from JDBC {@code PKCOLUMN_NAME} for this component.
     */
    private String primaryKeyColumn;
    /**
     * Catalog of the referencing table from JDBC {@code FKTABLE_CAT}, possibly {@code null}.
     */
    private String foreignKeyCatalog;
    /**
     * Schema of the referencing table from JDBC {@code FKTABLE_SCHEM}, possibly {@code null}.
     */
    private String foreignKeySchema;
    /**
     * Identifier of the referencing table from JDBC {@code FKTABLE_NAME}.
     */
    private String foreignKeyTable;
    /**
     * Referencing column identifier from JDBC {@code FKCOLUMN_NAME} for this component.
     */
    private String foreignKeyColumn;
    /**
     * One-based position of this column pair in a composite relationship from JDBC {@code
     * KEY_SEQ}, or {@code null} for SQL NULL.
     */
    private Integer keySequence;
    /**
     * JDBC {@code UPDATE_RULE} constant, such as {@link
     * java.sql.DatabaseMetaData#importedKeyCascade}, or {@code null} for SQL NULL; no rule is
     * executed by this DTO.
     */
    private Integer updateRule;
    /**
     * JDBC {@code DELETE_RULE} constant, such as {@link
     * java.sql.DatabaseMetaData#importedKeyRestrict}, or {@code null} for SQL NULL.
     */
    private Integer deleteRule;
    /**
     * Referencing foreign-key constraint name from JDBC {@code FK_NAME}, possibly {@code
     * null}; insufficient alone to identify a relationship across tables.
     */
    private String foreignKeyName;
    /**
     * Referenced primary/unique-key constraint name from JDBC {@code PK_NAME}, possibly
     * {@code null}.
     */
    private String primaryKeyName;
    /**
     * JDBC {@code DEFERRABILITY} constant, such as {@link
     * java.sql.DatabaseMetaData#importedKeyNotDeferrable}, or {@code null} for SQL NULL. This
     * reports metadata and does not change transaction settings.
     */
    private Integer deferrability;
}
