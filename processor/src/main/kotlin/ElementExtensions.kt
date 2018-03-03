package com.github.dimsuz.diffdispatcher.processor

import javax.lang.model.element.*

internal val Element.enclosedMethods: List<ExecutableElement>
    get() {
        return enclosedElements.filter({ it.kind == ElementKind.METHOD }).map { it as ExecutableElement }
    }

internal val Element.enclosedFields: List<VariableElement>
    get() {
        return enclosedElements.filter({ it.kind == ElementKind.FIELD }).map { it as VariableElement }
    }

internal val Element.isNullable: Boolean
    get() {
        return annotationMirrors.any { it.annotationType.asElement().simpleName.endsWith("Nullable") }
    }

internal val Element.enclosingPackage: PackageElement
    get() {
        var enclosing: Element? = this
        while (enclosing != null && enclosing.kind != ElementKind.PACKAGE) {
            enclosing = enclosing.enclosingElement
        }
        return (enclosing as? PackageElement) ?: throw IllegalStateException("no package element found")
    }

internal val Element.enclosingPackageName get() = enclosingPackage.qualifiedName.toString()
