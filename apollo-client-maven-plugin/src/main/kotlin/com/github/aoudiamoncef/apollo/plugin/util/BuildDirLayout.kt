package com.github.aoudiamoncef.apollo.plugin.util

import com.github.aoudiamoncef.apollo.plugin.config.CompilationUnit
import org.apache.maven.project.MavenProject
import java.io.File

object BuildDirLayout {
    internal fun operationOuput(
        project: MavenProject,
        compilationUnit: CompilationUnit,
    ): File =
        File(
            project.build.directory.plus("/generated/operationOutput/apollo/${compilationUnit.name}/operationOutput.json"),
        )

    internal fun metadata(
        project: MavenProject,
        compilationUnit: CompilationUnit,
    ): File =
        File(
            project.build.directory.plus(
                "/generated/metadata/apollo/${compilationUnit.name}/metadata.json",
            ),
        )

    internal fun sources(
        project: MavenProject,
        compilationUnit: CompilationUnit,
    ): File =
        File(
            project.build.directory.plus(
                "/generated-sources/apollo/${compilationUnit.name}",
            ),
        )

    internal fun debug(
        project: MavenProject,
        compilationUnit: CompilationUnit,
    ): File =
        File(
            project.build.directory.plus(
                "/generated-sources/apollo/${compilationUnit.name}/debug",
            ),
        )

    internal fun test(
        project: MavenProject,
        compilationUnit: CompilationUnit,
    ): File =
        File(
            project.build.directory.plus(
                "/generated-sources/apollo/${compilationUnit.name}/test",
            ),
        )
}
