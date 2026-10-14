/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests typed functional composition without reflection or selection planning.
 *
 * @author anahata
 */
public class ToolFilterTest {

    /**
     * Matches a typed property case-insensitively and composes a normal
     * projector.
     */
    @Test
    public void filtersAndProjectsTypedSources() {
        ToolFilter<String, String> filter = new ToolFilter<>(Function.identity(), FilterMatcher.containsIgnoreCase());
        assertEquals(List.of("Anahata prod", "anahata test"), List.of("Anahata prod", "other", "anahata test")
                .stream().filter(filter.predicate("AnAhAtA")).toList());
        assertTrue(filter.predicate(null).test(null));
        assertFalse(filter.predicate("Anahata").test(null));
        ToolProjector<String, Integer> projector = String::length;
        assertEquals(List.of(3, 4), List.of("one", "four").stream().map(projector).toList());
    }
}
