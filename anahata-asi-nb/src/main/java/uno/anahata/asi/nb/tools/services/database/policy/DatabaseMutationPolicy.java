/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import uno.anahata.asi.agi.tool.execution.MutableMutationExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;

/**
 * Editable partial command policy. Return loading never limits command effects
 * or grants permission. Publications and nested copies follow
 * DatabaseQueryPolicy.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class DatabaseMutationPolicy implements MutableMutationExecutionPolicy<DatabaseMutationPolicy>, Serializable {

    private static final long serialVersionUID = 1L;
    private volatile PolicyValues values = PolicyValues.empty();

    @Override
    public Duration getTimeout() {
        return values.timeout;
    }

    @Override
    public ResultLoadingPolicy getResultLoadingPolicy() {
        return values.loading;
    }

    @Override
    @JsonProperty("timeout")
    public synchronized DatabaseMutationPolicy putTimeout(Duration timeout) {
        PolicyValues.validateTimeout(timeout);
        values = new PolicyValues(true, timeout, values.loading);
        return this;
    }

    @Override
    @JsonProperty("resultLoadingPolicy")
    @JsonDeserialize(as = DatabaseLoadingPolicy.class)
    public synchronized DatabaseMutationPolicy putResultLoadingPolicy(ResultLoadingPolicy loadingPolicy) {
        values = new PolicyValues(values.timeoutSpecified, values.timeout, PolicyValues.Loading.copyOf(loadingPolicy));
        return this;
    }

    /**
     * @return detached JSON properties, including explicitly null timeout
     */
    @JsonAnyGetter
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
    public Map<String, Object> configurationProperties() {
        PolicyValues current = values;
        Map<String, Object> result = new LinkedHashMap<>();
        if (current.timeoutSpecified) {
            result.put("timeout", current.timeout);
        }
        if (current.loading.defaultSpecified || current.loading.maxSpecified) {
            result.put("resultLoadingPolicy", DatabaseLoadingPolicy.from(current.loading));
        }
        return result;
    }

    PolicyValues snapshot() {
        return values;
    }

    static DatabaseMutationPolicy from(PolicyValues values) {
        DatabaseMutationPolicy result = new DatabaseMutationPolicy();
        result.values = values;
        return result;
    }
}
