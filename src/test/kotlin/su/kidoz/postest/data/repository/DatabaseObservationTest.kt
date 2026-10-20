package su.kidoz.postest.data.repository

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import su.kidoz.postest.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseObservationTest {
    @Test fun `collection observers see writes from other instances and child only SQL changes`() =
        runBlocking {
            withTimeout(5000) {
                val database = TestDatabaseFactory.createInMemoryDatabase()
                val reader = CollectionRepository(database, TestDatabaseFactory.cipher())
                val writer = CollectionRepository(database, TestDatabaseFactory.cipher())
                val changes = Channel<List<RequestCollection>>(Channel.UNLIMITED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { reader.collections.collect { changes.send(it) } }
                try {
                    assertTrue(changes.receive().isEmpty())
                    val collection = writer.createCollection("API")
                    assertEquals(collection.id, changes.receive().single().id)
                    val request = writer.addRequestToCollection(collection.id, HttpRequest(), "Original")
                    assertEquals(
                        "Original",
                        changes
                            .receive()
                            .single()
                            .items
                            .single()
                            .name,
                    )
                    // Deliberately do not touch Collection: observation must also depend on CollectionItem.
                    database.postestQueries.renameCollectionItem(
                        TestDatabaseFactory.cipher().encode("CollectionItem", request.id, "name", "Renamed"),
                        request.id,
                    )
                    assertEquals(
                        "Renamed",
                        changes
                            .receive()
                            .single()
                            .items
                            .single()
                            .name,
                    )
                    writer.deleteCollection(collection.id)
                    assertTrue(changes.receive().isEmpty())
                } finally {
                    collector.cancelAndJoin()
                }
            }
        }

    @Test fun `environment reads work before initialization and observe variable only changes`() =
        runBlocking {
            withTimeout(5000) {
                val database = TestDatabaseFactory.createInMemoryDatabase()
                val reader = EnvironmentRepository(database, TestDatabaseFactory.cipher())
                val writer = EnvironmentRepository(database, TestDatabaseFactory.cipher())
                val variable = Variable(key = "host", value = "one")
                val first = Environment(name = "First", isActive = true, variables = listOf(variable))
                writer.saveEnvironment(first)
                assertEquals(first, reader.getActiveEnvironment())
                val changes = Channel<EnvironmentSnapshot>(Channel.UNLIMITED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { reader.snapshot.collect { changes.send(it) } }
                try {
                    assertEquals(first.id, changes.receive().activeEnvironmentId)
                    database.postestQueries.updateEnvironmentVariable(
                        TestDatabaseFactory.cipher().encode("EnvironmentVariable", variable.id, "key", variable.key),
                        TestDatabaseFactory.cipher().encode("EnvironmentVariable", variable.id, "value", "two"),
                        0,
                        1,
                        variable.id,
                    )
                    assertEquals(
                        "two",
                        changes
                            .receive()
                            .activeEnvironment
                            ?.variables
                            ?.single()
                            ?.value,
                    )
                    val second = writer.createEnvironment("Second")
                    changes.receive()
                    writer.setActiveEnvironment(second.id)
                    val selected = changes.receive()
                    assertEquals(second.id, selected.activeEnvironmentId)
                    assertEquals(1, selected.environments.count { it.isActive })
                    writer.deleteEnvironment(second.id)
                    assertNull(changes.receive().activeEnvironmentId)
                } finally {
                    collector.cancelAndJoin()
                }
            }
        }

    @Test fun `history observations agree with fresh reads across repository instances`() =
        runBlocking {
            withTimeout(5000) {
                val database = TestDatabaseFactory.createInMemoryDatabase()
                val reader = HistoryRepository(database, TestDatabaseFactory.cipher())
                val writer = HistoryRepository(database, TestDatabaseFactory.cipher())
                val changes = Channel<List<HistoryEntry>>(Channel.UNLIMITED)
                val collector = launch(start = CoroutineStart.UNDISPATCHED) { reader.history.collect { changes.send(it) } }
                try {
                    assertTrue(changes.receive().isEmpty())
                    val entry = HistoryEntry(request = HttpRequest(), response = null, duration = 1)
                    writer.addHistoryEntry(entry)
                    assertEquals(reader.loadHistory(), changes.receive())
                    writer.clearHistory()
                    assertTrue(changes.receive().isEmpty())
                    assertTrue(reader.history.first().isEmpty())
                } finally {
                    collector.cancelAndJoin()
                }
            }
        }
}
