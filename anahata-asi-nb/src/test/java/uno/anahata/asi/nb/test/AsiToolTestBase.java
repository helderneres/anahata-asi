/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 *
 * @author Helder
 */
public class AsiToolTestBase {

    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        doSetUp();
    }

    @SuppressWarnings("unused")
    @AfterEach
    void tearDown() {
        doTearDown();
    }

    protected void doSetUp() {
    }

    protected void doTearDown() {
    }

    /**
     * Functional interface for a code block that may throw a checked exception.
     */
    @FunctionalInterface
    public interface CheckedRunnable {

        void run() throws Exception;
    }

    /**
     * Functional interface for a supplier that may throw a checked exception.
     *
     * @param <T> the type of result supplied
     */
    @FunctionalInterface
    public interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    /**
     * Executes a {@link CheckedRunnable} and wraps any thrown exception into an
     * {@link AsiTestException}.
     *
     * @param runnable The code block to execute.
     */
    public static void wrapException(CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (AsiTestException e) {
            throw e;
        } catch (Exception e) {
            throw new AsiTestException(e);
        }
    }

    /**
     * Executes a {@link CheckedSupplier} and wraps any thrown exception into an
     * {@link AsiTestException}.
     *
     * @param <T> The return type.
     * @param supplier The code block to execute.
     * @return The result of the supplier.
     */
    public static <T> T wrapException(CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AsiTestException e) {
            throw e;
        } catch (Exception e) {
            throw new AsiTestException(e);
        }
    }

}
