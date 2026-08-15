# 📱 REKAPITULASI TEKNIS APLIKASI ORION NAVIGATOR

> **Dokumen Spesifikasi & Rekapitulasi Arsitektur Fitur Orion Navigator**  
> *Platform: Android (Kotlin)* | *Target SDK: 34 (Android 14)* | *Desain: Accessibility & Audio-First*

---

## 📖 1. Ikhtisar Aplikasi (App Overview)

**Orion Navigator** adalah ekosistem aplikasi Android pintar yang dirancang khusus untuk memandu dan menjaga keselamatan pengguna tunanetra (*visually impaired users*). Aplikasi ini menggabungkan navigasi spasial indoor berbasis **Augmented Reality (ARCore)**, pemantauan lokasi jarak jauh (**Live Location**) bagi caregiver/orang tua, penglihatan komputer cerdas (**Kamera AI & Computer Vision**) untuk deteksi rintangan real-time, serta **Pengenalan Wajah On-Device (Face Recognition & Anti-Spoofing)** berbasis Vector Database untuk mengidentifikasi orang di sekitar pengguna secara instan dan aman.

Aplikasi mendukung **Dual-Role System**:
1. **User Tunanetra (Child):** Antarmuka utama berbasis **Audio-First & Accessible Design**, didukung oleh **Sistem Perintah Suara 3-Layer (Voice Command System)**, gesture sederhana, dan sintesis **Text-to-Speech (TTS)** Bahasa Indonesia. Tunanetra memiliki akses ke 4 fitur pilar:
   - **Live Location Sharing:** Berbagi lokasi real-time dengan orang tua/pendamping.
   - **Navigasi 3D AR Spasial:** Panduan jalur indoor 6DoF menggunakan kamera ARCore dan algoritma Dijkstra.
   - **Kamera AI & Vision:** Deteksi rintangan real-time (YOLOv8) dan deskripsi pemandangan generatif (Google Gemini Vision).
   - **Kenali Wajah (Face Recognition):** Identifikasi instan anggota keluarga, teman, atau pendamping dengan verifikasi anti-spoofing dan basis data vektor lokal.
2. **User Pendamping / Caregiver (Parent):** Hub khusus (`PendampingHomeActivity`) yang menyediakan 3 modul utama:
   - **Pemantauan Live Location:** Peta interaktif OpenStreetMap (OSMDroid) dengan batas geofencing aman (*Safe Zones*).
   - **Pembuatan Peta AR (AR Map Authoring):** Memindai ruangan, menempatkan penanda 3D (*Waypoints*), dan menyambungkan jalur rute navigasi (*Walkable Paths*).
   - **Kelola Wajah (Face Data Management):** Mendaftarkan foto wajah keluarga/kerabat melalui pemindaian kamera interaktif atau galeri, serta mengelola basis data vektor wajah lokal.

---

## 🔵 2. Fitur Utama 1: Live Location (Pelacakan Lokasi Real-Time & Caregiver Hub)

### 👥 Konsep & Tujuan Fitur
Fitur **Live Location** memungkinkan pengguna tunanetra (*Child*) membagikan lokasi geografis mereka secara real-time kepada pendamping/orang tua (*Parent/Caregiver*). Fitur ini berfokus pada **keamanan, privasi, dan persetujuan 1-to-1**, dilengkapi dengan sistem **Geofencing (Safe Zones)** untuk memberi peringatan otomatis saat pengguna keluar dari area aman yang ditentukan.

Caregiver mengakses sistem melalui **Pendamping Home Screen (`PendampingHomeActivity.kt`)**, yang menyediakan akses terpadu ke modul pemantauan lokasi, pembuatan peta indoor AR, dan manajemen pendaftaran wajah.

