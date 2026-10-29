/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Controls result acquisition, independently of command effects or permission.
 * An effective implementation must be immutable and validate these values.
 */
public interface ResultLoadingPolicy {

    /**
     * @return mandatory positive page default in an effective policy
     */
    Integer getDefaultPageSize();

    /**
     * @return positive page ceiling, or null for no policy-imposed ceiling
     */
    Integer getMaxPageSize();
}
