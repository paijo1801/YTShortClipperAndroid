# YT Short Clipper Android 2.0.7 — AI On-Device

Port Android dari YT Short Clipper. Mesin desktop Windows (.exe/Python/FFmpeg) diganti dengan komponen Android.

## Fitur
- YouTube download melalui yt-dlp Android (embedded Python)
- Import video lokal
- Whisper on-device untuk transkripsi bahasa Indonesia + timestamp
- AI-assisted highlight scoring dari transcript
- ML Kit face detection/tracking untuk menentukan titik crop portrait
- FFmpeg Android untuk trim, crop 9:16, scale 1080x1920, AAC/H.264, dan burn-in SRT
- Export ke Movies/YT Short Clipper

## Dependensi utama
- `dev.ffmpegkit-maintained:yt-dlp-android-compat:2.0.2`
- `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`
- `dev.ffmpegkit-maintained:whisper-android:1.0.0`
- `com.google.mlkit:face-detection:16.1.7`
- AndroidX Media3 1.11.1

## Model Whisper — Full APK
Varian Full menempatkan `app/src/main/assets/whisper/ggml-base.bin` langsung di APK/AAB. Saat pertama kali dipakai, aplikasi menyalin model ke storage internal aplikasi dan memverifikasi ukurannya. Model `base` resmi whisper.cpp berukuran sekitar 142 MiB. Untuk Google Play, ukuran besar sebaiknya dikirim sebagai Play Asset Delivery; App Bundle mendukung asset pack hingga 1.5 GB per pack.

Jika file model belum ada di ZIP ini, salin file resmi `ggml-base.bin` ke `app/src/main/assets/whisper/` sebelum build. SHA-1 resmi model base: `465707469ff3a37a2b9b8d8f89f2f99de7299dac`.

## Alur
`URL YouTube -> yt-dlp -> video -> Face Tracking + Whisper -> Highlight scoring -> crop 9:16 -> subtitle -> MP4`

Catatan: hasil highlight adalah AI-assisted on-device scoring berbasis transcript; bukan klaim bahwa model generatif cloud dipakai. Untuk analisis semantic yang lebih berat, backend AI dapat ditambahkan kemudian.


## Android Studio build

1. Open this folder as an Android Studio project.
2. Use JDK 17 and Android SDK 35.
3. Ensure Android NDK 27c/CMake are installed if your local setup requires native dependencies.
4. Put the official `ggml-base.bin` (SHA-1 `465707469ff3a37a2b9b8d8f89f2f99de7299dac`) at `app/src/main/assets/whisper/ggml-base.bin`. The maintained Whisper Android AAR supports loading directly from an asset.
5. Sync Gradle, select `app`, then Build > Build APK(s).

The project targets arm64-v8a. Whisper Android documents API 24+ and compile/target SDK 35 for its free AAR.
