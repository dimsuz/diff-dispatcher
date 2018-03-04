package com.github.dimsuz.diffdispatcher.processor

import com.github.dimsuz.diffdispatcher.annotations.DiffElement
import com.squareup.javapoet.*
import java.util.*
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

private const val RECEIVER_PARAMETER_NAME = "diffReceiver"

class Processor : AbstractProcessor() {
    private lateinit var logger: Logger
    private lateinit var typeUtils: Types
    private lateinit var filer: Filer

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        logger = Logger(processingEnv.messager)
        typeUtils = processingEnv.typeUtils
        filer = processingEnv.filer
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(DiffElement::class.java.canonicalName)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        for (element in roundEnv.getElementsAnnotatedWith(DiffElement::class.java)) {
            if (element.kind != ElementKind.CLASS) {
                logger.error("${DiffElement::class.java.simpleName} can only be applied to class")
                return true
            }

            val targetElement = element as TypeElement
            val receiverElement = getReceiverElement(targetElement) ?: return true // error should already be printed
            // receiver interface method parameters grouped by method they belong to
            val receiverParameters = getReceiverFields(receiverElement)

            if (!checkTargetHasFieldsRequestedByReceiver(targetElement, receiverElement, receiverParameters)) {
                return true
            }
            if (!checkReceiverArgumentNullabilityConsistent(receiverElement)) {
                return true
            }
            if (!checkNullabilityMatches(targetElement, receiverParameters)) {
                return true
            }
            warnIfMissingHashCodeEquals(targetElement, receiverParameters.keys)

            val dispatcherImplSuffix = "_Generated"
            val dispatcherTypeSpec = generateDispatcherInterface(targetElement, receiverElement, dispatcherImplSuffix)
            generateDispatcher(dispatcherTypeSpec, targetElement, receiverElement, receiverParameters,
                dispatcherImplSuffix)
        }

