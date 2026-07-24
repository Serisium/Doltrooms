package dev.seri.doltrooms.dolt

import androidx.room3.Room
import androidx.sqlite.SQLiteException
import dev.seri.doltrooms.driver.DoltLiteDriver
import dev.seri.doltrooms.room.Person
import dev.seri.doltrooms.room.RoomConformanceDb
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

// Step 8: sync against a real doltlite-remotesrv over http — the network
// leg of the remote round-trip (the protocol-independent sync contract is
// pinned in AbstractDoltDatabaseTest over file:// remotes).
//
// jvm-only: the fixture spawns the doltlite-remotesrv binary from the
// pinned release's doltlite-tools zip (ProcessBuilder has no commonTest
// equivalent). Gradle downloads it on linux-x64 hosts and passes its
// path via the system property below; on other hosts the tests skip
// (recorded in docs/deferred-verification.md). On a linux-x64 host a
// MISSING property is a wiring failure, not a skip — these tests must
// not be able to rot away silently.
//
// There is deliberately no https leg: the DoltLite amalgamation excludes
// the TLS/credential stack ("compiled everywhere it is linked: it is
// excluded only from the single-file amalgamation, which links neither
// library" — doltlite.c, 0.11.33), and D9 builds every platform from the
// amalgamation. httpsRejected... pins the resulting contract. The
// release's PREBUILT binaries do speak TLS 1.3 + DOLTLITE_CA_FILE
// (verified via CLI against a --cert/--key remotesrv, 2026-07-18), so
// the gap is packaging, not protocol.
class RemoteServerSyncTest {

    private val remotesrvProperty = "dev.seri.doltrooms.remotesrv"

    private fun hostIsLinuxX64(): Boolean =
        System.getProperty("os.name").lowercase().contains("linux") &&
            System.getProperty("os.arch") in setOf("amd64", "x86_64")

    /** The fixture binary, or null when this host legitimately has none. */
    private fun remotesrvBinary(): File? {
        val path = System.getProperty(remotesrvProperty)
        if (path == null) {
            check(!hostIsLinuxX64()) {
                "$remotesrvProperty is not set on a linux-x64 host — the Gradle fixture wiring is broken"
            }
            println("SKIP: doltlite-remotesrv fixture is only wired on linux-x64 hosts")
            return null
        }
        return File(path).also {
            check(it.canExecute()) { "remotesrv fixture is not executable: $it" }
        }
    }

    private fun tempDir(): File = File.createTempFile("remotesrv", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private class Server(private val process: Process, val baseUrl: String) : AutoCloseable {
        override fun close() {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    /** Starts remotesrv on a free port (-p 0) and parses the URL it prints. */
    private fun startServer(binary: File, servedDir: File): Server {
        val process = ProcessBuilder(
            binary.absolutePath, "-p", "0", servedDir.absolutePath
        ).redirectErrorStream(true).start()
        val line = process.inputStream.bufferedReader().readLine()
            ?: error("remotesrv exited without output")
        val url = Regex("http://[0-9.]+:[0-9]+").find(line)?.value
            ?: error("could not parse server URL from: $line")
        return Server(process, url)
    }

    private fun fileDb(path: String): RoomConformanceDb =
        Room.databaseBuilder<RoomConformanceDb>(name = path)
            .setDriver(DoltLiteDriver())
            .build()

    private fun tempDbPath(): String = File(tempDir(), "db.db").absolutePath

    @Test
    fun httpRoundTripAgainstRemotesrv() = runTest {
        val binary = remotesrvBinary() ?: return@runTest
        startServer(binary, tempDir()).use { server ->
            val remoteUrl = "${server.baseUrl}/synced.db"
            val a = fileDb(tempDbPath())
            try {
                val doltA = DoltDatabase(a)
                a.personDao().insert(Person(name = "Ada", age = 36))
                doltA.commit("c1")
                doltA.addRemote("srv", remoteUrl)
                // The first push CREATES synced.db on the server side.
                doltA.push("srv", "main")

                val clonePath = tempDbPath()
                DoltDatabase.clone(DoltLiteDriver(), remoteUrl, clonePath)
                val b = fileDb(clonePath)
                try {
                    val doltB = DoltDatabase(b)
                    assertEquals(listOf("Ada"), b.personDao().olderThan(-1).map { it.name })
                    assertEquals(doltA.log(), doltB.log())

                    a.personDao().insert(Person(name = "Bob", age = 17))
                    doltA.commit("c2")
                    doltA.push("srv", "main")
                    // The clone's auto-configured remote is 'origin' -> the server URL.
                    doltB.pull("origin", "main")
                    assertEquals(
                        listOf("Ada", "Bob"),
                        b.personDao().olderThan(-1).map { it.name }.sorted(),
                    )
                    assertEquals(doltA.log(), doltB.log())
                } finally {
                    b.close()
                }
            } finally {
                a.close()
            }
        }
    }

    @Test
    fun httpsRejectedByAmalgamationBuiltEngine() = runTest {
        // No server needed: the engine rejects the URL scheme before any
        // connection is attempted. This is THIS library's sync security
        // contract (class comment): file:// and plain http:// only, so
        // network sync belongs on localhost/trusted networks (D3's
        // trusted-proxy rule; a client-side TLS proxy would need a plain
        // http hop anyway).
        val db = fileDb(tempDbPath())
        try {
            val dolt = DoltDatabase(db)
            db.personDao().insert(Person(name = "Ada", age = 36))
            dolt.commit("c1")
            // The remote CONFIG accepts any URL; the scheme check fires on use.
            dolt.addRemote("tls", "https://127.0.0.1:1/never.db")
            val e = assertFailsWith<SQLiteException> { dolt.push("tls", "main") }
            assertContains(e.message ?: "", "URL must start with file:// or http://")
        } finally {
            db.close()
        }
    }
}
