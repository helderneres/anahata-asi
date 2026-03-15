/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uno.anahata.asi.toolkit.files.LineBasedUpdate;
import uno.anahata.asi.toolkit.files.LineComment;
import uno.anahata.asi.toolkit.files.TextResourceLineBasedUpdates;

/**
 * A rich renderer for {@link TextResourceLineBasedUpdates} tool parameters.
 * It provides a preview of line-based replacements in the NetBeans diff viewer.
 * 
 * @author anahata
 */
public class TextResourceLineUpdatesRenderer extends AbstractTextResourceWriteRenderer<TextResourceLineBasedUpdates> {

    /** {@inheritDoc} */
    @Override
    protected String calculateProposedContent(String currentContent) throws Exception {
        return update.performUpdates(currentContent);
    }

    /** {@inheritDoc} */
    @Override
    protected List<LineComment> getLineComments(String currentContent) {
        List<LineComment> comments = new ArrayList<>();
        if (update.getUpdates()== null) {
            return comments;
        }

        List<LineBasedUpdate> sorted = new ArrayList<>(update.getUpdates());
        sorted.sort(Comparator.comparingInt(LineBasedUpdate::getStartLine));

        int cumulativeShift = 0;
        for (LineBasedUpdate lr : sorted) {
            if (lr.getReason() != null && !lr.getReason().isBlank()) {
                int proposedLine = lr.getStartLine() + cumulativeShift;
                comments.add(new LineComment(proposedLine, lr.getReason()));
            }

            int added = DiffCommentUtils.getLineCount(lr.getNewContent());
            int removed = lr.getLineCount();
            cumulativeShift += (added - removed);
        }
        return comments;
    }

    /** {@inheritDoc} */
    @Override
    protected TextResourceLineBasedUpdates createUpdatedDto(String newContent) {
        LineBasedUpdate fullOverride = LineBasedUpdate.builder()
                .startLine(1)
                .lineCount(Integer.MAX_VALUE) 
                .newContent(newContent)
                .reason("User manual edit")
                .build();
        
        TextResourceLineBasedUpdates dto = new TextResourceLineBasedUpdates(
                update.getResourceUuid(),
                update.getLastModified(),
                List.of(fullOverride)
        );
        dto.setOriginalContent(update.getOriginalContent());
        return dto;
    }

    /** {@inheritDoc} */
    @Override
    protected int getInitialTabIndex() {
        return 0;
    }
}
