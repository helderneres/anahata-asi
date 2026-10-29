/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import uno.anahata.asi.agi.tool.execution.MutableResultLoadingPolicy;

/**
 * Editable partial loading configuration. A new instance inherits both values.
 * Each put atomically publishes immutable values; resolution copies nested
 * data. Explicit null maximum removes an inherited maximum. JSON binds put
 * methods explicitly and serializes only properties present in this layer.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class DatabaseLoadingPolicy implements MutableResultLoadingPolicy<DatabaseLoadingPolicy>, Serializable {

    private static final long serialVersionUID = 1L;
    private volatile PolicyValues.Loading values = PolicyValues.Loading.empty();

    @Override
    public Integer getDefaultPageSize() {
        return values.defaultPageSize;
    }

    @Override
    public Integer getMaxPageSize() {
        return values.maxPageSize;
    }

    @Override
    @JsonProperty("defaultPageSize")
    public synchronized DatabaseLoadingPolicy putDefaultPageSize(Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("defaultPageSize must be positive");
        }
        values = new PolicyValues.Loading(true, value, values.maxSpecified, values.maxPageSize);
        return this;
    }

    @Override
    @JsonProperty("maxPageSize")
    public synchronized DatabaseLoadingPolicy putMaxPageSize(Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("maxPageSize must be positive or null");
        }
        values = new PolicyValues.Loading(values.defaultSpecified, values.defaultPageSize, true, value);
        return this;
    }

    /**
     * Exposes only present properties for JSON; null maximum must survive.
     *
     * @return detached map describing this partial layer
     */
    @JsonAnyGetter
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
    public Map<String, Object> configurationProperties() {
        PolicyValues.Loading current = values;
        Map<String, Object> result = new LinkedHashMap<>();
        if (current.defaultSpecified) {
            result.put("defaultPageSize", current.defaultPageSize);
        }
        if (current.maxSpecified) {
            result.put("maxPageSize", current.maxPageSize);
        }
        return result;
    }

    PolicyValues.Loading snapshot() {
        return values;
    }

    static DatabaseLoadingPolicy from(PolicyValues.Loading values) {
        DatabaseLoadingPolicy result = new DatabaseLoadingPolicy();
        result.values = values;
        return result;
    }
}
