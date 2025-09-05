package com.github.aoudiamoncef.apollo.plugin

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.introspection.toGQLDocument
import com.apollographql.apollo.ast.introspection.toIntrospectionSchema
import com.apollographql.apollo.ast.toGQLDocument
import com.apollographql.apollo.ast.toSchema
import com.apollographql.apollo.compiler.*
import com.apollographql.apollo.compiler.DocumentTransform
import com.apollographql.apollo.compiler.LayoutFactory
import com.apollographql.apollo.compiler.Transform
import com.apollographql.apollo.compiler.codegen.java.JavaOutput
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinOutput
import com.apollographql.apollo.compiler.codegen.writeTo
import com.apollographql.apollo.compiler.ir.IrOperations
import com.github.aoudiamoncef.apollo.plugin.config.CompilationUnit
import com.github.aoudiamoncef.apollo.plugin.config.Introspection
import com.github.aoudiamoncef.apollo.plugin.config.Service
import com.github.aoudiamoncef.apollo.plugin.util.ConfigUtils
import com.github.aoudiamoncef.apollo.plugin.util.ConfigUtils.isIntrospection
import com.github.aoudiamoncef.apollo.plugin.util.SchemaDownloader
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Generate queries classes for a GraphQl API
 */
@Mojo(
    name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true,
)
class GraphQLClientMojo : AbstractMojo() {
    /**
     * Maven project instance
     */
    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    /**
     * Whether to skip plugin execution
     */
    @Parameter
    private val skip: Boolean = false

    /**
     * registers services
     *
     * name: the name of the [Service], must be unique
     */
    @Parameter
    private lateinit var services: Map<String, Service>

