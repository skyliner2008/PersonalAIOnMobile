package com.example.personalaibot.db

object JarvisDatabaseHolder {
    @Volatile
    private var _database: JarvisDatabase? = null

    val database: JarvisDatabase?
        get() = _database

    fun install(db: JarvisDatabase) {
        _database = db
    }
}
