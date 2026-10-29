/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;

/**
 * Immutable publication values, including presence separately from null.
 */
final class PolicyValues implements Serializable {

    private static final long serialVersionUID = 1L;
    final boolean timeoutSpecified;
    final Duration timeout;
    final Loading loading;

    PolicyValues(boolean timeoutSpecified, Duration timeout, Loading loading) {
        this.timeoutSpecified = timeoutSpecified;
        this.timeout = timeout;
        this.loading = loading;
    }

    static PolicyValues empty() {
        return new PolicyValues(false, null, Loading.empty());
    }

    static void validateTimeout(Duration timeout) {
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive or null");
        }
    }

    /**
     * Immutable loading values; omitted fields retain their presence flags.
     */
    static final class Loading implements ResultLoadingPolicy, Serializable {

        private static final long serialVersionUID = 1L;
        final boolean defaultSpecified;
        final Integer defaultPageSize;
        final boolean maxSpecified;
        final Integer maxPageSize;

        Loading(boolean defaultSpecified, Integer defaultPageSize, boolean maxSpecified, Integer maxPageSize) {
            this.defaultSpecified = defaultSpecified;
            this.defaultPageSize = defaultPageSize;
            this.maxSpecified = maxSpecified;
            this.maxPageSize = maxPageSize;
        }

        static Loading empty() {
            return new Loading(false, null, false, null);
        }

        static Loading copyOf(ResultLoadingPolicy source) {
            Objects.requireNonNull(source, "loadingPolicy");
            if (source instanceof DatabaseLoadingPolicy editable) {
                return editable.snapshot();
            }
            if (source instanceof Loading immutable) {
                return immutable;
            }
            return new Loading(true, source.getDefaultPageSize(), true, source.getMaxPageSize());
        }

        Loading overlay(Loading next) {
            return new Loading(true, next.defaultSpecified ? next.defaultPageSize : defaultPageSize,
                    true, next.maxSpecified ? next.maxPageSize : maxPageSize);
        }

        Loading validated() {
            if (defaultPageSize == null || defaultPageSize <= 0
                    || (maxPageSize != null && (maxPageSize <= 0 || maxPageSize < defaultPageSize))) {
                throw new IllegalArgumentException("Effective defaultPageSize must be positive and no greater than maxPageSize");
            }
            return this;
        }

        @Override
        public Integer getDefaultPageSize() {
            return defaultPageSize;
        }

        @Override
        public Integer getMaxPageSize() {
            return maxPageSize;
        }
    }
}
