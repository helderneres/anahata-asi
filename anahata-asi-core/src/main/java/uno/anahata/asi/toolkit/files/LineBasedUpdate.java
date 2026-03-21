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
@Schema(description = "Represents a token-efficient line-based update operation that can insert, delete or replace a single line or a range of lines without surrounding anchors/context.\n"
        + " For adding javadoc, headers, or new code, ALWAYS prefer PURE INSERTION (`lineCount=0`). This places `newContent` BEFORE the `startLine`, pushing the original line down without any risk of deleting it. Only use `lineCount > 0` when you intend to remove or overwrite existing code.")
public class LineBasedUpdate {
    /**
     * The 1-based start line number.
     */
    @Schema(description = "The 1-based line number in the RAG message where the operation starts. For REPLACEMENTS and DELETIONS, this is the first line to be removed. For INSERTIONS, this is the line before which the new content will be placed.", required = true)
    private int startLine;

    /**
     * The number of lines from `startLine` (including the `startLine` line) in the text resource that will be deleted or replaced."
     * Use 0 for pure insertion.
     */
    @Schema(description = "The number of lines to remove starting from startLine. "
        + "0 = PURE INSERTION (newContent is placed BEFORE startLine); "
        + "1 = REPLACE SINGLE LINE (the line at startLine is replaced); "
        + "N = REPLACE RANGE (N lines starting from startLine are replaced); "
        + "If newContent is empty and lineCount > 0, it is a PURE DELETION.", required = true)
    private int lineCount;

    /**
     * The replacement text. Can be multiple lines. Use empty string for pure removal.
     */
    @Schema(description = "The new lines for the range. Do NOT include surrounding context/anchors from the source file as it would cause the tool to fail; "
        + "only include the content that should exist between [startLine] and [startLine + lineCount]. "
        + "Structural Newline Absorption is active: a single trailing \\n will NOT create a blank line.")
    private String newContent;

    /**
     * The reason for this change.
     */
    @Schema(description = "The reason for this change.", required = true)
    private String reason;
}
