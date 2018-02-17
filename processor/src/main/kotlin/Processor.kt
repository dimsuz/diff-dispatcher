package com.dimsuz.diffdispatcher.processor

import com.dimsuz.diffdispatcher.annotations.DiffElement
import com.squareup.javapoet.*
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
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

            checkHasHashCodeEquals(targetElement)
            targetElement.enclosedElements.forEach {
                logger.note(it.simpleName.toString() + ", " + it.kind)
            }

            val targetFields = targetElement.enclosedFields.map { TargetField(it) }
            // receiver interface method parameters grouped by method they belong to
            val receiverFields = getReceiverFields(receiverElement)

            if (!checkTargetHasFieldsRequestedByReceiver(targetFields, receiverFields)) {
                return true
            }

            generateDispatcherInterface(targetElement)
        }

        return true
    }

    private fun generateDispatcherInterface(targetElement: TypeElement): TypeSpec {
        val targetTypeName = TypeName.get(targetElement.asType())
        val typeSpec = TypeSpec.interfaceBuilder("${targetElement.simpleName}DiffDispatcher")
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
            .build()

        JavaFile.builder(targetElement.enclosingPackageName, typeSpec)
            .build()
            .writeTo(filer)
    }

    private fun getReceiverFields(
        receiverElement: TypeElement
    ): MutableMap<TargetField, MutableList<ExecutableElement>> {
        // TODO describe why need custom fold/grouping (because need to use isSameType)
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
        targetFields: List<TargetField>,
        receiverFields: Map<TargetField, List<ExecutableElement>>
    ) : Boolean {
        // TODO also do strict checking for nullability? both target and receiver nullability must match?
        val missing = receiverFields
            .filterKeys { receiverField ->
                targetFields.none { it.name == receiverField.name && typeUtils.isSameType(it.type, receiverField.type) }
            }
        for ((argName, methods) in missing) {
            // TODO log it like "ReceiverClassName contains a field name missing from TargetClassName:...."
            for (method in methods) {
                logger.error("diffReceiver method contains field missing from diff element class")
                logger.error("  method: $method")
                logger.error("  field: ${argName.name}")
                logger.error("  fieldType: ${argName.type}")
            }
        }
        return missing.isEmpty()
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

    private fun checkHasHashCodeEquals(typeElement: TypeElement) {
        if (!hasHashCodeEquals(typeElement)) {
            logger.warning("class \"${typeElement.simpleName}\" does not override equals/hashCode, " +
                "this will restrict diffing to reference only comparisons")
        }
    }

}

private data class TargetField(
    val name: String,
    val type: TypeMirror
) {
    constructor(element: VariableElement) : this(element.simpleName.toString(), element.asType())
    constructor(element: ExecutableElement) : this(element.simpleName.toString(), element.asType())
}

private fun hasHashCodeEquals(typeElement: TypeElement): Boolean {
    val enclosedElements = typeElement.enclosedElements
    return enclosedElements.any { it.kind == ElementKind.METHOD && it.simpleName.toString() == "equals" }
        && enclosedElements.any { it.kind == ElementKind.METHOD && it.simpleName.toString() == "hashCode" }
}

