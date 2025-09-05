package com.github.aoudiamoncef.apollo.plugin.config

/**
 * ScalarMapping contains all the parameters needed to generate scalar mappings with the apollo compiler.
 */
class ScalarMapping {
    /**
     * The fully qualified Java or Kotlin name of the type the scalar is mapped to.
     *
     * For example: `com.example.Date`
     */
    internal lateinit var targetName: String

    /**
     * An expression that will be used by the codegen to get an adapter for the given scalar.
     *
     * For example in Kotlin:
     * - `com.example.DateAdapter` (a top level property or object)
     * - `com.example.DateAdapter` (create a new instance every time)
     * Or in Java:
     * - `com.example.DateAdapter.INSTANCE` (a top level property or object)
     * - `new com.example.DateAdapter()` (create a new instance every time)
     */
    internal val expression: String? = null
}
