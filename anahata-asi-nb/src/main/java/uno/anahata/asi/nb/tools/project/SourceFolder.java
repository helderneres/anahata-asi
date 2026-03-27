/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a folder within a source directory tree, containing files and subfolders.
 * This is a Java 8-compatible, immutable data class.
 *
 * @author Anahata
 */
@Schema(description = "Represents a folder node within a source directory tree, containing a list of its own files and subfolders.")
@Data
@AllArgsConstructor
public final class SourceFolder {
    
    /** The display name of the source folder, if it differs from the physical folder name. */
    @Schema(description = "The display name of the source folder (e.g., 'Source Packages'). This is only included if it differs from the actual folder name.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String displayName;

    /** The absolute physical path to the folder on the host filesystem. */
    @Schema(description = "The absolute path to the folder.")
    private final String path;

    /** The total recursive size in bytes of all contents within this folder. */
    @Schema(description = "The total size of all files within this folder and all its subfolders, calculated recursively.")
    private final long recursiveSize;

    /** The list of files contained directly within this directory. */
    @Schema(description = "A list of files contained directly within this folder.")
    private final List<ProjectFile> files;

    /** The list of immediate sub-directories. */
    @Schema(description = "A list of subfolders contained directly within this folder.")
    private final List<SourceFolder> subfolders;
}