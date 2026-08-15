package com.orion.app.face.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

class ImagesVectorDB {
    private val imagesBox get() = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)

    fun addFaceImageRecord(record: FaceImageRecord) {
        imagesBox.put(record)
    }

    fun getNearestEmbeddingPersonName(
        embedding: FloatArray,
        flatSearch: Boolean = false,
    ): FaceImageRecord? {
        if (flatSearch) {
            val allRecords = imagesBox.all
            if (allRecords.isEmpty()) return null
            val numThreads = 4
            val batchSize = (allRecords.size / numThreads).coerceAtLeast(1)
            val batches = allRecords.chunked(batchSize)
            val results =
                runBlocking {
                    batches
                        .map { batch ->
                            async(Dispatchers.Default) {
                                var bestMatch: FaceImageRecord? = null
                                var bestDistance = Float.NEGATIVE_INFINITY
                                for (record in batch) {
                                    val distance = cosineDistance(embedding, record.faceEmbedding)
                                    if (distance > bestDistance) {
                                        bestDistance = distance
                                        bestMatch = record
                                    }
                                }
                                Pair(bestMatch, bestDistance)
                            }
                        }.awaitAll()
                }
            return results.maxByOrNull { it.second }?.first
        }

        val query = imagesBox
            .query(FaceImageRecord_.faceEmbedding.nearestNeighbors(embedding, 10))
            .build()
        val resultsWithScores = query.findWithScores()
        query.close()
        return resultsWithScores.firstOrNull()?.get()
    }

    private fun cosineDistance(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        for (i in x1.indices) {
            mag1 += x1[i] * x1[i]
            mag2 += x2[i] * x2[i]
            product += x1[i] * x2[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        if (mag1 == 0f || mag2 == 0f) return 0f
        return product / (mag1 * mag2)
    }

    fun removeFaceRecordsWithPersonID(personID: Long) {
        val query = imagesBox
            .query(FaceImageRecord_.personID.equal(personID))
            .build()
        val ids = query.findIds().toList()
        query.close()
        imagesBox.removeByIds(ids)
    }

    fun getRecordsCount(): Long = imagesBox.count()
}