```
[User Tunanetra (Child)]
       │
       ├── GPS Acquisition (FusedLocationProviderClient)
       ├── Foreground Service (LocationUpdateService)
       │
       ▼ (Publish Data: lat, lng, timestamp, accuracy)
[Firebase Realtime Database]
       ▲
       │ (Subscribe & Real-time Stream Updates)
       │
[Caregiver Hub (PendampingHomeActivity)] ──► 1. Pemantauan Live Location (OSMDroid + Safe Zones)
                                           ├──► 2. Pembuatan Peta AR (DirectionActivity MAP Mode)
                                           └──► 3. Kelola Wajah (ManageFacesActivity + ObjectBox DB)
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Hub Utama Pendamping (PendampingHomeActivity.kt)**
   - **Komponen Teknis:** `PendampingHomeActivity.kt`, `RoleSelectionActivity.kt`, `SplashActivity.kt`
   - **Konsep:** Menu navigasi utama untuk peran Pendamping. Alur autentikasi/role mengarahkan Pendamping ke hub ini untuk memilih antara pemantauan peta live (`LiveLocationParentActivity`), pembuatan/pemetaan rute AR baru (`DirectionActivity` dengan `EXTRA_APP_MODE = "MAP"`), atau pendaftaran data wajah keluarga (`ManageFacesActivity`).

2. **Peta Base Layer (OSMDroid / OpenStreetMap)**
   - **Dependency:** `org.osmdroid:osmdroid-android:6.1.17`
   - **Konsep:** Menggunakan OpenStreetMap (OSM) via library OSMDroid untuk rendering peta. Pendekatan ini bersifat *Map-Agnostic* (tanpa ketergantungan pada Google Maps API Key atau biaya billing proprietary), memungkinkan penyajian marker lokasi pengguna tunanetra dan zona aman secara efisien dan independen.

3. **Pengambilan Lokasi Presisi (Fused Location Provider)**
   - **Dependency:** `com.google.android.gms:play-services-location:21.0.1`
   - **Konsep:** Menggunakan `FusedLocationProviderClient` dengan mode `PRIORITY_HIGH_ACCURACY` untuk mengambil koordinat Latitude, Longitude, Altitude, Bearing, dan Accuracy secara berkala (interval 30–60 detik atau perubahan jarak >20 meter).

4. **Layanan Latar Belakang Tahan Mati (Foreground Service)**
   - **Komponen Teknis:** `LocationUpdateService.kt`
   - **Konsep:** Mengimplementasikan Android `ForegroundService` dengan notifikasi persisten (`STICKY_SERVICE`). Hal ini memastikan sistem terus memperbarui lokasi ke backend meskipun layar smartphone dimatikan atau aplikasi berada di latar belakang.

5. **Sinkronisasi Data Real-Time (Firebase Realtime Database & Auth)**
   - **Dependencies:** `com.google.firebase:firebase-database-ktx` & `com.google.firebase:firebase-auth-ktx` (Firebase BoM 32.7.0)
   - **Komponen Teknis:** `LiveLocationRepository.kt`, `LiveLocationChildActivity.kt`, `LiveLocationParentActivity.kt`
   - **Konsep:** 
     - **Pairing Code 1-to-1:** Pengguna tunanetra membuat 6-digit kode unik (`generateLinkCode()`). Pendamping memasukkan kode tersebut untuk membentuk *pair bonding*.
     - **Real-Time Data Stream:** Posisi dipublikasikan ke node `locations/{childId}` dan dipantau oleh Parent via Kotlin Flow / Realtime Listener `ValueEventListener`.

6. **Zona Aman (Geofencing / Safe Zones)**
   - **Komponen Teknis:** `SafeZonesActivity.kt`, `SafeZone.kt`
   - **Konsep:** Menghitung jarak Euclidean / Haversine antara koordinat terkini pengguna dengan titik pusat zona aman. Jika jarak melebihi radius batas, sistem secara otomatis mengirimkan notifikasi peringatan ke pendamping.

---

## 3D 3. Fitur Utama 2: Navigasi 3D AR & Spatial Indoor Positioning (Pemetaan & Navigasi)

### 🧭 Konsep & Tujuan Fitur
Fitur **3D AR Navigation** memandu pengguna tunanetra di dalam ruangan (indoor) dari satu titik lokasi ke lokasi tujuan. Seluruh logika navigasi indoor **100% menggunakan pelacakan ARCore (VIO)** dan peta graf spasial 3D, sepenuhnya menggantikan sinyal BLE beacon legacy.

Sistem memiliki 2 mode pengoperasian utama:
1. **Mode MAP (Authoring oleh Pendamping):** Pendamping memindai lingkungan, menandai Waypoint 3D (Pintu, Ruangan, Koridor), dan menyambungkan jalur rute yang dapat dilalui (*walkable paths*).
2. **Mode NAVIGATE (Panduan untuk Tunanetra):** Algoritma Dijkstra menghitung rute terpendek, lalu sistem menggambar bola-bola petunjuk 3D di ruang AR serta memberikan instruksi audio kompas.

```
[Mode MAP - Authoring Pendamping] ──► Penempatan Waypoint 3D (Crosshair Visual Indicator)
                                      └──► Sambungkan Jalur Walkable (Interactive Path Connecting)
                                                 │
                                                 ▼
