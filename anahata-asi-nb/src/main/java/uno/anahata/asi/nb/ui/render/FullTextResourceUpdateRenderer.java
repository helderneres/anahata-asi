/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.ui.render;

import java.util.List;
import uno.anahata.asi.toolkit.resources.text.FullTextResourceUpdate;
import uno.anahata.asi.toolkit.resources.text.LineComment;

/**
 * A rich renderer for {@link FullTextResourceUpdate} tool parameters.
 * 
 * @author anahata
 */
public class FullTextResourceUpdateRenderer extends AbstractTextResourceWriteRenderer<FullTextResourceUpdate> {

    /** {@inheritDoc} */
    @Override
    protected List<LineComment> getLineComments() {
        return update.getLineComments();
    }

    /** {@inheritDoc} */
    @Override
    protected FullTextResourceUpdate createUpdatedDto(String newContent) {
        FullTextResourceUpdate dto = new FullTextResourceUpdate(
                update.getResourceUuid(),
                update.getLastModified(),
                newContent,
                update.getLineComments()
        );
        dto.setOriginalContent(update.getOriginalContent());
        dto.setOriginalResourceName(update.getOriginalResourceName());
        return dto;
    }
}
