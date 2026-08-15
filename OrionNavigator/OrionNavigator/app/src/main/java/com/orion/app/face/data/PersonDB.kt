package com.orion.app.face.data

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class PersonDB {
    private val personBox get() = ObjectBoxStore.store.boxFor(PersonRecord::class.java)

    fun addPerson(person: PersonRecord): Long = personBox.put(person)

    fun removePerson(personID: Long) {
        personBox.removeByIds(listOf(personID))
    }

    fun getCount(): Long = personBox.count()

    fun getAllList(): List<PersonRecord> = personBox.all

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAll(): Flow<MutableList<PersonRecord>> =
        personBox
            .query(PersonRecord_.personID.notNull())
            .build()
            .flow()
            .flowOn(Dispatchers.IO)
}