[Spatial Graph & Pathfinding] ─────────► Dijkstra Algorithm (Shortest Path Search)
                                                 │
                                                 ▼
[Mode NAVIGATE - Guidance Tunanetra] ──► 3D OpenGL Rendering (Guide Spheres & Waypoints)
                                         + Sensor Manager Compass + TTS Audio Guidance
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Pemetaan & Penempatan Penanda Interaktif (AR Map Authoring Mode)**
   - **Komponen Teknis:** `DirectionActivity.kt`, `WaypointPlacementHelper.kt`, `bg_crosshair_ring.xml`, `bg_crosshair_dot.xml`
   - **Konsep:** 
     - **Crosshair Status Visual:** Dalam mode MAP, antarmuka menampilkan overlay crosshair dinamis (`camCrosshairRing` & `camCrosshairDot`). Warna lingkaran berubah secara otomatis: **Hijau (`#00E676`)** menandakan permukaan/pose valid untuk penempatan penanda, sedangkan **Merah (`#FF1744`)** menandakan area belum terdeteksi.
     - **Kategori Penanda:** Pendamping dapat menandai kategori Waypoint (`DOOR`, `ROOM`, `HALLWAY`, `OTHER`) dengan penamaan kustom yang disimpan ke JSON lokal (`WaypointPersistence`).

2. **Pengelolaan Jalur Interaktif (Walkable Path Connections & Graph Persistence)**
   - **Komponen Teknis:** `DirectionActivity.kt`, `GraphPersistence.kt`, `GraphData.kt`
   - **Konsep:** 
     - **Penyambungan Jalur Rute:** Pendamping menghubungkan dua penanda spasial melalui dialog interaktif (*Selection-based Connector*). Pilihan penanda pertama dan kedua akan membentuk *edge* tak berarah (*undirected graph edge*) dengan bobot jarak spasial.
     - **Daftar Koneksi Aktif & Penghapusan Instan:** Dialog menampilkan seluruh koneksi aktif (`A ↔ B`) dan menyediakan tombol hapus instan dengan konfirmasi audio TTS (*"Terhubung [A] dengan [B]"* / *"Koneksi dihapus"*).

3. **Augmented Reality Engine & Tracking 100% ARCore (Depresiasi Total BLE)**
   - **Dependency:** `com.google.ar:core:1.54.0`
   - **Komponen Teknis:** `DirectionActivity.kt`, `ArCoreTrackingToken.kt`, `ARCoreSessionLifecycleHelper.kt`, `TrackingStateHelper.kt`
   - **Konsep:** 
     - **Visual-Inertial Odometry (VIO):** Menggabungkan data sensor IMU dan analisis fitur visual kamera untuk memetakan koordinat spasial 3D 6DoF (X, Y, Z translation & Rotation Quaternion). Pemrosesan BLE beacon legacy telah dinonaktifkan penuh.
     - **Cloud Anchors Resolution:** Menggunakan `GoogleCloudBackend.kt` untuk menyimpan dan memulihkan (*resolve*) titik anchor lingkungan fisik agar posisi waypoint konsisten antar sesi.

4. **Rendering Grafik 3D Custom (OpenGL ES 3.0 & OBJ Parser)**
   - **Dependency:** `de.javagl:obj:0.4.0` (Wavefront OBJ Parser)
   - **Komponen Teknis:** `SampleRender.kt`, `BackgroundRenderer.kt`, Vertex & Fragment Shaders (`shaders/point_cloud`, `shaders/waypoint_marker`)
   - **Konsep:** 
     - Render pipeline OpenGL ES 3.0 custom untuk memproyeksikan feed kamera sebagai latar belakang (`BackgroundRenderer`) sekaligus menggambar objek 3D di ruang spasial.
     - **Waypoints & Navigation Spheres:** Memuat model 3D (`pawn.obj`, `nav_sphere.obj`) dan menggambar bola-bola petunjuk jalan 3D di sepanjang lintasan menuju target waypoint secara dinamis.

5. **Algoritma Pencarian Jalur Indoor (Graph Data & Dijkstra)**
   - **Komponen Teknis:** `IndoorNavigationManager.kt`, `Dijkstra.kt`, `GraphData.kt`, `WaypointRepository.kt`
   - **Konsep:** Algoritma **Dijkstra** menghitung rute paling efisien dari posisi ARCore perangkat ke waypoint tujuan berdasarkan matriks graf rute *walkable path* yang dibuat oleh Pendamping.

