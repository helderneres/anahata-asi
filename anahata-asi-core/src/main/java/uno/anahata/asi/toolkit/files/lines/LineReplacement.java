/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.files.lines;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import uno.anahata.asi.agi.tool.AiToolException;
import java.util.Arrays;
import java.util.List;

/**
 * VISUAL TARGET REPLACEMENT: Replaces a range of lines with new content.
 * <p>
 * Use this when you can clearly see the start and end of a block (like a method or Javadoc)
 * in the RAG message.
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Replaces a specific range of lines [startLine, endLine] inclusive."
        + " Use `startLine`=`endLine` to replace a single line. All lines from `startLine` to `endLine` will be replaced with `content`."
        + " The `content` can contain any number of lines. Never include in content lines that do not need to change and make sure that startLine is exactly the first line that needs to be replaced.")
public class LineReplacement extends AbstractLineEdit {

    @Schema(description = "The 1-based line number of the resource in the RAG message where the replacement starts (Inclusive).", required = true)
    private int startLine;

    @Schema(description = "The 1-based line number of the resource in the RAG message where the replacement ends (Inclusive).", required = true)
    private int endLine;

    @Schema(description = "The new content for the [startLine, endLine] range of the resource in the RAG message. It can have as many lines as you want. Do not include surrounding anchors because that is not the way this tool works.", required = true)
    private String content;

    @Override
    public void apply(List<String> lines) throws AiToolException {
        if (startLine < 1 || endLine < startLine || endLine > lines.size()) {
            throw new AiToolException("Replacement range [" + startLine + ", " + endLine + "] is invalid or out of bounds (1-" + lines.size() + ")");
        }

        // 1. Remove the old lines
        int countToRemove = (endLine - startLine) + 1;
        int removeIndex = startLine - 1;
        for (int i = 0; i < countToRemove; i++) {
            lines.remove(removeIndex);
        }

        // 2. Insert new content (with absorption)
        if (content != null && !content.isEmpty()) {
            List<String> newLines = new java.util.ArrayList<>(Arrays.asList(content.split("\\R", -1)));
            if (newLines.size() > 1 && newLines.get(newLines.size() - 1).isEmpty()) {
                newLines.remove(newLines.size() - 1);
            }
            for (int i = newLines.size() - 1; i >= 0; i--) {
                lines.add(removeIndex, newLines.get(i));
            }
        }
    }

    @Override
    public int getSortLine() {
        return startLine;
    }
}
