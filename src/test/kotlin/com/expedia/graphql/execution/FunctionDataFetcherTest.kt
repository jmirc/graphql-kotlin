package com.expedia.graphql.execution

import com.expedia.graphql.annotations.GraphQLContext
import com.expedia.graphql.exceptions.CouldNotCastArgumentException
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FunctionDataFetcherTest {

    internal class MyClass {
        fun print(string: String) = string

        fun printArray(items: Array<String>) = items.joinToString(separator = ":")

        fun printList(items: List<String>) = items.joinToString(separator = ":")

        fun context(@GraphQLContext string: String) = string

        fun dataFetchingEnvironment(environment: DataFetchingEnvironment) = environment.field.name

        suspend fun suspendPrint(string: String): String = coroutineScope {
            delay(10)
            string
        }
    }

    @Test
    fun `null target and null source returns null`() {
        val dataFetcher = FunctionDataFetcher(target = null, fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getSource<Any>() } returns null
        assertNull(dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `null target and valid source returns the value`() {
        val dataFetcher = FunctionDataFetcher(target = null, fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getSource<Any>() } returns MyClass()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "hello", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target and null source returns the value`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::print)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "hello", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target with context`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::context)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.getContext<String>() } returns "foo"
        assertEquals(expected = "foo", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target and value from predicate`() {
        val mockPredicate: DataFetcherExecutionPredicate = mockk()
        every { mockPredicate.evaluate<String>(any(), any(), any()) } returns "baz"
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::print, executionPredicate = mockPredicate)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "baz", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `valid target and null from predicate`() {
        val mockPredicate: DataFetcherExecutionPredicate = mockk()
        every { mockPredicate.evaluate<String?>(any(), any(), any()) } returns null
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::print, executionPredicate = mockPredicate)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")
        assertEquals(expected = "hello", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `array inputs can be converted by the object mapper`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::printArray, executionPredicate = null)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to arrayOf("foo", "bar"))
        assertEquals(expected = "foo:bar", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `list inputs throws exception`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::printList, executionPredicate = null)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("items" to listOf("foo", "bar"))

        assertFailsWith(CouldNotCastArgumentException::class) {
            dataFetcher.get(mockEnvironmet)
        }
    }

    @Test
    fun `dataFetchingEnvironement is passed as an argument`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::dataFetchingEnvironment)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.field } returns mockk {
            every { name } returns "fooBarBaz"
        }
        assertEquals(expected = "fooBarBaz", actual = dataFetcher.get(mockEnvironmet))
    }

    @Test
    fun `suspend functions return value wrapped in CompletableFuture`() {
        val dataFetcher = FunctionDataFetcher(target = MyClass(), fn = MyClass::suspendPrint)
        val mockEnvironmet: DataFetchingEnvironment = mockk()
        every { mockEnvironmet.arguments } returns mapOf("string" to "hello")

        val result = dataFetcher.get(mockEnvironmet)

        assertTrue(result is CompletableFuture<*>)
        assertEquals(expected = "hello", actual = result.get())
    }
}
