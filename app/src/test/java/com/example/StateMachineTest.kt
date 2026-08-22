package com.example

import com.example.trading.StateMachine
import com.example.domain.model.StateMachineState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StateMachineTest {

    private lateinit var sm: StateMachine

    @Before
    fun setup() {
        sm = StateMachine(StateMachineState.STOPPED)
    }

    @Test
    fun `initial state is STOPPED`() {
        assertEquals(StateMachineState.STOPPED, sm.currentState.value)
    }

    @Test
    fun `STOPPED can transition to STARTING`() {
        assertTrue(sm.transitionTo(StateMachineState.STARTING, "test"))
        assertEquals(StateMachineState.STARTING, sm.currentState.value)
    }

    @Test
    fun `STOPPED cannot transition to READY`() {
        assertFalse(sm.transitionTo(StateMachineState.READY, "invalid"))
        assertEquals(StateMachineState.STOPPED, sm.currentState.value)
    }

    @Test
    fun `STARTING can transition to CONNECTING`() {
        sm.transitionTo(StateMachineState.STARTING, "start")
        assertTrue(sm.transitionTo(StateMachineState.CONNECTING, "connect"))
        assertEquals(StateMachineState.CONNECTING, sm.currentState.value)
    }

    @Test
    fun `full happy path - STOPPED to POSITION_OPEN`() {
        assertTrue(sm.transitionTo(StateMachineState.STARTING, ""))
        assertTrue(sm.transitionTo(StateMachineState.CONNECTING, ""))
        assertTrue(sm.transitionTo(StateMachineState.SYNCING, ""))
        assertTrue(sm.transitionTo(StateMachineState.READY, ""))
        assertTrue(sm.transitionTo(StateMachineState.ANALYZING, ""))
        assertTrue(sm.transitionTo(StateMachineState.SIGNAL_FOUND, ""))
        assertTrue(sm.transitionTo(StateMachineState.VALIDATING, ""))
        assertTrue(sm.transitionTo(StateMachineState.EXECUTING, ""))
        assertTrue(sm.transitionTo(StateMachineState.POSITION_OPEN, ""))
        assertEquals(StateMachineState.POSITION_OPEN, sm.currentState.value)
    }

    @Test
    fun `READY to ANALYZING to READY (no signal)`() {
        sm.forceState(StateMachineState.READY, "")
        assertTrue(sm.transitionTo(StateMachineState.ANALYZING, ""))
        assertTrue(sm.transitionTo(StateMachineState.READY, "no signal"))
        assertEquals(StateMachineState.READY, sm.currentState.value)
    }

    @Test
    fun `SAFE_MODE can be forced from any state`() {
        sm.forceState(StateMachineState.SAFE_MODE, "emergency")
        assertEquals(StateMachineState.SAFE_MODE, sm.currentState.value)
    }

    @Test
    fun `SAFE_MODE can transition to READY`() {
        sm.forceState(StateMachineState.SAFE_MODE, "")
        assertTrue(sm.transitionTo(StateMachineState.READY, "cleared"))
        assertEquals(StateMachineState.READY, sm.currentState.value)
    }

    @Test
    fun `ERROR can transition to STARTING`() {
        sm.forceState(StateMachineState.ERROR, "crash")
        assertTrue(sm.transitionTo(StateMachineState.STARTING, "retry"))
        assertEquals(StateMachineState.STARTING, sm.currentState.value)
    }

    @Test
    fun `state reason is updated on transition`() {
        sm.transitionTo(StateMachineState.STARTING, "because test")
        assertEquals("because test", sm.stateReason.value)
    }

    @Test
    fun `forceState bypasses validation`() {
        sm.forceState(StateMachineState.POSITION_OPEN, "force")
        assertEquals(StateMachineState.POSITION_OPEN, sm.currentState.value)
    }

    @Test
    fun `STOPPING can transition to STOPPED`() {
        sm.forceState(StateMachineState.STOPPING, "")
        assertTrue(sm.transitionTo(StateMachineState.STOPPED, "done"))
        assertEquals(StateMachineState.STOPPED, sm.currentState.value)
    }

    @Test
    fun `PAUSED can transition to CONNECTING`() {
        sm.forceState(StateMachineState.PAUSED, "")
        assertTrue(sm.transitionTo(StateMachineState.CONNECTING, "reconnect"))
        assertEquals(StateMachineState.CONNECTING, sm.currentState.value)
    }

    @Test
    fun `EXECUTING to READY (order rejected)`() {
        sm.forceState(StateMachineState.EXECUTING, "")
        assertTrue(sm.transitionTo(StateMachineState.READY, "rejected"))
        assertEquals(StateMachineState.READY, sm.currentState.value)
    }

    @Test
    fun `POSITION_OPEN to READY (position closed)`() {
        sm.forceState(StateMachineState.POSITION_OPEN, "")
        assertTrue(sm.transitionTo(StateMachineState.READY, "closed"))
        assertEquals(StateMachineState.READY, sm.currentState.value)
    }
}
