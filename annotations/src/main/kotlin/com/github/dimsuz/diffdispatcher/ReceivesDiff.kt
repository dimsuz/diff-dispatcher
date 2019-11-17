package com.github.dimsuz.diffdispatcher

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ReceivesDiff(val state: KClass<*>)
