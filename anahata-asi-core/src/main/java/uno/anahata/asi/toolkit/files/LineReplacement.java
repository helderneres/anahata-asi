/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.files;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single line-based replacement operation.
 * 
 * @author anahata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Represents a single line-based replacement operation.")
public class LineReplacement {
    /**
     * The 1-based start line number.
     */
    @Schema(description = "The 1-based start line number.", required = true)
    private int startLine;

    /**
     * The number of lines to replace/remove. Use 0 for pure insertion.
     */
    @Schema(description = "The number of lines to replace/remove. Use 0 for pure insertion.", required = true)
    private int lineCount;

    /**
     * The replacement text. Can be multiple lines. Use empty string for pure removal.
     */
    @Schema(description = "The replacement text. Can be multiple lines. Use empty string for pure removal.", required = true)
    private String replacement;

    /**
     * The reason for this change.
     */
    @Schema(description = "The reason for this change.", required = true)
    private String reason;
}
