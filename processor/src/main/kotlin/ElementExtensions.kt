package com.dimsuz.diffdispatcher.processor

import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement

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
