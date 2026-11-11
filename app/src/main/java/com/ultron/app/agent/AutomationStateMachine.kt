package com.ultron.app.agent

import java.util.UUID

/** Central execution state. A task can only move forward once and every task has a unique ID. */
class AutomationStateMachine {
    enum class State { IDLE, PLANNING, EXECUTING, VERIFYING, COMPLETED, FAILED, CANCELLED }
    data class Execution(val id: String, val state: State, val commandKey: String, val attempt: Int)

    private var current: Execution? = null

    @Synchronized fun start(commandKey: String): Execution? {
        if (current?.state in setOf(State.PLANNING, State.EXECUTING, State.VERIFYING)) return null
        return Execution(UUID.randomUUID().toString(), State.PLANNING, commandKey, 0).also { current = it }
    }
    @Synchronized fun transition(id: String, next: State): Boolean {
        val c = current ?: return false
        if (c.id != id) return false
        val valid = when (c.state) {
            State.PLANNING -> next == State.EXECUTING || next == State.FAILED || next == State.CANCELLED
            State.EXECUTING -> next == State.VERIFYING || next == State.FAILED || next == State.CANCELLED
            State.VERIFYING -> next == State.COMPLETED || next == State.EXECUTING || next == State.FAILED || next == State.CANCELLED
            else -> false
        }
        if (!valid) return false
        current = c.copy(state = next, attempt = if (next == State.EXECUTING) c.attempt + 1 else c.attempt)
        return true
    }
    @Synchronized fun cancel(id: String): Boolean = transition(id, State.CANCELLED)
    @Synchronized fun snapshot(): Execution? = current
    @Synchronized fun clearIfTerminal(id: String) { if (current?.id == id && current?.state in setOf(State.COMPLETED, State.FAILED, State.CANCELLED)) current = null }
}
