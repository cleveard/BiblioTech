package com.github.cleveard.bibliotech.codegen

import com.github.cleveard.bibliotech.annotations.EnvironmentValues
import com.google.auto.service.AutoService
import java.io.File
import java.lang.Exception
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes("com.github.cleveard.bibliotech.annotations.EnvironmentValues")
@AutoService(Processor::class) // For registering the service
@SupportedSourceVersion(SourceVersion.RELEASE_8) // to support Java 8
@SupportedOptions(EnvironmentProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class EnvironmentProcessor: AbstractProcessor() {
    override fun getSupportedSourceVersion(): SourceVersion {
        // processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "getSupportedSourceVersion")
        return SourceVersion.latest()
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        // processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "getSupportedAnnotationTypes")
        return mutableSetOf(EnvironmentValues::class.java.name)
    }

    override fun process(types: MutableSet<out TypeElement>?, roundEnvironment: RoundEnvironment?): Boolean {
        // processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "process")
        // Process each class with an EnvironmentValues annotation
        roundEnvironment?.getElementsAnnotatedWith(EnvironmentValues::class.java)
            ?.forEach {
                // Generate a compile error if something goes wrong.
                try {
                    // Create the filename from the class name
                    val fileName = it.simpleName.toString()
                    // Put the new class in the same package as this class
                    val packageName = processingEnv.elementUtils.getPackageOf(it).toString()
                    // Get the annotation
                    val annotation = it.getAnnotation(EnvironmentValues::class.java)
                    // Build the map
                    generateMap(fileName, packageName, annotation.vars)
                } catch (e: Exception) {
                    // Generate a compile error if something goes wrong.
                    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "EnvironmentValue Exception $e")
                }
            }
        return true
    }

    /**
     * Create the file with the environment values in it
     * @param filename The base name for the file and map
     * @param packageName The name of the package for the map
     * @param variables The names of the environment variables we want at runtime
     */
    private fun generateMap(
        filename: String,
        packageName: String,
        variables: Array<out String>
    ) {
        // The name of the file and map
        val outName = "${filename}_Environment"
        // Create the content of the map
        val content = createContent(packageName, outName, variables)

        // Create the file containing the map
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        val file = File(kaptKotlinGeneratedDir, "$outName.kt")
        // processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Generate Environment Map $outName in ${file.absolutePath}")

        // Write the content to the file
        file.writeText(content)
    }

    /**
     * Create the content for the environment map
     * @param packageName The name of the package for the map
     * @param name The name of the map
     * @param variables The environment variables to add to the map
     */
    private fun createContent(packageName: String, name: String, variables: Array<out String>): String {
        // Create a list of the variables and their values. The list
        // contains strings formatted as arguments of mapOf()
        val values = variables.mapNotNull { variable ->
            // Create the string for a variable. Generate an error message
            // when the variable doesn't exist
            System.getenv(variable)?.let { "\"$variable\" to \"$it\"" }
                ?: processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "$variable: Missing environment variable").let { null }
        }.toList()

        // Return the content of the map
        return """
            |package $packageName
            |
            |class $name {
            |    companion object {
            |        private val envMap = mapOf(
            |            ${values.joinToString(",\n            ")}
            |        )
            |        operator fun get(key: String): String? = envMap[key]
            |    }
            |}
            |""".trimMargin()
    }

    companion object {
        // Option used to get directory for map file
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}