package com.github.aoudiamoncef.apollo.plugin.config

import java.io.File

/**
 * A [Service] represents a GraphQL endpoint and its associated schema.
 */
class Service {
    /**
     * Whether to use current service
     */
    internal val enabled: Boolean = true

    /**
     * Whether to add generated sources to root
     */
    internal val addSourceRoot: Boolean = true

    /**
     * Whether to add generated sources to root
     */
    internal val addTestSourceRoot: Boolean = false

    /**
     * Configures the [CompilationUnit]
     */
    internal lateinit var compilationUnit: CompilationUnit

    /**
     * Configures the [Introspection]
     */
    internal lateinit var introspection: Introspection

    /**
     * path to the folder containing the graphql files relative to the current source set.
     * The plugin will compile all graphql files accross all source sets
     *
     * By default sourceFolder is ".", i.e it uses everything under (${project.basedir}/src/main/graphql/)
     */
    internal var sourceFolder: File? = null

    /**
     * path to the schema file relative to the current source set (${project.basedir}/src/main/graphql/$schemaPath). The plugin
     * will search all possible source sets in the variant.
     *
     * By default, the plugin looks for a "schema.json|sdl|graphqls" file in the sourceFolders
     */
    internal val schemaPath: String = ""

    /**
     * Files to include from source set directory as in [java.nio.file.PathMatcher]
     */
    internal val includes: Set<String> = setOf("**/*.graphql", "**/*.gql", "**/*.json", "**/*.sdl")

    /**
     * Files to exclude from source set directory as in [java.nio.file.PathMatcher]
     */
    internal val excludes: Set<String> = emptySet()

    fun isIntrospectionInitialised() = ::introspection.isInitialized

    fun isCompilationUnitInitialised() = ::compilationUnit.isInitialized
}
