package com.github.dimsuz.diffdispatcher.sample

import com.github.dimsuz.diffdispatcher.DiffDispatcher
import com.github.dimsuz.diffdispatcher.DiffedState
import com.github.dimsuz.diffdispatcher.sample.CompoundViewState.*
import kotlin.reflect.KClass

@DiffedState
sealed class CompoundViewState {
    @DiffedState
    data class State1(
        val price: Int,
        val inputField: String
    ) : CompoundViewState()

    @DiffedState
    data class State2(
        val title: String,
        val subtitle: String
    ) : CompoundViewState()

    @DiffedState
    data class State3(
        val title: String,
        val subtitle: String,
        val error: String
    ) : CompoundViewState()

    object Loading : CompoundViewState()
}

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ReceivesDiffGeneric(val state: KClass<*>)

// Keep plain-old state in the picture too
@DiffedState
data class ViewStatePlain(
    val intField: Int
)

@ReceivesDiffGeneric(state = ViewStatePlain::class)
interface RendererPlain

interface CompoundRenderer {
    @ReceivesDiffGeneric(state = CompoundViewState::class)
    fun renderSwitch(stateFrom: CompoundViewState, stateTo: CompoundViewState)

    @ReceivesDiffGeneric(state = State1::class)
    fun renderFirst(price: Int)
    @ReceivesDiffGeneric(state = State1::class)
    fun renderFirst(inputField: String)

    @ReceivesDiffGeneric(state = State2::class)
    fun renderSecond(title: String, subtitle: String)

    @ReceivesDiffGeneric(state = State3::class)
    fun renderThird(title: String, subtitle: String)
}

class Dispatcher : DiffDispatcher<CompoundViewState> {
    override fun dispatch(newState: CompoundViewState, previousState: CompoundViewState?) {
        if (newState !== previousState) {
            /// ??? how better to dispatch child states
        }
    }
}

