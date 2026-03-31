/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.resources.text;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.tool.AgiToolException;

/**
 * Represents a set of text replacement operations for a specific file. Extends
 * AbstractTextFileWrite to inherit path and optimistic locking.
 *
 * @author anahata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Represents a set of text replacement operations for a specific resource.")
public class TextResourceReplacements extends AbstractTextResourceWrite {

    /**
     * The list of replacements to perform in this file.
     */
    @Schema(description = "The list of replacements to perform in this file.", required = true)
    private List<TextReplacement> replacements;

    @Builder
    public TextResourceReplacements(String resourceUuid, long lastModified, List<TextReplacement> replacements) {
        super(resourceUuid, lastModified);
        this.replacements = replacements;
    }

    /** {@inheritDoc} */
    @Override
    public String calculateResultingContent() throws Exception {
        if (originalContent == null) {
            throw new AgiToolException("Logic Error: calculateResultingContent called before captureOriginalContent");
        }
        String newContent = originalContent;
        for (TextReplacement replacement : replacements) {
            newContent = newContent.replace(replacement.getTarget(), replacement.getReplacement());
        }
        return newContent;
    }

    /** {@inheritDoc} */
    @Override
    public void validate(Agi agi) throws Exception {
        super.validate(agi);

        if (replacements == null || replacements.isEmpty()) {
             throw new AgiToolException("No replacements provided.");
        }

        for (TextReplacement replacement : replacements) {
            String target = replacement.getTarget();
            if (target == null || target.isEmpty()) {
                throw new AgiToolException("Replacement target cannot be null or empty.");
            }
            
            int count = StringUtils.countMatches(originalContent, target);
            
            if (replacement.getExpectedCount() > 0 && count != replacement.getExpectedCount()) {
                throw new AgiToolException("Replacement failed for '" + target + "'. Expected " + replacement.getExpectedCount() + " occurrences, but found " + count);
            }
            
            if (count == 0 && replacement.getExpectedCount() != 0) {
                 throw new AgiToolException("Target string not found in file: " + target);
            }
        }
        // Identical content check is now in parent validate()
    }
}
