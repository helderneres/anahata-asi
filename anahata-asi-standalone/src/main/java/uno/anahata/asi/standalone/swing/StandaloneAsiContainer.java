/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.standalone.swing;

import uno.anahata.asi.AsiContainer;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.cli.CommandLineArgs;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.swing.agi.resources.DefaultResourceUI;
import uno.anahata.asi.swing.agi.resources.ResourceUiRegistry;

/**
 * A specialized {@link AsiContainer} for the standalone Swing application.
 * It handles the storage and parsing of command-line arguments to configure
 * initial agi sessions.
 * 
 * @author anahata
 */
@Slf4j
public class StandaloneAsiContainer extends AsiContainer {
    
    static {
        log.info("Performing global Standalone environment configuration...");
        // Register the universal/standalone resource UI strategy
        ResourceUiRegistry.getInstance().setResourceUI(new DefaultResourceUI());
    }

    /** The raw command-line arguments passed to the application. */
    private final String[] cmdLineArgs;
    
    /**
     * Constructs a new StandaloneAsiContainer.
     * 
     * @param cmdLineArgs The command-line arguments from the main entry point.
     */
    public StandaloneAsiContainer(String[] cmdLineArgs) {
        super("swing-standalone");
        this.cmdLineArgs = cmdLineArgs;
    }

    /**
     * {@inheritDoc}
     * <p>
     * In the standalone container, this hook is used to parse command-line 
     * arguments and apply them to the newly created agi session.
     * </p>
     * 
     * @param agi The newly created agi session.
     */
    @Override
    public void onAgiCreated(Agi agi) {
        super.onAgiCreated(agi);
        log.info("Parsing command-line arguments for new standalone agi.");
        CommandLineArgs.parse(agi, cmdLineArgs);
    }

    /** {@inheritDoc} */
    @Override
    protected AgiConfig createNewAgiConfig() {
        return new StandaloneAgiConfig(this);
    }
}
