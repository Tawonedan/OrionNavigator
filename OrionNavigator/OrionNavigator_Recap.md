# 📱 REKAPITULASI TEKNIS APLIKASI ORION NAVIGATOR

> **Dokumen Spesifikasi & Rekapitulasi Arsitektur Fitur Orion Navigator**  
> *Platform: Android (Kotlin)* | *Target SDK: 34 (Android 14)* | *Desain: Accessibility & Audio-First*

---

## 📖 1. Ikhtisar Aplikasi (App Overview)

**Orion Navigator** adalah ekosistem aplikasi Android pintar yang dirancang khusus untuk memandu dan menjaga keselamatan pengguna tunanetra (*visually impaired users*). Aplikasi ini menggabungkan navigasi spasial indoor berbasis **Augmented Reality (AR)**, pemantauan lokasi jarak jauh (**Live Location**) bagi caregiver/orang tua, serta penglihatan komputer cerdas (**Kamera AI & Computer Vision**) untuk deteksi rintangan real-time.

Aplikasi mendukung **Dual-Role System**:
1. **User Tunanetra (Child):** Antarmuka utama berbasis **Audio-First & Accessible Design**, didukung oleh **Sistem Perintah Suara 3-Layer (Voice Command System)**, gesture sederhana, dan sintesis **Text-to-Speech (TTS)** Bahasa Indonesia.
2. **User Pendamping / Caregiver (Parent):** Hub khusus (`PendampingHomeActivity`) yang menyediakan dua modul utama: **Pemantauan Live Location** (via OpenStreetMap) dan **Pembuatan Peta AR (AR Map Authoring)** untuk memindai ruangan, menempatkan penanda 3D, dan menyambungkan jalur rute navigasi.

---

## 🔵 2. Fitur Utama 1: Live Location (Pelacakan Lokasi Real-Time & Caregiver Hub)

### 👥 Konsep & Tujuan Fitur
Fitur **Live Location** memungkinkan pengguna tunanetra (*Child*) membagikan lokasi geografis mereka secara real-time kepada pendamping/orang tua (*Parent/Caregiver*). Fitur ini berfokus pada **keamanan, privasi, dan persetujuan 1-to-1**, dilengkapi dengan sistem **Geofencing (Safe Zones)** untuk memberi peringatan otomatis saat pengguna keluar dari area aman yang ditentukan.

Caregiver diakses melalui **Pendamping Home Screen (`PendampingHomeActivity.kt`)**, yang memisahkan pemantauan lokasi luar ruangan dan modul pembuatan peta indoor.

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
[Caregiver Hub (PendampingHomeActivity)] ──► 1. Pemantauan Live Location (OSMDroid)
                                           └──► 2. Pembuatan Peta AR (DirectionActivity MAP Mode)
