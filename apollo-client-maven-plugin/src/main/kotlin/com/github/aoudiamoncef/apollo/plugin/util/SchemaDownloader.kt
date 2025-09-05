package com.github.aoudiamoncef.apollo.plugin.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.aoudiamoncef.apollo.plugin.util.ConfigUtils.isIntrospection
import com.squareup.moshi.JsonWriter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object SchemaDownloader {
    fun newOkHttpClient(
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        writeTimeoutSeconds: Long,
        useSelfSignedCertificat: Boolean,
        useGzip: Boolean,
    ): OkHttpClient {
        val okhttpClientBuilder =
            if (useSelfSignedCertificat) {
                UnsafeOkHttpClient.getUnsafeOkHttpClient()
            } else {
                OkHttpClient.Builder()
            }

        if (useGzip) {
            okhttpClientBuilder.addInterceptor(GzipRequestInterceptor())
        }

        return okhttpClientBuilder
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    private fun executeQuery(
        query: String,
        variables: String? = null,
        url: String,
        headers: Map<String, String>,
        okHttpClient: OkHttpClient,
    ): Response {
        val byteArrayOutputStream = ByteArrayOutputStream()
        JsonWriter
            .of(byteArrayOutputStream.sink().buffer())
            .apply {
                beginObject()
                name("query")
                value(query)
                if (variables != null) {
                    name("variables")
                    value(variables)
                }
                endObject()
                flush()
            }

        val body = byteArrayOutputStream.toByteArray().toRequestBody("application/json".toMediaTypeOrNull())
        val request =
            Request
                .Builder()
                .post(body)
                .apply {
                    addHeader("User-Agent", "ApolloMavenPlugin")
                    headers.entries.forEach {
                        addHeader(it.key, it.value)
                    }
                }.header("apollographql-client-name", "apollo-maven-plugin")
                .header("apollographql-client-version", com.apollographql.apollo.compiler.APOLLO_VERSION)
                .url(url)
                .build()

        val response = okHttpClient.newCall(request).execute()

        check(response.isSuccessful) {
            "Cannot get schema from $url: ${response.code}:\n${response.body?.string()}"
        }

        return response
    }

    fun downloadIntrospection(
        endpoint: String,
        schema: File,
        headers: Map<String, String>,
        prettyPrint: Boolean,
        okHttpClient: OkHttpClient,
    ) {
        val response = executeQuery(introspectionQuery, null, endpoint, headers, okHttpClient)

        writeResponse(schema, response, prettyPrint)
    }

    fun downloadRegistry(
        graph: String,
        schema: File,
        key: String,
        variant: String,
        prettyPrint: Boolean,
        okHttpClient: OkHttpClient,
    ) {
        val query =
            """
            query DownloadSchema(${'$'}graphID: ID!, ${'$'}variant: String!) {
              service(id: ${'$'}graphID) {
                variant(name: ${'$'}variant) {
                  activeSchemaPublish {
                    schema {
                      document
                    }
                  }
                }
              }
            }
            """.trimIndent()
        val variables =
            """
            {
              "graphID": "$graph",
              "variant": "$variant"
            }
            """.trimIndent()

        val response =
            executeQuery(query, variables, "https://graphql.api.apollographql.com/api/graphql", mapOf("x-api-key" to key), okHttpClient)

        val responseString = response.body.use { it?.string() }

        val jsonNode = mapper.readTree(responseString)
        val document =
            jsonNode
                .path("data")
                .path("service")
                .path("variant")
                .path("activeSchemaPublish")
                .path("schema")
                .path("document")
                .asText(null)

        check(document != null) {
            "Cannot retrieve document from $responseString\nCheck graph id and variant"
        }

        writeResponse(schema, document, prettyPrint)
    }

    private inline fun <reified T> Any?.cast() = this as? T

    private fun writeResponse(
        schema: File,
        response: Response,
        prettyPrint: Boolean,
    ) {
        schema.parentFile?.mkdirs()
        response.body.use { responseBody ->
            if (schema.isIntrospection()) {
                schema.writeText(pretify(responseBody?.string(), prettyPrint))
            } else {
                responseBody?.let { schema.writeText(it.toString()) }
            }
        }
    }

    private fun writeResponse(
        schema: File,
        document: String?,
        prettyPrint: Boolean,
    ) {
        schema.parentFile?.mkdirs()
        if (schema.isIntrospection()) {
            schema.writeText(pretify(document, prettyPrint))
        } else {
            document?.let { schema.writeText(it) }
        }
    }

    private fun pretify(
        document: String?,
        prettyPrint: Boolean,
    ): String {
        if (document.isNullOrBlank()) {
            throw IllegalArgumentException("document: is null or blank")
        }
        if (prettyPrint) {
            val json: JsonNode = mapper.readValue(document, JsonNode::class.java)
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
        }

        return document
    }

    private val mapper = ObjectMapper()

    private val introspectionQuery =
        """
        query IntrospectionQuery {
          __schema {
            queryType { name }
            mutationType { name }
            subscriptionType { name }
            types {
              ...FullType
            }
            directives {
              name
              description
              locations
              args {
                ...InputValue
              }
            }
          }
        }

        fragment FullType on __Type {
          kind
          name
          description
          fields(includeDeprecated: true) {
            name
            description
            args {
              ...InputValue
            }
            type {
              ...TypeRef
            }
            isDeprecated
            deprecationReason
          }
          inputFields {
            ...InputValue
          }
          interfaces {
            ...TypeRef
          }
          enumValues(includeDeprecated: true) {
            name
            description
            isDeprecated
            deprecationReason
          }
          possibleTypes {
            ...TypeRef
          }
        }

        fragment InputValue on __InputValue {
          name
          description
          type { ...TypeRef }
          defaultValue
        }

        fragment TypeRef on __Type {
          kind
          name
          ofType {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent()
}