6. **Kalkulasi Arah & Orientasi (Sensor Manager / Magnetometer & Accelerometer)**
   - **Komponen Teknis:** `CompassManager.kt`
   - **Konsep:** Mengakses sensor bawaan `Sensor.TYPE_ACCELEROMETER` dan `Sensor.TYPE_MAGNETIC_FIELD` dengan *Low-Pass Filter* untuk memandu orientasi pengguna tunanetra via umpan balik haptik (`Vibrator`) dan audio beeper (`ToneGenerator`).

---

## 👁️ 4. Fitur Utama 3: Kamera AI untuk Deteksi Objek Real-Time & Deskripsi Pemandangan

### 🤖 Konsep & Tujuan Fitur
Fitur **AI Camera** bertindak sebagai "mata digital" bagi tunanetra. Fitur ini menganalisis aliran video kamera secara real-time untuk mendeteksi rintangan (*obstacle detection*) di sekitar pengguna (misalnya kursi, tangga, orang, pintu, atau meja) dan menyuarakan peringatan secara langsung, serta mampu menjelaskan pemandangan secara rinci menggunakan kecerdasan buatan generatif.

```
[CameraX Live Stream] ──► Frame Conversion (NV21 / YUV -> Bitmap)
                                   │
                                   ├──► [TFLite YOLOv8 Engine] ──► Realtime Obstacle Detection (>= 70% Conf)
                                   │                                 ├── Overlay Bounding Box
                                   │                                 └── TTS Announcement ("Ada kursi 1m di depan")
                                   │
                                   └──► [Google Gemini Vision API] ──► Generative Scene Description ("Jelaskan")
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Inference Engine Lokal (TensorFlow Lite & Support Library)**
   - **Dependencies:** `org.tensorflow:tensorflow-lite:2.14.0` & `org.tensorflow:tensorflow-lite-support:0.4.4`
   - **Komponen Teknis:** `YoloDetectorHelper.kt`, `CameraAIActivity.kt`
   - **Konsep:** 
     - **Model YOLOv8 (You Only Look Once):** Menggunakan model deteksi objek YOLOv8 terkuantisasi (`yolov8n_float32.tflite` / `float16`) yang berjalan secara offline di *on-device NPU/CPU*.
     - **Post-Processing (NMS):** Menerapkan algoritma *Non-Maximum Suppression* (NMS) dan persentase *IoU (Intersection over Union)* untuk memfilter kotak deteksi ganda dan menyajikan label dengan tingkat keyakinan (*confidence threshold*) terbaik.

2. **Aliran Video Berkinerja Tinggi & Flip Camera (Android CameraX SDK)**
   - **Dependencies:** `androidx.camera:camera-core:1.2.3`, `camera-camera2`, `camera-lifecycle`, `camera-view`
   - **Komponen Teknis:** `CameraManager.kt`
   - **Konsep:** 
     - Menggunakan `ImageAnalysis.Analyzer` dari CameraX dengan skema *Frame Skipping* untuk menjaga performa perangkat tetap dingin dan responsif.
     - **Flip Camera Support:** Modul `CameraManager.kt` dilengkapi method `flipCamera()` dan `isFrontFacing()` untuk beralih mulus antara kamera belakang (*back camera*) dan kamera depan (*front/selfie camera*).

3. **Overlay Bounding Box & Translasi Label**
   - **Komponen Teknis:** `DetectionOverlayView.kt` / Custom Canvas Bounding Box
   - **Konsep:** Menggambar ulang hasil deteksi objek secara visual pada *Canvas Overlay* yang disesuaikan dengan skala layar. Label objek berbahasa Inggris diterjemahkan secara otomatis ke Bahasa Indonesia secara real-time.

4. **Kecerdasan Buatan Generatif (Google Gemini Vision API)**
   - **Komponen Teknis:** `GeminiHelper.kt`
   - **Konsep:** Ketika pengguna menekan tombol atau mengucapkan perintah *"Jelaskan"*, frame Bitmap terbaru dikirim ke **Google Gemini REST API** via Kotlin Coroutines. Gemini menganalisis konteks penuh gambar dan menghasilkan narasi pemandangan komprehensif dalam Bahasa Indonesia.

---

## 👤 5. Fitur Utama 4: Pengenalan Wajah On-Device & Anti-Spoofing (Face Recognition & Vector Search)

### 👥 Konsep & Tujuan Fitur
Fitur **Face Recognition** memungkinkan pengguna tunanetra mengenali identitas orang di hadapannya (keluarga, teman, atau pendamping) secara instan, mandiri, dan 100% offline (*on-device Edge AI*). Sistem ini mengintegrasikan deteksi wajah real-time, ekstraksi vektor embedding 512D berbasis **PyTorch ExecuTorch FaceNet**, perlindungan dari pemalsuan wajah (**Dual-Scale Silent-Face Anti-Spoofing**), serta pencarian kemiripan kosinus instan menggunakan basis data vektor **ObjectBox Vector Search (HNSW Index)**.

```
[CameraX Live Stream (Front/Back)]
                │
                ▼ (NV21 to Bitmap + Rotation Correction)
