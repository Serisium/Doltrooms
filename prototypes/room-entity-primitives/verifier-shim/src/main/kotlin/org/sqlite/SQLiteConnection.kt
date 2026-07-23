package org.sqlite

import java.sql.Connection

/**
 * Shadow of xerial's `org.sqlite.SQLiteConnection`. Must exist as a real
 * class with this exact name: Room's compiled `DatabaseVerifier` calls
 * `JDBC.createConnection` through a method descriptor that RETURNS
 * `org/sqlite/SQLiteConnection`. All behavior delegates to the proxy
 * built by [DoltVerifierEngine].
 */
public class SQLiteConnection internal constructor(
    delegate: Connection,
) : Connection by delegate
