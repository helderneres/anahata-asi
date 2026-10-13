/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Composes typed property extraction and matching into an in-memory predicate.
 * Prefer native source filtering before using this fallback on small
 * collections.
 *
 * @param <E> Source entity type.
 * @param <V> Extracted property type.
 * @author anahata
 */
@RequiredArgsConstructor
public class ToolFilter<E, V> {

    /**
     * Typed property extraction, avoiding reflective getters.
     */
    @NonNull
    private final Function<E, V> propertyExtractor;

    /**
     * Domain-appropriate comparison of actual and requested values.
     */
    @NonNull
    private final FilterMatcher<V> matcher;

    /**
     * Binds a requested value to this typed filter.
     *
     * @param expected Requested property value.
     * @return A predicate directly usable by streams or collections.
     */
    public Predicate<E> predicate(V expected) {
        return entity -> matcher.matches(propertyExtractor.apply(entity), expected);
    }
}