[Google ML Kit Face Detector] ──► Face Bounding Box & Pose (Euler Yaw/Pitch/Center)
                │
        ┌───────┴───────────────────────────────┐
        ▼                                       ▼
[PyTorch ExecuTorch FaceNet]           [Dual-Scale FASNet Anti-Spoofing]
(Extract 512D Float Embedding)         (TFLite Scale 2.7x & 4.0x Softmax Fusion)
        │                                       │
        ▼                                       ▼
[ObjectBox Vector DB (HNSW Index)]      [Liveness Validation: Real vs Spoof]
(Nearest Neighbor Cosine Search >0.55)          │
        │                                       │
        └───────────────────┬───────────────────┘
                            ▼
        [Face Recognition Output & Accessibility Feedback]
          ├── Canvas Visual Overlay (Green=Known, Orange=Unknown, Red=Spoof)
          ├── Debounced TTS Announcement ("Ada [Nama] di depan Anda")
          ├── Haptic Pulse Vibration on Match
          └── Voice Command Trigger ("Siapa ini?")
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Deteksi Wajah Real-Time & Estimasi Pose (Google ML Kit Face Detection)**
   - **Dependency:** `com.google.mlkit:face-detection:16.1.7`
   - **Komponen Teknis:** `MLKitFaceDetector.kt`, `BaseFaceDetector.kt`
   - **Konsep:** 
     - **Dual Performance Mode:** Menggunakan `PERFORMANCE_MODE_FAST` untuk streaming frame kamera secara *live* dan `PERFORMANCE_MODE_ACCURATE` untuk pemindaian gambar statis beresolusi tinggi saat pendaftaran wajah.
     - **Face Pose & Alignment Estimation (`getFacePose`):** Mengevaluasi sudut rotasi kepala (Euler Yaw & Pitch < 15°), rasio lebar wajah terhadap frame (25% - 85%), serta pemusatan koordinat (*centering*) untuk menjamin kualitas sampel foto saat pendaftaran wajah.

2. **Ekstraksi Fitur Vektor Wajah 512D On-Device (PyTorch ExecuTorch & FaceNet)**
   - **Dependency:** `org.pytorch:executorch-android:1.2.0`
   - **Model Binary:** `model.pte` (FaceNet ExecuTorch module di folder `assets/`)
   - **Komponen Teknis:** `FaceNet.kt`
   - **Konsep:** 
     - Memproses potongan wajah berukuran 160x160 piksel RGB dan mengekstrak representasi fitur wajah berdimensi 512 (`Tensor.fromBlob(1, 160, 160, 3)`).
     - Berjalan langsung di atas runtime **ExecuTorch**, runtime komputasi AI on-device terbaru dari PyTorch yang dioptimalkan untuk performa tinggi dan konsumsi memori/daya yang sangat hemat pada arsitektur ARM Android.

3. **Deteksi Keaslian Wajah & Anti-Spoofing (Dual-Scale Silent-Face FASNet)**
   - **Dependencies:** `org.tensorflow:tensorflow-lite:2.14.0` & `org.tensorflow:tensorflow-lite-support:0.4.4`
   - **Model Binaries:** `spoof_model_scale_2_7.tflite` & `spoof_model_scale_4_0.tflite`
   - **Komponen Teknis:** `FaceSpoofDetector.kt`
   - **Konsep:** 
     - Mencegah serangan pemalsuan identitas (*presentation attacks*) menggunakan foto cetak atau layar HP/tablet.
     - Menggunakan arsitektur FASNet dual-scale dengan memotong area wajah pada dua skala pembesaran berbeda (**Scale 2.7x** untuk detail kontur dan **Scale 4.0x** untuk konteks batas tepi).
     - Mengonversi citra ke format BGR 80x80 piksel, mengeksekusi kedua model TFLite secara multi-thread (4 threads), dan menggabungkan probabilitas output melalui fungsi *Softmax Fusion*.