```

### 🛠️ Detail Teknis (Libraries, Dependencies & Concepts)

1. **Hub Utama Pendamping (PendampingHomeActivity.kt)**
   - **Komponen Teknis:** `PendampingHomeActivity.kt`, `RoleSelectionActivity.kt`, `SplashActivity.kt`
   - **Konsep:** Menu navigasi utama untuk peran Pendamping. Alur autentikasi/role mengarahkan Pendamping ke hub ini untuk memilih antara pemantauan peta live (`PendampingActivity`) atau pembuatan/pemetaan rute AR baru (`DirectionActivity` dengan `EXTRA_APP_MODE = "MAP"`).

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

2. **Aliran Video Berkinerja Tinggi (Android CameraX SDK)**
   - **Dependencies:** `androidx.camera:camera-core:1.2.3`, `camera-camera2`, `camera-lifecycle`, `camera-view`
   - **Komponen Teknis:** `CameraManager.kt`
   - **Konsep:** Menggunakan `ImageAnalysis.Analyzer` dari CameraX dengan skema *Frame Skipping* (memproses 1 dari tiap 5 frame) untuk menjaga performa perangkat tetap dingin, mencegah *memory leak*, dan menghindari kelebihan beban CPU/GPU.

3. **Overlay Bounding Box & Translasi Label**
   - **Komponen Teknis:** `DetectionOverlayView.kt` / Custom Canvas Bounding Box
   - **Konsep:** Menggambar ulang hasil deteksi objek secara visual pada *Canvas Overlay* yang disesuaikan dengan skala layar. Label objek berbahasa Inggris diterjemahkan secara otomatis ke Bahasa Indonesia secara real-time.

4. **Kecerdasan Buatan Generatif (Google Gemini Vision API)**
   - **Komponen Teknis:** `GeminiHelper.kt`
   - **Konsep:** Ketika pengguna menekan tombol atau mengucapkan perintah *"Jelaskan"*, frame Bitmap terbaru dikirim ke **Google Gemini REST API** via Kotlin Coroutines. Gemini menganalisis konteks penuh gambar dan menghasilkan narasi pemandangan komprehensif dalam Bahasa Indonesia (misal: *"Di depan Anda terdapat koridor dengan 2 buah kursi di sebelah kiri dan pintu terbuka di ujung jalan"*).

---

## 🎙️ 5. Fitur Suplemen Utama: Sistem Perintah Suara & Asisten Aksesibilitas (Voice Command System)

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
     - Mencocokkan teks ucapan secara offline dengan kamus kata kunci lokal (`matchKeyword()`). Mengeksekusi perintah instan dengan latensi < 50ms untuk fungsi standar seperti:
       - *"Buka Navigasi"* (`VoiceIntent.OPEN_NAVIGATION`)
       - *"Buka Kamera"* (`VoiceIntent.OPEN_CAMERA`)
       - *"Dimana saya"* (`VoiceIntent.CHECK_LOCATION`)
       - *"Jelaskan"* (`VoiceIntent.DESCRIBE_SCENE`)
       - *"Suara Mati / Suara Hidup"* (`VoiceIntent.TOGGLE_SOUND_OFF / ON`)
   - **Layer 3: Gemini NLU Fallback (Natural Language Understanding)**
     - **Komponen Teknis:** `GeminiCommandProcessor.kt`
     - Jika kata yang diucapkan kompleks atau bermakna ganda (misal: *"tolong bimbing saya jalan menuju ke toilet pria"*), input dikirim ke Google Gemini API dengan prompt klasifikasi intent untuk mengekstrak maksud pengguna secara cerdas.

3. **Output Auditori (Text-To-Speech / TTS Manager)**
   - **API Native:** `android.speech.tts.TextToSpeech`
   - **Komponen Teknis:** `TTSManager.kt`
   - **Konsep:** Menyuarakan semua respon, instruksi navigasi, dan hasil deteksi objek menggunakan Bahasa Indonesia (`Locale("id", "ID")`). Menerapkan skema *Debounce & Priority Queue* agar instruksi keselamatan tidak terpotong oleh suara pembacaan objek sekunder.

4. **Mekanisme Pencegahan Feedback Loop (TTS Audio Muting)**
   - **Komponen Teknis:** `pauseForTTS()` dan `resumeAfterTTS()` pada `VoiceCommandManager.kt`
   - **Konsep:** Menerapkan callback `UtteranceProgressListener` pada TTS. Ketika TTS mulai berbicara, pengenalan suara (*SpeechRecognizer*) dihentikan sementara (*pause*) dan suara beep notifikasi di-mute. Setelah TTS selesai berbicara, perekaman suara diaktifkan kembali secara otomatis. Hal ini mencegah pengenalan suara "mendengarkan suaranya sendiri" dari speaker smartphone.

---

## 📊 6. Tabel Rangkuman Arsitektur & Dependensi Utama

| Fitur Utama | Modul / Kelas Utama | Dependensi / Library Utama | Konsep Teknis Utama |
| :--- | :--- | :--- | :--- |
| **Live Location & Caregiver Hub** | `PendampingHomeActivity`<br>`LiveLocationChildActivity`<br>`LiveLocationParentActivity`<br>`LocationUpdateService`<br>`LiveLocationRepository` | • `org.osmdroid:osmdroid-android:6.1.17`<br>• `play-services-location:21.0.1`<br>• `firebase-database-ktx`<br>• `firebase-auth-ktx` | Caregiver Hub (Live Location + Buat Peta AR), OpenStreetMap rendering map-agnostic, 1-to-1 code pairing, persistent Foreground Service, real-time Firebase DB sync, Haversine Geofencing. |
| **3D AR Navigation & Authoring** | `DirectionActivity`<br>`IndoorNavigationManager`<br>`CompassManager`<br>`WaypointRepository`<br>`GraphPersistence.kt`<br>`Dijkstra.kt` | • `com.google.ar:core:1.54.0`<br>• `de.javagl:obj:0.4.0`<br>• Custom OpenGL ES 3.0 Shaders<br>• Android Hardware SensorManager | 6DoF Visual-Inertial Odometry, 100% ARCore (Depresiasi BLE), Penempatan Waypoint via visual Crosshair, Dialog penyambung rute walkable path interaktif, Custom OpenGL 3D path spheres rendering, Dijkstra shortest-path algorithm. |
| **AI Camera** | `CameraAIActivity`<br>`CameraManager`<br>`YoloDetectorHelper`<br>`GeminiHelper` | • `tensorflow-lite:2.14.0`<br>• `tensorflow-lite-support:0.4.4`<br>• `androidx.camera:camera-*:1.2.3`<br>• Google Gemini Vision API | On-device YOLOv8 object detection (TFLite), CameraX frame skipping analysis, NMS post-processing, Generative Vision AI for scene description. |
| **Voice Command** | `VoiceCommandManager`<br>`VoiceIntent`<br>`GeminiCommandProcessor`<br>`TTSManager` | • `android.speech.SpeechRecognizer`<br>• `android.speech.tts.TextToSpeech`<br>• Google Gemini NLU REST API | 3-Layer Voice Architecture (Passive Fuzzy Wake Word, Instant Offline Keyword Matching, Gemini NLU Fallback), TTS Muting Feedback Prevention. |

---

> **Kesimpulan:**  
> **Orion Navigator** menggabungkan teknologi modern **Augmented Reality (ARCore)**, **Computer Vision (YOLOv8 + Gemini Vision)**, **Geospatial Tracking (OSMDroid + Firebase)**, dan **AI Speech/NLU** ke dalam satu arsitektur native Android yang aman, responsif, dan ramah tunanetra.
