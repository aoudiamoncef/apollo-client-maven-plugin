package com.lahzouz.java.graphql.client.tests

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.*
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import com.apollographql.apollo3.network.okHttpClient
import com.coxautodev.graphql.tools.SchemaParser
import com.lahzouz.apollo.graphql.client.GetAuthorsQuery
import com.lahzouz.apollo.graphql.client.GetBooksQuery
import graphql.schema.GraphQLSchema
import graphql.servlet.DefaultGraphQLSchemaProvider
import graphql.servlet.GraphQLInvocationInputFactory
import graphql.servlet.SimpleGraphQLHttpServlet
import io.undertow.Undertow
import io.undertow.servlet.Servlets
import io.undertow.servlet.util.ImmediateInstanceFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.net.InetSocketAddress
import javax.servlet.Servlet

@TestInstance(PER_CLASS)
class ApolloClientMavenPluginTest {

    private lateinit var server: Undertow
    private var port: Int = 0
    private lateinit var client: ApolloClient

    @BeforeAll
    fun setupSpec() {
        val libSchema = SchemaParser.newParser()
            .file("schema.graphqls")
            .resolvers(Query())
            .build()
            .makeExecutableSchema()

        val servlet = createServlet(libSchema)

        val servletBuilder = Servlets.deployment()
            .setClassLoader(javaClass.classLoader)
            .setContextPath("/")
            .setDeploymentName("test")
            .addServlets(
                Servlets.servlet(
                    "GraphQLServlet",
                    Servlet::class.java,
                    ImmediateInstanceFactory(servlet as Servlet),
                ).addMapping("/graphql/*"),
            )

        val manager = Servlets.defaultContainer().addDeployment(servletBuilder)
        manager.deploy()
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(manager.start()).build()
        server.start()

        val inetSocketAddress: InetSocketAddress = server.listenerInfo[0].address as InetSocketAddress
        port = inetSocketAddress.port

        val longCustomScalarTypeAdapter = object : Adapter<Long> {
            override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters): Long {
                return reader.nextLong() // Assuming the Long value is directly parseable from JSON
            }

            override fun toJson(writer: JsonWriter, customScalarAdapters: CustomScalarAdapters, value: Long) {
                writer.value(value) // Write the Long value to JSON
            }
        }

        client = ApolloClient.Builder()
            .serverUrl("http://127.0.0.1:$port/graphql")
            .addCustomScalarAdapter(com.lahzouz.apollo.graphql.client.type.Long.type, longCustomScalarTypeAdapter)
            .okHttpClient(OkHttpClient())
            .build()
    }

    @AfterAll
    fun cleanupSpec() {
        server.stop()
    }

    @Test
    @DisplayName("generated book query returns data")
    fun bookQueryTest(): Unit = runBlocking {
        val response = client.query(GetBooksQuery()).execute()
        assertThat(response.data?.books).isNotEmpty.hasSize(4)
    }

    @Test
    @DisplayName("generated author query returns data")
    fun authorQueryTest(): Unit = runBlocking {
        val response = client.query(GetAuthorsQuery()).execute()
        assertThat(response.data?.authors).isNotEmpty.hasSize(2)
    }

    private fun createServlet(schema: GraphQLSchema): SimpleGraphQLHttpServlet {
        val schemaProvider = DefaultGraphQLSchemaProvider(schema)
        val invocationInputFactory = GraphQLInvocationInputFactory.newBuilder(schemaProvider).build()
        return SimpleGraphQLHttpServlet.newBuilder(invocationInputFactory).build()
    }
}