    @OptIn(ApolloExperimental::class)
    @Throws(MojoExecutionException::class)
    override fun execute() {
        val start = System.nanoTime()
        if (skip) {
            log.info("Apollo GraphQL Client code generation skipping execution because skip option is true")
            return
        }

        if (!this::services.isInitialized) {
            log.error("Apollo GraphQL Client code generation failed because of wrong settings")
        }

        log.info("Apollo GraphQL Client code generation task started")
        services.entries.forEach service@{
            if (!it.value.enabled) {
                log.info("Skipping generation of service: ${it.key} because enabled option is false")
                return@service
            }

            val service: Service = ConfigUtils.checkService(project, it.key, it.value)
            val compilationUnit: CompilationUnit =
                ConfigUtils.checkCompilationUnit(project, it.key, service.compilationUnit)
            val compilerParams = ConfigUtils.checkCompilerParams(project, service, compilationUnit.compilerParams, log)
            val introspection: Introspection = ConfigUtils.checkIntrospection(project, service)

            log.info("Generating service: ${it.key}")

            if (introspection.enabled) {
                log.info("Automatically generating introspection file from: ${introspection.endpointUrl}")
                introspection.schemaFile.let { schema ->
                    val okHttpClient =
                        SchemaDownloader.newOkHttpClient(
                            connectTimeoutSeconds = introspection.connectTimeoutSeconds,
                            readTimeoutSeconds = introspection.readTimeoutSeconds,
                            writeTimeoutSeconds = introspection.writeTimeoutSeconds,
                            useSelfSignedCertificat = introspection.useSelfSignedCertificat,
                            useGzip = introspection.useGzip,
                        )
                    if (introspection.endpointUrl.isNotEmpty()) {
                        SchemaDownloader.downloadIntrospection(
                            schema = schema as File,
                            endpoint = introspection.endpointUrl,
                            headers = introspection.headers,
                            prettyPrint = introspection.prettyPrint,
                            okHttpClient = okHttpClient,
                        )
                    } else if (introspection.graph.isNotEmpty()) {
                        SchemaDownloader.downloadRegistry(
                            graph = introspection.graph,
                            schema = schema as File,
                            key = introspection.key,
                            variant = introspection.graphVariant,
                            prettyPrint = introspection.prettyPrint,
                            okHttpClient = okHttpClient,
                        )
                    }
                }
            }

            log.info("Read schema file")
            val sourceSetFiles =
                ConfigUtils.getSourceSetFiles(
                    sourceFolder = service.sourceFolder as File,
                    includes = service.includes,
                    excludes = service.excludes,
                )
            val schemaMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{json,sdl,graphqls}")
            val directories = ConfigUtils.findFilesByMatcher(sourceSetFiles, schemaMatcher)
            val resolveSchema =
                ConfigUtils.resolveSchema(
                    project = project,
                    schemaPath = service.schemaPath,
                    directories = directories,
                    sourceSetFiles = sourceSetFiles,
                )

            log.info("Read querie(s)/fragment(s) files")
            val graphqlMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{graphql,gql,graphqls}")
            val graphqlFiles =
                ConfigUtils
                    .findFilesByMatcher(sourceSetFiles, graphqlMatcher)
                    .takeIf { set -> set.isNotEmpty() }
                    ?: throw MojoExecutionException("No querie(s)/fragment(s) found")

            val operationOutputGenerator =
                if (compilerParams.operationIdGeneratorClass.isEmpty()) {
                    OperationOutputGenerator.Default(OperationIdGenerator.Sha256)
                } else {
                    val operationIdGenerator =
                        Class.forName(compilerParams.operationIdGeneratorClass).newInstance() as OperationIdGenerator
                    OperationOutputGenerator.Default(operationIdGenerator)
                }

            val layoutFactory =
                if (compilerParams.layoutFactoryClass.isEmpty()) {
                    null
                } else {
                    Class
                        .forName(compilerParams.layoutFactoryClass)
                        .getDeclaredConstructor()
                        .newInstance() as LayoutFactory
                }

            val irOperationsTransform =
                if (compilerParams.irOperationsTransformClass.isEmpty()) {
                    null
                } else {
                    Class
                        .forName(compilerParams.irOperationsTransformClass)
                        .getDeclaredConstructor()
                        .newInstance() as Transform<IrOperations>
                }

            val javaOutputTransform =
                if (compilerParams.javaOutputTransformClass.isEmpty()) {
                    null
                } else {
                    Class
                        .forName(compilerParams.javaOutputTransformClass)
                        .getDeclaredConstructor()
                        .newInstance() as Transform<JavaOutput>
                }

            val kotlinOutputTransform =
                if (compilerParams.kotlinOutputTransformClass.isEmpty()) {
                    null
                } else {
                    Class
                        .forName(compilerParams.kotlinOutputTransformClass)
                        .getDeclaredConstructor()
                        .newInstance() as Transform<KotlinOutput>
                }

            val documentTransform =
                if (compilerParams.documentTransformClass.isEmpty()) {
                    null
                } else {
                    Class
                        .forName(compilerParams.documentTransformClass)
                        .getDeclaredConstructor()
                        .newInstance() as DocumentTransform
                }

            val scalarMapping =
                compilerParams.scalarsMapping
                    .mapValues { scalarMapping ->
                        when (val expression = scalarMapping.value.expression) {
                            null -> ScalarInfo(scalarMapping.value.targetName)
                            else -> ScalarInfo(scalarMapping.value.targetName, ExpressionAdapterInitializer(expression))
                        }
                    }

            val codegenOptions =
                buildCodegenOptions(
                    targetLanguage = compilerParams.targetLanguage,
                    useSemanticNaming = compilerParams.useSemanticNaming,
                    operationManifestFormat = compilerParams.operationManifestFormat,
                    generateSchema = compilerParams.generateSchema,
                    sealedClassesForEnumsMatching = compilerParams.sealedClassesForEnumsMatching,
                    generateAsInternal = compilerParams.generateAsInternal,
                    generateFilterNotNull = compilerParams.generateFilterNotNull,
                    generateModelBuilders = compilerParams.generateModelBuilders,
                    nullableFieldStyle = compilerParams.nullableFieldStyle,
                    generateFragmentImplementations = compilerParams.generateFragmentImplementations,
                    generateQueryDocument = compilerParams.generateQueryDocument,
                    packageName = compilerParams.packageName,
                )

            val schema =
                if (resolveSchema!!.isIntrospection()) {
                    resolveSchema.toIntrospectionSchema().toGQLDocument().toSchema()
                } else {
                    resolveSchema.toGQLDocument().toSchema()
                }

            val codegenSchema =
                CodegenSchema(
                    schema = schema,
                    normalizedPath = service.schemaPath,
                    scalarMapping = scalarMapping,
                    generateDataBuilders = compilerParams.generateDataBuilders,
                )

            val irOptions =
                buildIrOptions(
                    flattenModels = compilerParams.flattenModels,
                    warnOnDeprecatedUsages = compilerParams.warnOnDeprecatedUsages,
                    failOnWarnings = compilerParams.failOnWarnings,
                    generateOptionalOperationVariables = compilerParams.generateOptionalOperationVariables,
                    alwaysGenerateTypesMatching = compilerParams.alwaysGenerateTypesMatching,
                    codegenModels = compilerParams.codegenModels.label,
                )

            ApolloCompiler
                .buildSchemaAndOperationsSources(
                    codegenSchema = codegenSchema,
                    executableFiles = graphqlFiles.toInputFiles(),
                    irOptions = irOptions,
                    codegenOptions = codegenOptions,
                    layoutFactory = layoutFactory,
                    operationOutputGenerator = operationOutputGenerator,
                    irOperationsTransform = irOperationsTransform,
                    javaOutputTransform = javaOutputTransform,
                    kotlinOutputTransform = kotlinOutputTransform,
                    documentTransform = documentTransform,
                    logger = compilerParams.logger,
                    operationManifestFile = compilationUnit.operationOutputFile,
                ).writeTo(compilationUnit.outputDirectory as File, true, null)

            if (service.addSourceRoot) {
                val generatedSourcePath = compilationUnit.outputDirectory?.canonicalPath
                log.info("Add the compiled sources from $generatedSourcePath to project root")
                project.addCompileSourceRoot(generatedSourcePath)
            }

            if (service.addTestSourceRoot) {
                val generatedSourcePath = compilationUnit.outputDirectory?.canonicalPath
                log.info("Add the test compiled sources from $generatedSourcePath to project root")
                project.addTestCompileSourceRoot(generatedSourcePath)
            }
        }
        log.info("Apollo GraphQL Client code generation task finished")

        val finish = System.nanoTime()
        val timeElapsed = (finish - start).toDouble() / 1000000000
        log.info("Total time: ${String.format("%.3f", timeElapsed)} s")
    }
}
