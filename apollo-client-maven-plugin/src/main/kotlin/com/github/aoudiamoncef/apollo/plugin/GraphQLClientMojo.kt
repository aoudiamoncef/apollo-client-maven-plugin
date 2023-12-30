package com.github.aoudiamoncef.apollo.plugin

import com.apollographql.apollo3.compiler.*
import com.apollographql.apollo3.compiler.introspection.toSchema
import com.github.aoudiamoncef.apollo.plugin.config.CompilationUnit
import com.github.aoudiamoncef.apollo.plugin.config.Introspection
import com.github.aoudiamoncef.apollo.plugin.config.Service
import com.github.aoudiamoncef.apollo.plugin.util.ConfigUtils
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
            val compilerParams = ConfigUtils.checkCompilerParams(project, service, compilationUnit.compilerParams)
            val introspection: Introspection = ConfigUtils.checkIntrospection(project, service)

            log.info("Generating service: ${it.key}")

            if (introspection.enabled) {
                log.info("Automatically generating introspection file from: ${introspection.endpointUrl}")
                introspection.schemaFile.let { schema ->
                    val okHttpClient = SchemaDownloader.newOkHttpClient(
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
            val sourceSetFiles = ConfigUtils.getSourceSetFiles(
                sourceFolder = service.sourceFolder as File,
                includes = service.includes,
                excludes = service.excludes,
            )
            val schemaMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{json,sdl,graphqls}")
            val directories = ConfigUtils.findFilesByMatcher(sourceSetFiles, schemaMatcher)
            val resolveSchema = ConfigUtils.resolveSchema(
                project = project,
                schemaPath = service.schemaPath,
                directories = directories,
                sourceSetFiles = sourceSetFiles,
            )

            log.info("Read querie(s)/fragment(s) files")
            val graphqlMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{graphql,gql,graphqls}")
            val graphqlFiles =
                ConfigUtils.findFilesByMatcher(sourceSetFiles, graphqlMatcher)
                    .takeIf { set -> set.isNotEmpty() }
                    ?: throw MojoExecutionException("No querie(s)/fragment(s) found")

            val operationOutputGenerator = if (compilerParams.operationIdGeneratorClass.isEmpty()) {
                OperationOutputGenerator.Default(OperationIdGenerator.Sha256)
            } else {
                val operationIdGenerator =
                    Class.forName(compilerParams.operationIdGeneratorClass).newInstance() as OperationIdGenerator
                OperationOutputGenerator.Default(operationIdGenerator)
            }

            val metadata = compilerParams.metadataFiles.toList().map { ApolloMetadata.readFrom(it).compilerMetadata }

            val scalarMapping = compilerParams.scalarsMapping
                .mapValues { scalarMapping ->
                    when (val expression = scalarMapping.value.expression) {
                        null -> ScalarInfo(scalarMapping.value.targetName)
                        else -> ScalarInfo(scalarMapping.value.targetName, ExpressionAdapterInitializer(expression))
                    }
                }

            ApolloCompiler.write(
                Options(
                    schema = resolveSchema!!.toSchema(),
                    outputDir = compilationUnit.outputDirectory as File,
                    testDir = compilationUnit.testDirectory as File,
                    debugDir = compilationUnit.debugDirectory as File,
                    executableFiles = graphqlFiles,
                    schemaPackageName = compilerParams.schemaPackageName,
                    packageNameGenerator = PackageNameGenerator.Flat(compilerParams.packageName as String),
                    alwaysGenerateTypesMatching = compilerParams.alwaysGenerateTypesMatching,
                    operationOutputGenerator = operationOutputGenerator,
                    incomingCompilerMetadata = metadata,
                    scalarMapping = scalarMapping,
                    codegenModels = compilerParams.codegenModels.label,
                    flattenModels = compilerParams.flattenModels,
                    useSemanticNaming = compilerParams.useSemanticNaming,
                    warnOnDeprecatedUsages = compilerParams.warnOnDeprecatedUsages,
                    failOnWarnings = compilerParams.failOnWarnings,
                    logger = compilerParams.logger,
                    generateAsInternal = compilerParams.generateAsInternal,
                    generateFilterNotNull = compilerParams.generateFilterNotNull,
                    generateFragmentImplementations = compilerParams.generateFragmentImplementations,
                    operationManifestFormat = compilerParams.operationManifestFormat,
                    operationManifestFile = compilationUnit.operationOutputFile,
                    generateResponseFields = compilerParams.generateResponseFields,
                    generateQueryDocument = compilerParams.generateQueryDocument,
                    generateSchema = compilerParams.generateSchema,
                    targetLanguage = compilerParams.targetLanguage,
                    generateTestBuilders = compilerParams.generateTestBuilders,
                    generateModelBuilders = compilerParams.generateModelBuilders,
                    generateDataBuilders = compilerParams.generateDataBuilders,
                    nullableFieldStyle = compilerParams.nullableFieldStyle,
                    sealedClassesForEnumsMatching = compilerParams.sealedClassesForEnumsMatching,
                    generateOptionalOperationVariables = compilerParams.generateOptionalOperationVariables,
                ),
            )

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
