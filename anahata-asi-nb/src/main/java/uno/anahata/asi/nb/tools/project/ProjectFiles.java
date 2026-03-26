/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.project;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents the file and folder structure of a NetBeans project.
 * <p>
 * This DTO provides a unified view of the project's root files and its 
 * primary source packages, allowing the ASI to navigate the codebase 
 * efficiently through a hierarchical tree structure.
 * </p>
 * 
 * @author anahata
 */
@Schema(description = "Represents the file and folder structure of a project, including root files and a detailed source tree.")
@Data
@AllArgsConstructor
public final class ProjectFiles {

    /** A list of files located directly in the project's root directory. */
    @Schema(description = "A list of files located directly in the project's root directory.")
    private final List<ProjectFile> rootFiles;

    /** A list of the names of all folders located in the project's root directory. */
    @Schema(description = "A list of the names of all folders located in the project's root directory.")
    private final List<String> rootFolderNames;

    /** A detailed, recursive tree structure of the project's primary source code and resource folders. */
    @Schema(description = "A detailed, recursive tree structure of the project's primary source code folders.")
    private final List<SourceFolder> sourceFolders;
}
