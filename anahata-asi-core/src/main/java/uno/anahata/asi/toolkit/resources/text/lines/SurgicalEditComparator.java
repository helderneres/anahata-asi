/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.toolkit.resources.text.lines;

import java.io.Serializable;
import java.util.Comparator;

/**
 * The definitive comparator for surgical line edits, ensuring coordinate stability 
 * and predictable tie-breaking.
 * <p>
 * This comparator sorts primarily by the 1-based line number (ascending). 
 * In cases where multiple edits target the same coordinate, it places 
 * {@link LineInsertion}s before range-based edits ({@link LineReplacement} or 
 * {@link LineDeletion}).
 * </p>
 * <p>
 * <b>Tie-Breaker Logic:</b>
 * Logically, a point-insertion at line X happens "above" the content currently at line X. 
 * By sorting insertions first in an ascending sequence, the validator can ensure that 
 * a push-down operation does not interfere with a subsequent replacement of the original 
 * line content.
 * </p>
 * <p>
 * For application phases (bottom-up), use {@code new SurgicalEditComparator().reversed()}.
 * </p>
 * 
 * @author anahata
 */
public class SurgicalEditComparator implements Comparator<AbstractLineEdit>, Serializable {

    /** 
     * {@inheritDoc} 
     * <p>
     * Implementation details: performs ascending numeric sort with an insertion-first 
     * tie-breaker for identical coordinates.
     * </p>
     */
    @Override
    public int compare(AbstractLineEdit a, AbstractLineEdit b) {
        int cmp = Integer.compare(a.getSortLine(), b.getSortLine());
        if (cmp != 0) {
            return cmp;
        }

        boolean aIsIns = a instanceof LineInsertion;
        boolean bIsIns = b instanceof LineInsertion;

        if (aIsIns && !bIsIns) {
            return -1; // Insertion comes first
        }
        if (!aIsIns && bIsIns) {
            return 1; // Range edit comes last
        }

        return 0;
    }
}
