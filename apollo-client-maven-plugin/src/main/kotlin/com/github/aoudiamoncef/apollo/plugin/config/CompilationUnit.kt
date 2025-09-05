package com.github.aoudiamoncef.apollo.plugin.config

import java.io.File

/**
 * A [CompilationUnit] is a single invocation of the compiler.
 */
class CompilationUnit {
    /**
     * Configures the [CompilerParams]
     */
    internal lateinit var compilerParams: CompilerParams

    /**
     * The name of the [CompilationUnit]
     */
    internal var name: String = ""

    /**
     * Whether to generate operation descriptors
     */
    internal val generateOperationDescriptors: Boolean = false

    /**
     * A json file containing a [Map]<[String], [com.apollographql.apollo3.compiler.operationoutput.OperationDescriptor]>
     */
    internal var operationOutputFile: File? = null

    /**
     * The directory where the generated models will be written
     */
    internal var outputDirectory: File? = null

    /**
     * The directory where to write the generated models debug code
     */
    internal var debugDirectory: File? = null

    fun isCompilationparamsInitialised() = ::compilerParams.isInitialized
}
