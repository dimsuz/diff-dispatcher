package com.github.dimsuz.diffdispatcher.sample

import com.github.dimsuz.diffdispatcher.DiffedState
import com.github.dimsuz.diffdispatcher.ReceivesDiff

@DiffedState
data class ViewState(
    val intField: Int
)

@ReceivesDiff(state = ViewState::class)
interface Renderer

fun main() {

}
