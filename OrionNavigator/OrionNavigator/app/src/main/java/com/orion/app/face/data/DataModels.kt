package com.orion.app.face.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

@Entity
data class FaceImageRecord(
    // primary-key of `FaceImageRecord`
    @Id var recordID: Long = 0,
    // personId is derived from `PersonRecord`
    @Index var personID: Long = 0,
    var personName: String = "",
    // the FaceNet model provides a 512-dimensional embedding
    @HnswIndex(
        dimensions = 512,
        distanceType = VectorDistanceType.COSINE,
    ) var faceEmbedding: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceImageRecord

        if (recordID != other.recordID) return false
        if (personID != other.personID) return false
        if (personName != other.personName) return false
        if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = recordID.hashCode()
        result = 31 * result + personID.hashCode()
        result = 31 * result + personName.hashCode()
        result = 31 * result + faceEmbedding.contentHashCode()
        return result
    }
}

@Entity
data class PersonRecord(
    // primary-key
    @Id var personID: Long = 0,
    var personName: String = "",
    // number of images enrolled by the user
    var numImages: Long = 0,
    // time when the record was added
    var addTime: Long = 0,
)

data class RecognitionMetrics(
    val timeFaceDetection: Long,
    val timeVectorSearch: Long,
    val timeFaceEmbedding: Long,
    val timeFaceSpoofDetection: Long,
)
