package com.ultron.app.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class MemoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "ultron_memory.db",
    null,
    2
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                summary TEXT NOT NULL DEFAULT '',
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                duration_ms INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_messages_session_time ON messages(session_id, timestamp)")
        db.execSQL("""
            CREATE TABLE instructions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                trigger_text TEXT NOT NULL,
                workflow_json TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 0,
                approved INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(started_at DESC)")
        db.execSQL("""
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                memory_key TEXT NOT NULL,
                memory_value TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT 'user',
                updated_at INTEGER NOT NULL,
                UNIQUE(category, memory_key)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_memories_updated ON memories(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                memory_key TEXT NOT NULL,
                memory_value TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT 'user',
                updated_at INTEGER NOT NULL,
                UNIQUE(category, memory_key)
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at DESC)")
        }
    }
}
