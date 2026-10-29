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
import uno.anahata.asi.agi.tool.execution.MutableQueryExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;

/**
 * Editable partial query configuration with immutable atomic publication.
 * Nested loading values are copied when supplied. Caller-owned implementations
 * of the open loading interface must remain stable during that boundary copy.
 * Omitted properties inherit; putTimeout(null) explicitly removes the timeout.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class DatabaseQueryPolicy implements MutableQueryExecutionPolicy<DatabaseQueryPolicy>, Serializable {

    private static final long serialVersionUID = 1L;
    private volatile PolicyValues values = PolicyValues.empty();

    @Override
    public Duration getTimeout() {
        return values.timeout;
    }

    @Override
    public ResultLoadingPolicy getLoadingPolicy() {
        return values.loading;
    }

    @Override
    @JsonProperty("timeout")
    public synchronized DatabaseQueryPolicy putTimeout(Duration timeout) {
        PolicyValues.validateTimeout(timeout);
        values = new PolicyValues(true, timeout, values.loading);
        return this;
    }

    @Override
    @JsonProperty("loadingPolicy")
    @JsonDeserialize(as = DatabaseLoadingPolicy.class)
    public synchronized DatabaseQueryPolicy putLoadingPolicy(ResultLoadingPolicy loadingPolicy) {
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
            result.put("loadingPolicy", DatabaseLoadingPolicy.from(current.loading));
        }
        return result;
    }

    PolicyValues snapshot() {
        return values;
    }

    static DatabaseQueryPolicy from(PolicyValues values) {
        DatabaseQueryPolicy result = new DatabaseQueryPolicy();
        result.values = values;
        return result;
    }
}