4. **Basis Data Vektor Lokal & Pencarian Kemiripan (ObjectBox Vector Search & HNSW Index)**
   - **Dependencies:** `io.objectbox:objectbox-android:4.0.0` & `io.objectbox:objectbox-kotlin:4.0.0` (Gradle Plugin `io.objectbox:4.0.0`)
   - **Komponen Teknis:** `DataModels.kt` (`FaceImageRecord`, `PersonRecord`, `RecognitionMetrics`), `ImagesVectorDB.kt`, `PersonDB.kt`, `ObjectBoxStore.kt`
   - **Konsep:** 
     - **HNSW Vector Index:** Entitas `FaceImageRecord` memanfaatkan indeks vektor bawaan ObjectBox dengan anotasi `@HnswIndex(dimensions = 512, distanceType = VectorDistanceType.COSINE)`.
     - **Pencarian Nearest Neighbor Instan:** Menemukan rekaman wajah terdekat dengan latensi < 10ms menggunakan kueri `FaceImageRecord_.faceEmbedding.nearestNeighbors(embedding, 10)`.
     - **Thresholding Kosinus:** Kecocokan dikonfirmasi jika *Cosine Similarity* melampaui batas ambang keyakinan (default > 0.55 / 55%).
     - **Kaskade Penghapusan Data:** Menghapus entitas `PersonRecord` secara otomatis membersihkan seluruh vektor embedding terkait di `ImagesVectorDB`.

5. **Antarmuka Interaktif Pendaftaran Wajah (AddFaceActivity.kt)**
   - **Komponen Teknis:** `AddFaceActivity.kt`, `activity_add_face.xml`
   - **Konsep:** 
     - **Panduan Suara Berkelanjutan:** Memberi instruksi suara real-time (*"Wajah belum di tengah"*, *"Terlalu jauh, dekatkan ponsel"*, *"Harap menghadap lurus"*).
     - **Indikator Kualitas Dinamis:** Menghitung skor kualitas wajah (0% hingga 100%). Ketika kualitas mencapai 100% dan lolos uji anti-spoofing, sistem secara otomatis menangkap frame (*Auto-Capture*) dan menyimpannya ke basis data vektor.
     - **Pendaftaran Fleksibel:** Menyediakan opsi pendaftaran manual (*Snap Now*), pembalik kamera depan/belakang (*Flip Camera*), serta impor foto dari galeri HP (*Gallery Import*).

6. **Antarmuka Pengenalan Wajah Real-Time Tunanetra (FaceRecognitionActivity.kt)**
   - **Komponen Teknis:** `FaceRecognitionActivity.kt`, `FaceDetectionOverlayView.kt`, `activity_face_recognition.xml`
   - **Konsep:** 
     - **Visual Overlay Dinamis:** Menggambar kotak deteksi berstatus:
       - **Hijau (`#00E676`):** Wajah terdaftar dikenali beserta nama dan persentase keyakinan.
       - **Oranye (`#FF9100`):** Wajah terdeteksi namun belum terdaftar di basis data.
       - **Merah (`#FF1744`):** Wajah terdeteksi sebagai serangan palsu (*Spoof Attack*).
     - **Debounce TTS Announcement:** Menerapkan jeda cooldown 4 detik per individu teridentifikasi (`DEBOUNCE_COOLDOWN_MS = 4000L`) agar suara pengumuman tidak bertabrakan atau berulang secara berlebihan.
     - **Umpan Balik Haptik:** Memberikan getaran singkat (*Haptic Pulse*) saat seseorang berhasil dikenali.
     - **Integrasi Perintah Suara:** Pengguna dapat menekan tombol mic atau mengucap *"Hello Orion, siapa di depan saya?"* untuk verifikasi langsung.

7. **Manajemen Daftar Wajah Terdaftar (ManageFacesActivity.kt)**
   - **Komponen Teknis:** `ManageFacesActivity.kt`, `PersonListAdapter.kt`, `activity_manage_faces.xml`, `item_person_card.xml`
   - **Konsep:** Layar khusus bagi pendamping untuk melihat seluruh daftar orang yang terdaftar, melihat tanggal penambahan, menambah wajah baru, serta menghapus data wajah dengan dialog konfirmasi yang aman. Perubahan data dipantau secara reaktif menggunakan Kotlin `Flow`.

---

## 🎙️ 6. Fitur Suplemen Utama: Sistem Perintah Suara & Asisten Aksesibilitas (Voice Command System)

