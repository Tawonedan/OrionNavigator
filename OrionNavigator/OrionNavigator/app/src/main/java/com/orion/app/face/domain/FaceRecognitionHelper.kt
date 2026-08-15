package com.orion.app.face.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.orion.app.face.data.FaceImageRecord
import com.orion.app.face.data.ImagesVectorDB
import com.orion.app.face.data.ObjectBoxStore
import com.orion.app.face.data.PersonDB
import com.orion.app.face.data.PersonRecord
import com.orion.app.face.data.RecognitionMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class FaceRecognitionHelper(private val context: Context) {

    data class FaceRecognitionResult(
        val personName: String,
        val boundingBox: Rect,
        val confidence: Float,
        val isSpoof: Boolean,
        val isRecognized: Boolean,
    )

    val faceDetector: BaseFaceDetector by lazy { MLKitFaceDetector(context) }
    val faceSpoofDetector: FaceSpoofDetector by lazy { FaceSpoofDetector(context) }
    val faceNet: FaceNet by lazy { FaceNet(context) }
    val vectorDB: ImagesVectorDB by lazy { ImagesVectorDB() }
    val personDB: PersonDB by lazy { PersonDB() }

    init {
        ObjectBoxStore.init(context)
    }

    /**
     * Enrolls a new person from an image URI (from camera or gallery)
     */
    suspend fun enrollPerson(personName: String, imageUri: Uri): Result<PersonRecord> =
        withContext(Dispatchers.IO) {
            val faceDetectionResult = faceDetector.getCroppedFace(imageUri)
            if (faceDetectionResult.isFailure) {
                return@withContext Result.failure(
                    faceDetectionResult.exceptionOrNull() ?: Exception("Face detection failed")
                )
            }

            val croppedFace = faceDetectionResult.getOrNull()!!
            val embedding = faceNet.getFaceEmbedding(croppedFace)

            val personId = personDB.addPerson(
                PersonRecord(
                    personName = personName,
                    numImages = 1,
                    addTime = System.currentTimeMillis(),
                )
            )

            vectorDB.addFaceImageRecord(
                FaceImageRecord(
                    personID = personId,
                    personName = personName,
                    faceEmbedding = embedding,
                )
            )

            return@withContext Result.success(
                PersonRecord(
                    personID = personId,
                    personName = personName,
                    numImages = 1,
                    addTime = System.currentTimeMillis(),
                )
            )
        }

    /**
     * Enrolls a person with a directly cropped Bitmap (e.g. from camera)
     */
    suspend fun enrollPersonFromBitmap(personName: String, faceBitmap: Bitmap): Result<PersonRecord> =
        withContext(Dispatchers.IO) {
            try {
                val embedding = faceNet.getFaceEmbedding(faceBitmap)
                val personId = personDB.addPerson(
                    PersonRecord(
                        personName = personName,
                        numImages = 1,
                        addTime = System.currentTimeMillis(),
                    )
                )

                vectorDB.addFaceImageRecord(
                    FaceImageRecord(
                        personID = personId,
                        personName = personName,
                        faceEmbedding = embedding,
                    )
                )

                Result.success(
                    PersonRecord(
                        personID = personId,
                        personName = personName,
                        numImages = 1,
                        addTime = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Deletes a person and all their vector embeddings
     */
    suspend fun deletePerson(personID: Long) = withContext(Dispatchers.IO) {
        vectorDB.removeFaceRecordsWithPersonID(personID)
        personDB.removePerson(personID)
    }

    /**
     * Processes a live frame: detects all faces, extracts embeddings, searches vector DB, checks liveness
     */
    suspend fun processFrame(
        frameBitmap: Bitmap,
        flatSearch: Boolean = false,
    ): Pair<RecognitionMetrics?, List<FaceRecognitionResult>> =
        withContext(Dispatchers.Default) {
            val (faceDetectionResult, t1) = measureTimedValue {
                faceDetector.getAllCroppedFaces(frameBitmap)
            }

            val results = ArrayList<FaceRecognitionResult>()
            var avgT2 = 0L
            var avgT3 = 0L
            var avgT4 = 0L

            for ((croppedBitmap, boundingBox) in faceDetectionResult) {
                val (embedding, t2) = measureTimedValue { faceNet.getFaceEmbedding(croppedBitmap) }
                avgT2 += t2.toLong(DurationUnit.MILLISECONDS)

                val (nearestMatch, t3) = measureTimedValue {
                    vectorDB.getNearestEmbeddingPersonName(embedding, flatSearch)
                }
                avgT3 += t3.toLong(DurationUnit.MILLISECONDS)

                val spoofResult = faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
                avgT4 += spoofResult.timeMillis

                if (nearestMatch == null) {
                    results.add(
                        FaceRecognitionResult(
                            personName = "Tidak Dikenal",
                            boundingBox = boundingBox,
                            confidence = 0f,
                            isSpoof = spoofResult.isSpoof,
                            isRecognized = false,
                        )
                    )
                    continue
                }

                val similarity = cosineSimilarity(embedding, nearestMatch.faceEmbedding)
                if (similarity > 0.55f) {
                    results.add(
                        FaceRecognitionResult(
                            personName = nearestMatch.personName,
                            boundingBox = boundingBox,
                            confidence = similarity,
                            isSpoof = spoofResult.isSpoof,
                            isRecognized = true,
                        )
                    )
                } else {
                    results.add(
                        FaceRecognitionResult(
                            personName = "Tidak Dikenal",
                            boundingBox = boundingBox,
                            confidence = similarity,
                            isSpoof = spoofResult.isSpoof,
                            isRecognized = false,
                        )
                    )
                }
            }

            val metrics = if (faceDetectionResult.isNotEmpty()) {
                RecognitionMetrics(
                    timeFaceDetection = t1.toLong(DurationUnit.MILLISECONDS),
                    timeFaceEmbedding = avgT2 / faceDetectionResult.size,
                    timeVectorSearch = avgT3 / faceDetectionResult.size,
                    timeFaceSpoofDetection = avgT4 / faceDetectionResult.size,
                )
            } else {
                null
            }

            Pair(metrics, results)
        }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        for (i in x1.indices) {
            mag1 += x1[i].pow(2)
            mag2 += x2[i].pow(2)
            product += x1[i] * x2[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        if (mag1 == 0f || mag2 == 0f) return 0f
        return product / (mag1 * mag2)
    }

    fun getTotalPersonsCount(): Long = personDB.getCount()
}
