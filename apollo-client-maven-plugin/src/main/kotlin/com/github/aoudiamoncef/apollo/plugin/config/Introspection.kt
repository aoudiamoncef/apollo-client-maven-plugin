package com.github.aoudiamoncef.apollo.plugin.config

import java.io.File

/**
 * [Introspection] represents a GraphQL endpoint and its introspection query used to retrieve a schema.
 */
class Introspection {
    /**
     * Whether to use introspection
     */
    internal val enabled: Boolean = false

    /**
     * Whether to enable JSon schema pretty print
     */
    internal val prettyPrint: Boolean = false

    /**
     * The HTTP endpoint url
     *
     * This parameter is mandatory
     */
    internal val endpointUrl: String = ""

    /**
     * HTTP headers if any required to get the introspection response
     *
     * empty by default
     */
    internal val headers: Map<String, String> = emptyMap()

    /**
     * The identifier of the Apollo graph used to download the schema
     */
    internal val graph: String = ""

    /**
     * The identifier of the Apollo graph used to download the schema
     */
    /**
     * The file where to download the schema. By default it will be downloaded
     * in (${project.basedir}/src/main/graphql/schema.json)
     */
    internal var schemaFile: File? = null

    /**
     * The Apollo API key. See https://www.apollographql.com/docs/studio/api-keys/ for more information on how to get your API key
     */
    internal val key: String = ""

    /**
     * The variant of the Apollo graph used to download the schema
     */
    internal val graphVariant: String = ""

    /**
     * Time period in which our client should establish a connection with a target host
     *
     * The default timeout of 10 seconds
     */
    internal val connectTimeoutSeconds: Long = 10L

    /**
     * Maximum time of inactivity between two data packets when waiting for the server's response
     *
     * The default timeout of 10 seconds
     */
    internal val readTimeoutSeconds: Long = 10L

    /**
     * Maximum time of inactivity between two data packets when sending the request to the server.
     *
     * The default timeout of 10 seconds
     */
    internal val writeTimeoutSeconds: Long = 10L

    /**
     *  Whether to use self-signed certificate
     */
    internal val useSelfSignedCertificat: Boolean = false

    /**
     *  Whether to use Gzip compression
     */
    internal val useGzip: Boolean = false
}
