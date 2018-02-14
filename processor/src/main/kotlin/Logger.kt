package com.dimsuz.diffdispatcher.processor

import javax.annotation.processing.Messager
import javax.tools.Diagnostic

internal class Logger(private val messager: Messager) {
    fun note(message: String) {
        messager.printMessage(Diagnostic.Kind.NOTE, message)
    }

    fun warning(message: String) {
        messager.printMessage(Diagnostic.Kind.WARNING, message)
    }

    fun error(message: String) {
        messager.printMessage(Diagnostic.Kind.ERROR, message)
    }

}
