/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Composes typed property extraction and matching into an in-memory filter.
 * Prefer filtering in the source API before applying this fallback to a small
 * collection. The Lombok-generated constructor requires a non-null extractor
 * and matcher; neither component is invoked until the predicate is tested.
 *
 * <p>Null entities are passed to the extractor unchanged. The extractor owns
 * entity-null handling and the matcher owns property-null handling. This
 * class does not catch component exceptions or make stateful components
 * thread-safe.</p>
 *
 * @param <E> source entity type
 * @param <V> extracted property and expected value type
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
     * Binds an expected value without inspecting the source collection.
     * Each predicate evaluation calls the extractor and then the matcher. The
     * predicate retains the supplied expected-value reference rather than a
     * snapshot; exceptions from either component propagate to the caller.
     *
     * @param expected requested value, including null when the matcher permits it
     * @return a new predicate using this filter's extractor and matcher
     */
    public Predicate<E> predicate(V expected) {
        return entity -> matcher.matches(propertyExtractor.apply(entity), expected);
    }
}
