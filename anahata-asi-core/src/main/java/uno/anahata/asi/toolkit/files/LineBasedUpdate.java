/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.files;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single line-based replacement operation.
 * <p>
 * This DTO is optimized for agentic workflows, using semantic naming 
 * that aligns with natural model reasoning.
 * </p>
 * 
 * @author anahata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Represents a line-based update operation.")
public class LineBasedUpdate {
    /**
     * The 1-based start line number.
     */
    @Schema(description = "The 1-based start line number.", required = true)
    private int startLine;

    /**
     * The number of lines to remove from the file, starting at startLine.
     * Use 0 for pure insertion.
     */
    @Schema(description = "The number of lines to update: 0 for pure insertions. The number of lines to delete for pure deletions. The number of lines to replace for replacements. ", required = true)
    private int lineCount;

    /**
     * The replacement text. Can be multiple lines. Use empty string for pure removal.
     */
    @Schema(description = "The new lines for that range. Use the platforms line separator between lines or no line separator at all if you are just replacing a single line. Empty if you just want to delete lines.")
    private String newContent;

    /**
     * The reason for this change.
     */
    @Schema(description = "The reason for this change.", required = true)
    private String reason;
}
