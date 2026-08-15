package com.orion.app.face.data

import android.content.Context
import io.objectbox.BoxStore

object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (!::store.isInitialized) {
            store = MyObjectBox.builder().androidContext(context.applicationContext).build()
        }
    }
}
