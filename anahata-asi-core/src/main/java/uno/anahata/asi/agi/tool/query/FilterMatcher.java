/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.Locale;

/**
 * Compares an extracted property with a requested filter value.
 * Null handling and matching semantics belong to each implementation; this
 * interface does not impose reflection, locale rules or source access.
 * Implementations shared between threads must make their own state safe.
 *
 * @param <V> type of both the extracted and requested values
 * @author anahata
 */
@FunctionalInterface
public interface FilterMatcher<V> {

    /**
     * Tests an actual property against the requested filter value.
     *
     * @param actual extracted property, possibly null if supported by the matcher
     * @param expected requested value, possibly null if supported by the matcher
     * @return true when this implementation considers the values a match
     */
    boolean matches(V actual, V expected);

    /**
     * Creates a substring matcher using {@link Locale#ROOT} case conversion.
     * A null expected value matches every actual value, including null. A
     * non-null expected value never matches a null actual value. An empty
     * expected string matches every non-null actual string.
     *
     * @return a stateless, locale-independent substring matcher
     */
    static FilterMatcher<String> containsIgnoreCase() {
        return (actual, expected) -> expected == null
                || actual != null && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
