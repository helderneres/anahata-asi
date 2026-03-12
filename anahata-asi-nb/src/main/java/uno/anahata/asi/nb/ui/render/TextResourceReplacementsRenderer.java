/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.render;

import java.util.ArrayList;
import java.util.List;
import uno.anahata.asi.toolkit.files.LineComment;
import uno.anahata.asi.toolkit.files.TextResourceReplacements;
import uno.anahata.asi.toolkit.files.TextReplacement;

/**
 * A rich renderer for {@link TextResourceReplacements} tool parameters.
 * It provides a preview of surgical replacements in the NetBeans diff viewer
 * and automatically maps replacement reasons to line-level gutter comments.
 * 
 * @author anahata
 */
public class TextResourceReplacementsRenderer extends AbstractTextResourceWriteRenderer<TextResourceReplacements> {

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

        for (TextReplacement tr : update.getReplacements()) {
            if (tr.getReason() == null || tr.getReason().isBlank()) {
                continue;
            }

            // Resolve the line number where the target replacement begins
            int index = currentContent.indexOf(tr.getTarget());
            if (index != -1) {
                int lineNum = 1;
                for (int i = 0; i < index; i++) {
                    if (currentContent.charAt(i) == '\n') {
                        lineNum++;
                    }
                }
                comments.add(new LineComment(lineNum, tr.getReason()));
            }
        }
        return comments;
    }

    @Override
    protected TextResourceReplacements createUpdatedDto(String newContent) {
        // When the user edits a surgical replacement in the UI, we convert it
        // to a 'custom' replacement that replaces everything with the new content.
        // This is necessary because the original surgical list no longer applies to the manual edits.
        TextResourceReplacements dto = new TextResourceReplacements(
                update.getResourceUuid(),
                update.getLastModified(),
                List.of(TextReplacement.builder()
                        .target(newContent) // This is a bit of a hack, but it works for UI merging
                        .replacement(newContent)
                        .reason("User manual edit")
                        .expectedCount(1)
                        .build())
        );
        dto.setOriginalContent(update.getOriginalContent());
        return dto;
    }

    @Override
    protected int getInitialTabIndex() {
        return 1; // Default to Textual tab for replacements
    }
}