        return true
    }

    private fun generateDispatcherInterface(
        targetElement: TypeElement,
        receiverElement: TypeElement,
        dispatcherImplSuffix: String
    ): TypeSpec {
        val targetTypeName = TypeName.get(targetElement.asType())
        val interfaceName = "${targetElement.simpleName}DiffDispatcher"
        val typeSpec = TypeSpec.interfaceBuilder(interfaceName)
            .addModifiers(Modifier.PUBLIC)
            .addMethod(MethodSpec.methodBuilder("dispatch")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(ParameterSpec.builder(targetTypeName, "newState")
                    .addAnnotation(Nonnull::class.java)
                    .build())
                .addParameter(ParameterSpec.builder(targetTypeName, "previousState")
                    .addAnnotation(Nullable::class.java)
                    .build())
                .build()
            )
            .addType(generateDispatcherBuilder(interfaceName, receiverElement, dispatcherImplSuffix))
            .build()

        JavaFile.builder(targetElement.enclosingPackageName, typeSpec)
            .build()
            .writeTo(filer)
        return typeSpec
    }

    private fun generateDispatcher(
        superInterface: TypeSpec,
        targetElement: TypeElement,
        receiverElement: TypeElement,
        receiverParameters: Map<TargetField, List<ExecutableElement>>,
        dispatcherImplSuffix: String
    ): TypeSpec {
        val packageName = targetElement.enclosingPackageName
        val dispatchMethodSpec = superInterface.methodSpecs.single()
        val constructorSpec = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(TypeName.get(receiverElement.asType()), "receiver")
            .addStatement("this.\$N = \$N", "receiver", "receiver")
            .build()

        val typeSpec = TypeSpec.classBuilder("${superInterface.name}$dispatcherImplSuffix")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(
                ClassName.get(packageName, superInterface.name))
            .addField(TypeName.get(receiverElement.asType()), "receiver", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(constructorSpec)
            .addMethod(dispatchMethodSpec.override()
                .addCode(generateDispatcherStatements(
                    dispatchMethodSpec.parameters[0],
                    dispatchMethodSpec.parameters[1],
                    receiverElement,
                    receiverParameters
                )).build())
            .addMethods(generateEqualsHelpers(receiverParameters.keys))
            .build()

        JavaFile.builder(packageName, typeSpec)
            .build()
            .writeTo(filer)

        return typeSpec
    }

    private fun generateDispatcherBuilder(
        interfaceName: String,
        receiverElement: TypeElement,
        dispatcherImplSuffix: String
    ): TypeSpec {
        val builderClassName = "Builder"
        val receiverParamName = "receiver"
        val receiverSetterName = "target"
        val typeSpec = TypeSpec.classBuilder(builderClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .addField(TypeName.get(receiverElement.asType()), receiverParamName, Modifier.PRIVATE)
            .addMethod(MethodSpec.methodBuilder(receiverSetterName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(ParameterSpec.builder(TypeName.get(receiverElement.asType()), receiverParamName)
                    .addAnnotation(Nonnull::class.java)
                    .build())
                .addAnnotation(Nonnull::class.java)
                .returns(ClassName.get(receiverElement.enclosingPackageName, interfaceName, builderClassName))
                .addStatement("this.\$1N = \$1N", receiverParamName)
                .addStatement("return this")
                .build())
            .addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Nonnull::class.java)
                .returns(ClassName.get(receiverElement.enclosingPackageName, interfaceName))
                .beginControlFlow("if (this.\$N == null)", receiverParamName)
                .addStatement(
                    "throw new \$T(\$S)",
                    IllegalStateException::class.java,
                    "no \"$receiverParamName\" specified, use \"$receiverSetterName\" Builder's method to set it")
                .endControlFlow()
                .addStatement("return new \$N\$N(this.\$N)", interfaceName, dispatcherImplSuffix, receiverParamName)
                .build())
        return typeSpec.build()
    }

    private fun generateDispatcherStatements(
        newStateArgSpec: ParameterSpec,
        prevStateArgSpec: ParameterSpec,
        receiverElement: TypeElement,
        receiverParameters: Map<TargetField, List<ExecutableElement>>): CodeBlock {

        return CodeBlock.builder()
            .beginControlFlow("if (\$N == null)", prevStateArgSpec)
            .apply {
                receiverElement.enclosedMethods
                    .map { generateDispatchCallStatement(newStateArgSpec, it) }
                    .forEach { addStatement(it) }
            }
            .endControlFlow()
            .beginControlFlow("else")
            .apply {
                // if some parameter is used in more than 1 receiver method, cache computation of its
                // "is changed" state in case it's costly
                val cacheDiffComputation = receiverParameters
                    .filter { (_, parentMethods) -> parentMethods.size > 1  }
                    .keys
                val generatedComputations = mutableSetOf<TargetField>()

                receiverElement.enclosedMethods
                    .map {
                        generateDiffableDispatchCallStatement(
                            newStateArgSpec,
                            prevStateArgSpec,
                            cacheDiffComputation,
                            generatedComputations,
                            it
                        )
                    }
                    .forEach { add(it) }
            }
            .endControlFlow()
            .build()
    }

    private fun generateDiffableDispatchCallStatement(
        newStateArgSpec: ParameterSpec,
        prevStateArgSpec: ParameterSpec,
        cacheDiffStatements: Set<TargetField>,
        generatedCachedStatements: MutableSet<TargetField>,
        element: ExecutableElement
    ): CodeBlock {
        val dispatchBlock = CodeBlock.builder()
        val checkStatements = ArrayList<CodeBlock>()
        element.parameters.forEach {
            val tf = TargetField(it)
            if (cacheDiffStatements.contains(tf)) {
                generateCachedChangeCheckIfNeeded(tf, newStateArgSpec, prevStateArgSpec, generatedCachedStatements)
                    ?.let { dispatchBlock.addStatement(it) }

                checkStatements.add(CodeBlock.of("${tf.name}Changed"))
            } else {
                checkStatements.add(generateComparison(tf, newStateArgSpec, prevStateArgSpec))
            }
        }
        return dispatchBlock
            .beginControlFlow("if (\$N)", CodeBlock.join(checkStatements, " || ").toString())
            .addStatement(generateDispatchCallStatement(newStateArgSpec, element))
            .endControlFlow()
            .build()
    }

    /**
     * Generates a change check "cached" int variable, for example:
     *
     * ```
     * boolean somePrimitiveFieldChanged = newState.someField != previousState.someField;
     * boolean someObjectFieldChanged = !newState.someField.equals(previousState.someField);
     * ```
     */
    private fun generateCachedChangeCheckIfNeeded(
        parameter: TargetField,
        newStateArgSpec: ParameterSpec,
        prevStateArgSpec: ParameterSpec,
        generatedCachedStatements: MutableSet<TargetField>
    ): CodeBlock? {
        val statement: CodeBlock?
        if (!generatedCachedStatements.contains(parameter)) {
            statement = CodeBlock.join(listOf(
                CodeBlock.of("boolean ${parameter.name}Changed"),
                generateComparison(parameter, newStateArgSpec, prevStateArgSpec)),
                " = "
            )
            generatedCachedStatements.add(parameter)
        } else {
            statement = null
        }
        return statement
    }

    private fun generateComparison(
        parameter: TargetField,
        newStateArgSpec: ParameterSpec,
        prevStateArgSpec: ParameterSpec
        ): CodeBlock {

        val kind = parameter.type.kind
        val needsCustomEquals = kind == TypeKind.FLOAT || kind == TypeKind.DOUBLE || kind == TypeKind.ARRAY
        return if (kind.isPrimitive && !needsCustomEquals) {
            CodeBlock.of(
                "\$N.${parameter.name.toGetter()} != \$N.${parameter.name.toGetter()}",
                newStateArgSpec,
                prevStateArgSpec
            )
        } else {
            if (parameter.isNullable || needsCustomEquals) {
                CodeBlock.of(
                    "!areEqual(\$N.${parameter.name.toGetter()}, \$N.${parameter.name.toGetter()})",
                    newStateArgSpec,
                    prevStateArgSpec
                )
            } else {
                CodeBlock.of(
                    "!\$N.${parameter.name.toGetter()}.equals(\$N.${parameter.name.toGetter()})",
                    newStateArgSpec,
                    prevStateArgSpec
                )
            }
        }
    }

    private fun generateDispatchCallStatement(stateArgSpec: ParameterSpec, element: ExecutableElement): CodeBlock {
        return CodeBlock.of(
            "receiver.\$N(\$N)",
            element.simpleName,
            element.parameters.joinToString(", ", transform = {
                "${stateArgSpec.name}.${it.simpleName.toGetter()}"
            })
        )
    }

    /**
     * Generates a set of helper-methods to check for nullable objects, arrays and floating point types equality
     */
    private fun generateEqualsHelpers(receiverParameters: Collection<TargetField>): List<MethodSpec> {
        // there can be several types of arrays (int[], float[], Object[], ...) for each of them
        // need to add an utility function
        val arraysTypes = receiverParameters.filter { it.type.kind == TypeKind.ARRAY }
            .mapTo(mutableSetOf(), { TypeName.get(it.type) })
        val haveFloats = receiverParameters.any { it.type.kind == TypeKind.FLOAT }
        val haveDoubles = receiverParameters.any { it.type.kind == TypeKind.DOUBLE }
        val haveObjects = receiverParameters.any { it.type.kind == TypeKind.DECLARED }

        val methods = arrayListOf<MethodSpec>()

        arraysTypes.mapTo(methods) {
            MethodSpec.methodBuilder("areEqual")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .addParameter(it, "first")
                .addParameter(it, "second")
                .addStatement("return \$T.equals(\$N, \$N)", Arrays::class.java, "first", "second")
                .returns(TypeName.BOOLEAN)
                .build()
        }

        if (haveFloats) {
            val m = MethodSpec.methodBuilder("areEqual")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .addParameter(TypeName.FLOAT, "first")
                .addParameter(TypeName.FLOAT, "second")
                .addStatement("return Float.compare(\$N, \$N) == 0", "first", "second")
                .returns(TypeName.BOOLEAN)
                .build()
            methods.add(m)
        }

        if (haveDoubles) {
            val m = MethodSpec.methodBuilder("areEqual")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .addParameter(TypeName.DOUBLE, "first")
                .addParameter(TypeName.DOUBLE, "second")
                .addStatement("return Double.compare(\$N, \$N) == 0", "first", "second")
                .returns(TypeName.BOOLEAN)
                .build()
            methods.add(m)
        }

        if (haveObjects) {
            val m = MethodSpec.methodBuilder("areEqual")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .addParameter(TypeName.OBJECT, "first")
                .addParameter(TypeName.OBJECT, "second")
                .addStatement("return \$1N == null ? \$2N == null : $1N.equals($2N)", "first", "second")
                .returns(TypeName.BOOLEAN)
                .build()
            methods.add(m)
        }
        return methods
    }

    /**
     * Returns a set of fields used in diff-receiver interface mapped to the function elements of interface
     * in which they are used.
     * This maybe a subset of diff target data class fields or all of them.
     * ```
     * interface MyReceiver {
     *   fun function1(field1: Data, field2: String)
     *   fun function2(field2: String)
     * }
     * ```
     * will return a map like
     *
     * ```
     * field1 -> [function1]
     * field2 -> [function1, function2]
     * ```
     */
    private fun getReceiverFields(
        receiverElement: TypeElement
    ): MutableMap<TargetField, MutableList<ExecutableElement>> {
        // TODO describe why need custom fold/grouping (because need to use isSameType)
        // TODO UPD now TargetField has proper equals() implementation, this should be simplified to use stdlib group*?
        return receiverElement.enclosedMethods
            .flatMap { method -> method.parameters.map { param -> param to method } }
            .fold(mutableMapOf(), { acc, (param, method) ->
                val key = acc.keys
                    .find { it.name == param.simpleName.toString() && typeUtils.isSameType(it.type, param.asType()) }
                    ?: TargetField(param)
                val methods = acc[key] ?: mutableListOf()
                val add = methods.isEmpty()
                methods.add(method)
                if (add) {
                    acc[key] = methods
                }
                acc
            })
    }

    private fun checkTargetHasFieldsRequestedByReceiver(
        targetElement: TypeElement,
        receiverElement: TypeElement,
        receiverParameters: Map<TargetField, List<ExecutableElement>>
    ) : Boolean {

        val missing = receiverParameters
            .filterKeys { receiverField ->
                // NOTE searching for getters, because this is what ends up in generated code!
                targetElement.enclosedMethods.none {
                    it.isPublic && it.simpleName.toString() == receiverField.name.toGetterName()
                        && typeUtils.isSameType(it.returnType, receiverField.type)
                }
            }

        for ((argName, methods) in missing) {
            // Catch when field is private in kotlin data class
            // (if check only getters, it will be reported as completely missing, be a bit smarter)
            val isNotPublicField = targetElement.enclosedFields.any {
                !it.isPublic && it.simpleName.toString() == argName.name
            }
            // Catch a private getter (most probably will happen not in a data class)
            val isNotPublicGetter = targetElement.enclosedMethods.any {
                !it.isPublic && it.simpleName.toString() == argName.name
            }
            for (method in methods) {
                if (isNotPublicField || isNotPublicGetter) {
                    logger.error( "\"${receiverElement.simpleName}\" method uses " +
                        "a property which does not have a public getter in \"${targetElement.simpleName}\" class")
                } else {
                    logger.error( "\"${receiverElement.simpleName}\" method uses " +
                        "a property missing from \"${targetElement.simpleName}\" class")
                }
                logger.error("  property: ${argName.name}")
                logger.error("  type:     ${argName.type}")
                logger.error("  method:   $method")
                logger.error("Solutions:")
                logger.error("  * Add a public property \"${argName.name}\" to \"${targetElement.simpleName}\"")
                logger.error("  * Add a public getter \"${argName.name.toGetter()}\" to \"${targetElement.simpleName}\"")
                logger.error("  * Remove \"${argName.name}\" from argument list of \"$method\"")
            }
        }
        return missing.isEmpty()
    }

    /**
     * Returns true if all [targetElement] fields have stronger or the same nullability as ones in [receiverParameters].
     *
     * For example if receiverElement has some method accepting a `String`, but the diff target class has the
     * corresponding property listed as `String?` that means null can potentially be passed to the render
     * function when it doesn't expect it. So an error must be raised in this case
     */
    private fun checkNullabilityMatches(
        targetElement: TypeElement,
        receiverParameters: Map<TargetField, List<ExecutableElement>>
    ): Boolean {
        val weakerParams = receiverParameters
            .keys
            .filter { !it.isNullable }
            .filter {
                targetElement.findGetter(it)?.isNullable == true
            }
        for (p in weakerParams) {
            logger.error("Parameter \"${p.name}\" has a weaker nullability " +
                "than a \"${targetElement.simpleName}\" class property.")
            logger.error("This can lead to a runtime error and must be fixed.")
            logger.error("  method(s): ${receiverParameters[p]!!.joinToString { it.simpleName }}")
            logger.error("  parameter type: ${p.type.toString().prettifiedQualified(targetElement)}")
            logger.error("  expected  type: ${p.type.toString().prettifiedQualified(targetElement)}?")
        }
        return weakerParams.isEmpty()
    }

    /**
     * Checks that whenever parameter is used in multiple methods of the receiver class, it has the same nullability.
     * Otherwise [checkNullabilityMatches] won't catch "tricky" cases, for example:
     * ```
     * data class Target(val field: String?)
     * interface Renderer {
     *   fun render1(field: String?)
     *   fun render2(field: String)
     * }
     * ```
     * In the above case `field`'s nullability will be recorded into `receiverParameters` as nullable, same as
     * Target's nullability and [checkNullabilityMatches] will pass although it shouldn't.
     *
     * An alternative solution would be to have `receiverParameters` to be multi-map and have
     * two keys for `field`,  but that's too much of a refactor for now...
     */
    private fun checkReceiverArgumentNullabilityConsistent(
        receiverElement: TypeElement
    ): Boolean {
        val params = receiverElement.enclosedMethods
            .flatMap { it.parameters }
            .groupBy({ it.simpleName.toString() }, { it.isNullable })

        val varyingNullabilityParams = params.filter { it.value.distinct().size > 1 }

        for ((p, _) in varyingNullabilityParams) {
            logger.error("Parameter \"$p\" has varying nullability in different methods " +
                "of \"${receiverElement.simpleName}\"")
            logger.error("Nullability has to be the same to prevent runtime errors.")
        }

        return varyingNullabilityParams.isEmpty()
    }

    private fun getReceiverElement(targetElement: TypeElement): TypeElement? {
        val annotation = targetElement.annotationMirrors
            .find {
                (it.annotationType.asElement() as TypeElement).qualifiedName.toString() == DiffElement::class.java.name
            }
        if (annotation == null) {
            // must be enforced by compiler, how come?
            logger.error("internal error, no target annotation")
            return null
        }
        val receiverValue = annotation.elementValues.entries
            .firstOrNull { (element, _) ->
                element.simpleName.toString() == RECEIVER_PARAMETER_NAME
            }
            ?.value
        if (receiverValue == null) {
            // must be enforced by compiler, how come?
            logger.error("internal error, annotation misses $RECEIVER_PARAMETER_NAME property")
            return null
        }
        val receiverTypeMirror = receiverValue.value as TypeMirror
        return typeUtils.asElement(receiverTypeMirror) as TypeElement
    }

    private fun warnIfMissingHashCodeEquals(
        targetElement: TypeElement,
        receiverParameters: Set<TargetField>
    ) {
        val elements = mutableListOf(targetElement) +
            receiverParameters.mapNotNull {
                typeUtils.asElement(it.type) as? TypeElement
            }.toSet()

        elements
            .filterNot { hasHashCodeEquals(it) }
            .forEach {
                logger.warning("class \"${it.qualifiedName}\" does not override equals/hashCode, " +
                    "this will restrict diffing to reference only comparisons")
            }
    }

    // can't use a data class here, TypeMirror equals() isn't good enough,
    // need to compare using Types.isSameType :-/
    // For example for TypeMirrors of two parameters of same type in different functions equals() will return false
    private inner class TargetField(
        val name: String,
        val type: TypeMirror,
        val isNullable: Boolean
    ) {
        constructor(element: VariableElement)
            : this(element.simpleName.toString(), element.asType(), element.isNullable)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TargetField

            if (name != other.name) return false
            if (!typeUtils.isSameType(type, other.type)) return false
            if (isNullable != other.isNullable) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + type.toString().hashCode()
            result = 31 * result + isNullable.hashCode()
            return result
        }

    }

    private fun Element.findGetter(param: Processor.TargetField): ExecutableElement? {
        return this.enclosedMethods .find {
            it.simpleName.toString() == param.name.toGetterName() && typeUtils.isSameType(it.returnType, param.type)
        }
    }

    private fun CharSequence.prettifiedQualified(targetElement: TypeElement): String {
        return this.toString()
            .replace("java.lang.", "")
            .replace("java.util.", "")
            .replace("${targetElement.enclosingPackageName}.", "")
    }
}

private fun hasHashCodeEquals(typeElement: TypeElement): Boolean {
    val enclosedElements = typeElement.enclosedElements
    return enclosedElements.any { it.kind == ElementKind.METHOD && it.simpleName.toString() == "equals" }
        && enclosedElements.any { it.kind == ElementKind.METHOD && it.simpleName.toString() == "hashCode" }
}

/**
 * Returns a method spec builder which overrides an interface method
 */
private fun MethodSpec.override(): MethodSpec.Builder {
    return MethodSpec.methodBuilder(this.name)
        .addJavadoc(this.javadoc)
        .addAnnotations(this.annotations)
        .addAnnotation(Override::class.java)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(this.returnType)
        .addTypeVariables(this.typeVariables)
        .addParameters(this.parameters)
        .addExceptions(this.exceptions)
}

private fun CharSequence.toGetter(): String {
    return "${this.toGetterName()}()"
}

private fun CharSequence.toGetterName(): String {
    return "get${this.toString().capitalize()}"
}
