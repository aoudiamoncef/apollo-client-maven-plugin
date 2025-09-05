package com.github.aoudiamoncef.apollo.plugin.util

import com.apollographql.apollo.compiler.TargetLanguage
import com.github.aoudiamoncef.apollo.plugin.config.CompilationUnit
import com.github.aoudiamoncef.apollo.plugin.config.CompilerParams
import com.github.aoudiamoncef.apollo.plugin.config.Introspection
import com.github.aoudiamoncef.apollo.plugin.config.Service
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProject
import org.apache.tools.ant.DirectoryScanner
import java.io.File
import java.nio.file.PathMatcher
import java.nio.file.Paths

object ConfigUtils {
    internal fun checkService(
        project: MavenProject,
        serviceName: String,
        service: Service,
    ): Service {
        service.introspection = if (service.isIntrospectionInitialised()) service.introspection else Introspection()
        service.compilationUnit =
            if (service.isCompilationUnitInitialised()) service.compilationUnit else CompilationUnit()
        if (service.sourceFolder == null) {
            val compilationUnitName =
                if (service.compilationUnit.name.isNotBlank()) service.compilationUnit.name else serviceName
            val sourceFolder = File("${project.basedir}/src/main/graphql/$compilationUnitName")
            sourceFolder.parentFile.mkdirs()

            service.sourceFolder = sourceFolder
        }

        return service
    }

    internal fun checkCompilationUnit(
        project: MavenProject,
        serviceName: String,
        compilationUnit: CompilationUnit,
    ): CompilationUnit {
        compilationUnit.compilerParams =
            if (compilationUnit.isCompilationparamsInitialised()) compilationUnit.compilerParams else CompilerParams()
        if (compilationUnit.name.isBlank()) {
            compilationUnit.name = serviceName
        }
        if (compilationUnit.generateOperationDescriptors) {
            if (compilationUnit.operationOutputFile == null) {
                val operationOuput = BuildDirLayout.operationOuput(project, compilationUnit)
                operationOuput.parentFile.mkdirs()
                operationOuput.createNewFile()

                compilationUnit.operationOutputFile = operationOuput
            } else {
                compilationUnit.operationOutputFile?.parentFile?.mkdirs()
                compilationUnit.operationOutputFile?.createNewFile()
            }
        } else {
            compilationUnit.operationOutputFile = null
        }
        if (compilationUnit.outputDirectory == null) {
            compilationUnit.outputDirectory = BuildDirLayout.sources(project, compilationUnit)
        }
        if (compilationUnit.debugDirectory == null) {
            compilationUnit.debugDirectory = BuildDirLayout.debug(project, compilationUnit)
        }

        return compilationUnit
    }

    internal fun checkIntrospection(
        project: MavenProject,
        service: Service,
    ): Introspection {
        val introspection = service.introspection
        if (introspection.enabled) {
            if (introspection.endpointUrl.isBlank()) {
                throw MojoExecutionException("introspection: must have a url")
            }
            if (introspection.schemaFile == null) {
                if (service.schemaPath.isNotBlank()) {
                    val schemaFile = File(service.schemaPath)
                    schemaFile.parentFile.mkdirs()
                    schemaFile.createNewFile()

                    introspection.schemaFile = schemaFile
                } else {
                    val schemaFile =
                        File("${project.basedir}/src/main/graphql/${service.compilationUnit.name}/schema.json")
                    schemaFile.parentFile.mkdirs()
                    schemaFile.createNewFile()

                    introspection.schemaFile = schemaFile
                }
            }
        }

        return introspection
    }

