/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.files.lines;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import uno.anahata.asi.agi.tool.AiToolException;
import java.util.Arrays;
import java.util.List;

/**
 * PURE INSERTION: Places new content BEFORE the specified line.
 * <p>
 * Zero risk of deleting existing code. If you target line 10, the original 
 * line 10 becomes line 11 (or lower depending on how many lines you insert).
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Performs a 'push-down' insertion. The original line at 'atLine' and all subsequent lines are pushed down.")
public class LineInsertion extends AbstractLineEdit {

    @Schema(description = "The 1-based line number before which the content will be inserted.", required = true)
    private int atLine;

    @Schema(description = "The new lines to insert.", required = true)
    private String content;

    @Override
    public void apply(List<String> lines) throws AiToolException {
        if (atLine < 1 || atLine > lines.size() + 1) {
            throw new AiToolException("Insertion line " + atLine + " is out of bounds (1-" + (lines.size() + 1) + ")");
        }
        
        if (content == null || content.isEmpty()) {
            return;
        }

        // Split into lines, treating trailing newline as structural (absorption)
        List<String> newLines = new java.util.ArrayList<>(Arrays.asList(content.split("\\R", -1)));
        if (newLines.size() > 1 && newLines.get(newLines.size() - 1).isEmpty()) {
            newLines.remove(newLines.size() - 1);
        }

        int targetIndex = atLine - 1;
        for (int i = newLines.size() - 1; i >= 0; i--) {
            lines.add(targetIndex, newLines.get(i));
        }
    }

    @Override
    public int getSortLine() {
        return atLine;
    }
}
