/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.render;

import java.util.ArrayList;
import java.util.List;
import uno.anahata.asi.toolkit.files.LineReplacement;
import uno.anahata.asi.toolkit.files.LineComment;
import uno.anahata.asi.toolkit.files.TextFileLineReplacements;
import uno.anahata.asi.toolkit.files.TextReplacement;

/**
 * A rich renderer for {@link TextFileLineReplacements} tool parameters.
 * It provides a preview of line-based replacements in the NetBeans diff viewer.
 * 
 * @author anahata
 */
public class TextFileLineReplacementsRenderer extends AbstractTextFileWriteRenderer<TextFileLineReplacements> {

    @Override
    protected String calculateProposedContent(String currentContent) throws Exception {
        return update.performReplacements(currentContent);
    }

    @Override
    protected List<LineComment> getLineComments(String currentContent) {
        List<LineComment> comments = new ArrayList<>();
        if (update.getReplacements() == null) {
            return comments;
        }

        for (LineReplacement lr : update.getReplacements()) {
            if (lr.getReason() == null || lr.getReason().isBlank()) {
                continue;
            }
            // Line numbers in DTO are already 1-based, matching LineComment expectation
            comments.add(new LineComment(lr.getStartLine(), lr.getReason()));
        }
        return comments;
    }

    @Override
    protected TextFileLineReplacements createUpdatedDto(String newContent) {
        // Fallback to a single 'custom' replacement if the user manually edits the diff
        TextFileLineReplacements dto = new TextFileLineReplacements(
                update.getPath(),
                update.getLastModified(),
                List.of(LineReplacement.builder()
                        .startLine(1)
                        .lineCount(-1) // Special flag for renderer-driven full replacement? No, let's keep it simple.
                        .replacement(newContent)
                        .reason("User manual edit")
                        .build())
        );
        
        // Actually, for manual edits in a line-based tool, it's safer to just 
        // convert it to a full content update or a single 1-to-N line replacement.
        // Let's implement a 'Full override' logic:
        LineReplacement fullOverride = LineReplacement.builder()
                .startLine(1)
                .lineCount(Integer.MAX_VALUE) // Replace everything
                .replacement(newContent)
                .reason("User manual edit")
                .build();
        
        dto.setReplacements(List.of(fullOverride));
        dto.setOriginalContent(update.getOriginalContent());
        return dto;
    }

    @Override
    protected int getInitialTabIndex() {
        return 0; // Line-based changes are best viewed in Graphical diff
    }
}
