package com.dimsuz.diffdispatcher.annotations

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class DiffElement(val diffReceiver: KClass<*>)
