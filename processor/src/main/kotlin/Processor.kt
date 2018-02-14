package com.dimsuz.diffdispatcher.processor

import com.dimsuz.diffdispatcher.annotations.DiffElement
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

class Processor : AbstractProcessor() {
    private lateinit var logger: Logger

    @Synchronized
    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        logger = Logger(processingEnv.messager)
        logger.note("starting annotation processing")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(DiffElement::class.java.canonicalName)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        logger.note("processing $annotations")
        return true
    }

}

