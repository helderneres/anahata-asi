/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers lifecycle state semantics using only IDE/JDBC mocks, with no dialog.
 */
class DatabaseConnectionCommandsTest {

    private ConnectionManager manager;
    private DatabaseConnection registered;
    private Connection jdbc;
    private AtomicReference<Connection> current;

    @BeforeEach
    void setUp() {
        manager = mock(ConnectionManager.class);
        registered = mock(DatabaseConnection.class);
        jdbc = mock(Connection.class);
        current = new AtomicReference<>();
        when(registered.getJDBCConnection()).thenAnswer(invocation -> current.get());
    }

    @Test
    void synchronousSuccessRequiresObservedHandleAndNeverOpensDialog() throws Exception {
        when(manager.connect(registered)).thenAnswer(invocation -> {
            current.set(jdbc);
            return true;
        });
        ConnectionCommandInfo outcome = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.SUCCEEDED, outcome.getStatus());
        assertTrue(outcome.isSuccess());
        assertTrue(outcome.isConnected());
        DatabaseConnectionCommands.execute(manager, registered, true);
        verify(manager, times(1)).connect(registered);
        verify(manager, never()).showConnectionDialog(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void alreadySatisfiedOutcomeUsesOneCoherentObservation() {
        when(registered.getJDBCConnection()).thenReturn(jdbc, null);
        ConnectionCommandInfo result = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.SUCCEEDED, result.getStatus());
        assertTrue(result.isSuccess());
        assertTrue(result.isConnected());
        verify(registered, times(1)).getJDBCConnection();
        verifyNoInteractions(manager);
    }

    @Test
    void completedConnectionUsesTheSameObservationForStatusAndConnected() throws Exception {
        when(registered.getJDBCConnection()).thenReturn(null, jdbc, null);
        when(manager.connect(registered)).thenReturn(true);
        ConnectionCommandInfo result = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.SUCCEEDED, result.getStatus());
        assertTrue(result.isSuccess());
        assertTrue(result.isConnected());
        verify(registered, times(2)).getJDBCConnection();
    }

    @Test
    void missingConfigurationAndUnconfirmedCompletionAreNotTerminalFailures() throws Exception {
        when(manager.connect(registered)).thenReturn(false);
        ConnectionCommandInfo interaction = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.REQUIRES_INTERACTION, interaction.getStatus());
        assertFalse(interaction.isSuccess());
        when(manager.connect(registered)).thenReturn(true);
        ConnectionCommandInfo unknown = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.UNKNOWN, unknown.getStatus());
        assertFalse(unknown.isSuccess());
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void confirmedFailureIsDistinctAndDoesNotExposeDriverText() throws Exception {
        when(manager.connect(registered)).thenThrow(new DatabaseException("private-driver-text"));
        ConnectionCommandInfo failed = DatabaseConnectionCommands.execute(manager, registered, true);
        assertEquals(ConnectionCommandStatus.FAILED, failed.getStatus());
        assertFalse(failed.isSuccess());
        assertFalse(failed.getMessage().contains("private-driver-text"));
        verify(manager, never()).showConnectionDialog(any());
    }

    @Test
    void eventDispatchThreadDoesNotStartOrWaitForLifecycleWork() throws Exception {
        AtomicReference<ConnectionCommandInfo> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(DatabaseConnectionCommands.execute(manager, registered, true)));
        assertEquals(ConnectionCommandStatus.REQUIRES_INTERACTION, result.get().getStatus());
        assertFalse(result.get().isSuccess());
        verifyNoInteractions(manager, jdbc);
    }

    @Test
    void concurrentConnectReturnsPendingWithoutRepeatingTheCommand() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        when(manager.connect(registered)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            current.set(jdbc);
            return true;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> DatabaseConnectionCommands.execute(manager, registered, true));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            ConnectionCommandInfo pending = DatabaseConnectionCommands.execute(manager, registered, true);
            assertEquals(ConnectionCommandStatus.PENDING, pending.getStatus());
            assertFalse(pending.isSuccess());
            assertFalse(pending.isConnected());
            ConnectionCommandInfo otherDirection = DatabaseConnectionCommands.execute(manager, registered, false);
            assertEquals(ConnectionCommandStatus.PENDING, otherDirection.getStatus());
            verify(manager, never()).disconnect(any());
            complete.countDown();
            assertEquals(ConnectionCommandStatus.SUCCEEDED, first.get(5, TimeUnit.SECONDS).getStatus());
            verify(manager, times(1)).connect(registered);
            verify(manager, never()).showConnectionDialog(any());
        } finally {
            complete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void disconnectUsesIdeOwnershipAndDoesNotCloseJdbcDirectly() throws Exception {
        current.set(jdbc);
        doAnswer(invocation -> {
            current.set(null);
            return null;
        }).when(manager).disconnect(registered);
        ConnectionCommandInfo result = DatabaseConnectionCommands.execute(manager, registered, false);
        assertEquals(ConnectionCommandStatus.SUCCEEDED, result.getStatus());
        assertFalse(result.isConnected());
        DatabaseConnectionCommands.execute(manager, registered, false);
        verify(manager, times(1)).disconnect(registered);
        verify(jdbc, never()).close();
    }
}
