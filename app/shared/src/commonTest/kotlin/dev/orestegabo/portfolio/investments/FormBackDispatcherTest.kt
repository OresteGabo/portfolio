package dev.orestegabo.portfolio.investments

import kotlin.test.*

class FormBackDispatcherTest {
    @Test fun appBarBackUsesTheVisibleFormPolicyWithoutAlsoLeavingTheRoute() {
        val dispatcher = FormBackDispatcher()
        val owner = Any()
        var prompts = 0; var exits = 0
        dispatcher.install(owner) { prompts++ }
        dispatcher.dispatch { exits++ }
        assertEquals(1, prompts); assertEquals(0, exits)
        dispatcher.remove(owner)
        dispatcher.dispatch { exits++ }
        assertEquals(1, exits)
    }

    @Test fun oldFormDisposalDoesNotRemoveTheReplacementFormsHandler() {
        val dispatcher = FormBackDispatcher()
        val old = Any(); val next = Any()
        var oldCalls = 0; var nextCalls = 0; var fallbackCalls = 0
        dispatcher.install(old) { oldCalls++ }
        dispatcher.install(next) { nextCalls++ }
        dispatcher.remove(old)
        dispatcher.dispatch { fallbackCalls++ }
        assertEquals(0, oldCalls); assertEquals(1, nextCalls); assertEquals(0, fallbackCalls)
        dispatcher.remove(next)
        dispatcher.dispatch { fallbackCalls++ }
        assertEquals(1, fallbackCalls)
    }
}
