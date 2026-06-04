package com.example.adfalls.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialQueryIsSubmittedOnlyOnce() = runTest(dispatcher) {
        val viewModel = AiChatViewModel()

        viewModel.submitInitialQueryOnce("学生数码推荐")
        viewModel.submitInitialQueryOnce("不应再次发送")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(state.messages.joinToString { "${it.role}:${it.text}" }, 4, state.messages.size)
        assertEquals("学生数码推荐", state.messages.first().text)
        assertFalse(state.sending)
    }

    @Test
    fun responseDoesNotClearDraftTypedWhileSending() = runTest(dispatcher) {
        val viewModel = AiChatViewModel()
        advanceUntilIdle()
        viewModel.updateInput("周末露营")
        viewModel.sendMessage()

        assertEquals("", viewModel.uiState.value.inputText)
        assertTrue(viewModel.uiState.value.sending)

        viewModel.updateInput("下一条草稿")
        advanceUntilIdle()

        assertEquals("下一条草稿", viewModel.uiState.value.inputText)
        assertFalse(viewModel.uiState.value.sending)
    }
}
