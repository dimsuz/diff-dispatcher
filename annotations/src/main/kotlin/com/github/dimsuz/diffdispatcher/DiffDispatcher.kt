package com.github.dimsuz.diffdispatcher

interface DiffDispatcher<in ViewState> {
    fun dispatch(newState: ViewState, previousState: ViewState?)
}