    internal fun checkCompilerParams(
        project: MavenProject,
        service: Service,
        compilerParams: CompilerParams,
        log: org.apache.maven.plugin.logging.Log,
    ): CompilerParams {
        compilerParams.rootFolders =
            if (compilerParams.rootFolders.isNotEmpty()) compilerParams.rootFolders else listOf(service.sourceFolder as File)

        if (compilerParams.metadataOutputFile == null) {
            compilerParams.metadataOutputFile = BuildDirLayout.metadata(project, service.compilationUnit)
        }

        if (compilerParams.generateApolloMetadata && compilerParams.alwaysGenerateTypesMatching.isEmpty()) {
            compilerParams.alwaysGenerateTypesMatching = setOf(".*")
        }

        if (compilerParams.packageName.isNullOrBlank()) {
            if (compilerParams.schemaPackageName.isNotBlank()) {
                compilerParams.packageName = compilerParams.schemaPackageName.removeSuffix("schema").plus("operation")
            } else {
                compilerParams.packageName = "${project.groupId}.apollo.client.${service.compilationUnit.name}.operation"
            }
        }

        if (compilerParams.schemaPackageName.isBlank()) {
            compilerParams.schemaPackageName = "${project.groupId}.apollo.client.${service.compilationUnit.name}.schema"
        }

        if (compilerParams.targetLanguage == TargetLanguage.JAVA) {
            if (compilerParams.generateAsInternal != null) {
                log.warn("generateAsInternal is not used in Java")
            }
            if (compilerParams.generateFilterNotNull != null) {
                log.warn("generateFilterNotNull is not used in Java")
            }
            if (compilerParams.sealedClassesForEnumsMatching != null) {
                log.warn("sealedClassesForEnumsMatching is not used in Java")
            }
        } else {
            if (compilerParams.nullableFieldStyle != null) {
                log.warn("nullableFieldStyle is not used in Kotlin")
            }
            if (compilerParams.generateModelBuilders != null) {
                log.warn("generateModelBuilders is not used in Kotlin")
            }
        }

        return compilerParams
    }

    internal fun findFilesByMatcher(
        files: Set<File>,
        matcher: PathMatcher,
    ): Set<File> =
        files
            .asSequence()
            .filter { file -> matcher.matches(file.toPath()) }
            .toSet()

    internal fun getSourceSetFiles(
        sourceFolder: File,
        includes: Set<String>,
        excludes: Set<String>,
    ): Set<File> {
        val scanner =
            DirectoryScanner().apply {
                basedir = sourceFolder
                isCaseSensitive = false
                setIncludes(includes.toTypedArray())
                addExcludes(excludes.toTypedArray())
                scan()
            }
        return scanner.includedFiles
            .asSequence()
            .map { path -> Paths.get(sourceFolder.path, path).toFile() }
            .filter { file -> file.exists() }
            .toSet()
    }

    internal fun resolveSchema(
        project: MavenProject,
        schemaPath: String,
        directories: Set<File>,
        sourceSetFiles: Set<File>,
    ): File? {
        if (schemaPath.isNotBlank()) {
            when {
                File(schemaPath).isRooted -> {
                    return File(schemaPath)
                }
                schemaPath.startsWith("..") -> {
                    return File("${project.basedir}/src/main/graphql/$schemaPath").normalize()
                }
                else -> {
                    require(sourceSetFiles.size <= 1) {
                        "ApolloGraphQL: duplicate(s) schema file(s) found:\n${sourceSetFiles.joinToString("\n") { it.absolutePath }}"
                    }
                    require(sourceSetFiles.size == 1) {
                        "ApolloGraphQL: cannot find a schema file at $schemaPath. Tried:\n${
                            sourceSetFiles.joinToString(
                                "\n",
                            ) { it.absolutePath }
                        }"
                    }

                    return sourceSetFiles.first()
                }
            }
        } else {
            val candidates =
                directories.flatMap { srcDir ->
                    srcDir
                        .walkTopDown()
                        .filter { it.name == "schema.json" || it.name == "schema.sdl" || it.name == "schema.graphqls" }
                        .toList()
                }

            require(candidates.size <= 1) {
                throw MojoExecutionException(
                    "duplicate Schema : ${
                        candidates.map { it }.joinToString(
                            ",",
                        )
                    }",
                )
            }

            return candidates.firstOrNull()
        }
    }

    fun File.isIntrospection() = extension == "json"
}
