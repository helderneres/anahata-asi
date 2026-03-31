/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.resources.text.lines;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import uno.anahata.asi.agi.tool.AgiToolException;
import java.util.List;

/**
 * SAFETY-FIRST DELETION: Removes a range of lines and verifies the count.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Deletes a range of lines. Requires an explicit count to prevent off-by-one errors.")
public class LineDeletion extends AbstractLineEdit {

    @Schema(description = "The 1-based line number where deletion starts (Inclusive).", required = true)
    private int startLine;

    @Schema(description = "The 1-based line number where deletion ends (Inclusive).", required = true)
    private int endLine;

    @Schema(description = "The number of lines you expect to delete. Used as a checksum.", required = true)
    private int expectedCount;

    @Override
    public void apply(List<String> lines) throws AgiToolException {
        int actualCount = (endLine - startLine) + 1;
        if (actualCount != expectedCount) {
            throw new AgiToolException("Checksum failed: range [" + startLine + ", " + endLine + "] is " + actualCount + " lines, but you expected to delete " + expectedCount + ".");
        }

        if (startLine < 1 || endLine > lines.size()) {
            throw new AgiToolException("Deletion range out of bounds: [" + startLine + ", " + endLine + "] for file with " + lines.size() + " lines.");
        }

        int removeIndex = startLine - 1;
        for (int i = 0; i < actualCount; i++) {
            lines.remove(removeIndex);
        }
    }

    @Override
    public int getSortLine() {
        return startLine;
    }
}