### 🗣️ Konsep & Tujuan Fitur
Untuk memastikan antarmuka 100% ramah tunanetra, seluruh aktivitas utama dalam Orion Navigator diselaraskan dengan **Voice Command System**. Pengguna tidak perlu melihat atau menyentuh tombol di layar, melainkan cukup mengendalikan aplikasi menggunakan suara.

```
[Continuous Audio Input] ──► [Layer 1: Wake Word Engine ("Hello Orion" Fuzzy Match)]
                                       │
                                       ▼ (Detected)
                             [Layer 2: Local Keyword Dictionary (Offline, <50ms)]
                                       │
                                       ▼ (If Ambiguous)
                             [Layer 3: Gemini NLU Classifier (AI Intent Parser)]
                                       │
                                       ▼
                             [Action Execution & TTS Voice Response]
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Pengenalan Suara Native (Android SpeechRecognizer API)**
   - **API Native:** `android.speech.SpeechRecognizer` & `android.speech.RecognizerIntent`
   - **Komponen Teknis:** `VoiceCommandManager.kt`, `VoiceIntent.kt`
   - **Konsep:** Menangkap aliran audio mikrofon secara kontinu (`LANGUAGE_MODEL_FREE_FORM`) dalam setelan Bahasa Indonesia (`id-ID`).

2. **Arsitektur Pengendalian Suara 3 Layer (Triple-Layer Voice Architecture)**
   - **Layer 1: Passive Wake Word Listener ("Hello Orion / Halo Orion")**
     - Mendengarkan secara pasif di latar belakang. Menggunakan algoritma matching jarak string **Levenshtein Distance** (*fuzzy matching*) untuk mengenali puluhan variasi pengucapan wake word (misal: "Halo Orion", "Hey Orion", "Halo Oreo", "Hello Rian") tanpa terpengaruh aksen.
   - **Layer 2: Instant Offline Keyword Matching**
     - Mencocokkan teks ucapan secara offline dengan kamus kata kunci lokal (`matchKeyword()`). Mengeksekusi perintah instan dengan latensi < 50ms untuk seluruh fitur inti:
       - *"Buka Navigasi"* (`VoiceIntent.OPEN_NAVIGATION`)
       - *"Buka Kamera"* (`VoiceIntent.OPEN_CAMERA`)
       - *"Buka Wajah / Kenali Wajah / Deteksi Wajah"* (`VoiceIntent.OPEN_FACE_RECOGNITION`)
       - *"Siapa di depan saya / Siapa ini / Cek orang"* (`VoiceIntent.IDENTIFY_PERSON`)
       - *"Dimana saya / Buka Lokasi"* (`VoiceIntent.CHECK_LOCATION` / `VoiceIntent.OPEN_LIVE_LOCATION`)
       - *"Jelaskan"* (`VoiceIntent.DESCRIBE_SCENE`)
       - *"Suara Mati / Suara Hidup"* (`VoiceIntent.TOGGLE_SOUND_OFF / ON`)
       - *"Kembali / Keluar"* (`VoiceIntent.GO_BACK` / `VoiceIntent.LOGOUT`)
   - **Layer 3: Gemini NLU Fallback (Natural Language Understanding)**
     - **Komponen Teknis:** `GeminiCommandProcessor.kt`
     - Jika ucapan pengguna bernada natural, panjang, atau bermakna ganda (misal: *"tolong lihat siapa orang yang sedang berdiri di depanku"*), input teks dikirim ke Google Gemini API dengan prompt klasifikasi intent untuk mengekstrak maksud pengguna secara cerdas ke salah satu enum `VoiceIntent`.

3. **Output Auditori (Text-To-Speech / TTS Manager)**
   - **API Native:** `android.speech.tts.TextToSpeech`
   - **Komponen Teknis:** `TTSManager.kt`
   - **Konsep:** Menyuarakan semua respon, instruksi navigasi, pengenalan wajah, dan hasil deteksi rintangan menggunakan Bahasa Indonesia (`Locale("id", "ID")`). Menerapkan skema *Debounce & Priority Queue* agar instruksi keselamatan tidak terpotong oleh suara pembacaan objek sekunder.

4. **Mekanisme Pencegahan Feedback Loop (TTS Audio Muting)**
   - **Komponen Teknis:** `pauseForTTS()` dan `resumeAfterTTS()` pada `VoiceCommandManager.kt`
   - **Konsep:** Menerapkan callback `UtteranceProgressListener` pada TTS. Ketika TTS mulai berbicara, pengenalan suara (*SpeechRecognizer*) dihentikan sementara (*pause*) dan suara beep notifikasi di-mute. Setelah TTS selesai berbicara, perekaman suara diaktifkan kembali secara otomatis. Hal ini mencegah pengenalan suara "mendengarkan suaranya sendiri" dari speaker smartphone.

---

## 📊 7. Tabel Rangkuman Arsitektur & Dependensi Utama

| Fitur Utama | Modul / Kelas Utama | Dependensi / Library Utama | Konsep Teknis Utama |
| :--- | :--- | :--- | :--- |
| **Live Location & Caregiver Hub** | `PendampingHomeActivity`<br>`LiveLocationChildActivity`<br>`LiveLocationParentActivity`<br>`LocationUpdateService`<br>`LiveLocationRepository` | • `org.osmdroid:osmdroid-android:6.1.17`<br>• `play-services-location:21.0.1`<br>• `firebase-database-ktx`<br>• `firebase-auth-ktx` | Caregiver Hub (Live Location, Buat Peta AR, Kelola Wajah), OpenStreetMap rendering map-agnostic, 1-to-1 code pairing, persistent Foreground Service, real-time Firebase DB sync, Haversine Geofencing. |
| **3D AR Navigation & Authoring** | `DirectionActivity`<br>`IndoorNavigationManager`<br>`CompassManager`<br>`WaypointRepository`<br>`GraphPersistence.kt`<br>`Dijkstra.kt` | • `com.google.ar:core:1.54.0`<br>• `de.javagl:obj:0.4.0`<br>• Custom OpenGL ES 3.0 Shaders<br>• Android Hardware SensorManager | 6DoF Visual-Inertial Odometry, 100% ARCore (Depresiasi BLE), Penempatan Waypoint via visual Crosshair, Dialog penyambung rute walkable path interaktif, Custom OpenGL 3D path spheres rendering, Dijkstra shortest-path algorithm. |
| **AI Camera** | `CameraAIActivity`<br>`CameraManager`<br>`YoloDetectorHelper`<br>`GeminiHelper` | • `tensorflow-lite:2.14.0`<br>• `tensorflow-lite-support:0.4.4`<br>• `androidx.camera:camera-*:1.2.3`<br>• Google Gemini Vision API | On-device YOLOv8 object detection (TFLite), CameraX frame analysis & Flip Camera support, NMS post-processing, Generative Vision AI for scene description. |
| **Face Recognition & Anti-Spoofing** | `FaceRecognitionActivity`<br>`AddFaceActivity`<br>`ManageFacesActivity`<br>`FaceRecognitionHelper`<br>`MLKitFaceDetector`<br>`FaceNet`<br>`FaceSpoofDetector`<br>`ImagesVectorDB`<br>`PersonDB` | • `org.pytorch:executorch-android:1.2.0`<br>• `com.google.mlkit:face-detection:16.1.7`<br>• `io.objectbox:objectbox-android:4.0.0`<br>• `io.objectbox:objectbox-kotlin:4.0.0`<br>• `tensorflow-lite:2.14.0` | On-device PyTorch ExecuTorch FaceNet (512D embeddings), ML Kit Face Detection & Pose Quality Analysis, Dual-scale FASNet Anti-Spoofing (Scale 2.7x & 4.0x), ObjectBox Vector DB HNSW Cosine Search, Auto-capture guided enrollment, Debounced TTS announcements & haptic pulse. |
| **Voice Command** | `VoiceCommandManager`<br>`VoiceIntent`<br>`GeminiCommandProcessor`<br>`TTSManager` | • `android.speech.SpeechRecognizer`<br>• `android.speech.tts.TextToSpeech`<br>• Google Gemini NLU REST API | 3-Layer Voice Architecture (Passive Fuzzy Wake Word, Instant Offline Keyword Matching, Gemini NLU Fallback), Full intent routing (Navigasi, Kamera AI, Kenali Wajah, Live Lokasi), TTS Muting Feedback Prevention. |

---

> **Kesimpulan:**  
> **Orion Navigator** merupakan ekosistem mobile komprehensif yang memadukan teknologi modern **Augmented Reality (ARCore)**, **Computer Vision & On-Device AI (YOLOv8, PyTorch ExecuTorch FaceNet, Dual-Scale FASNet Anti-Spoofing)**, **Embedded Vector Database (ObjectBox HNSW Index)**, **Geospatial Tracking (OSMDroid + Firebase)**, dan **AI Speech/NLU (3-Layer Voice Architecture + Gemini Vision & NLU)** ke dalam satu arsitektur native Android yang aman, berkinerja tinggi, dan ramah tunanetra.
