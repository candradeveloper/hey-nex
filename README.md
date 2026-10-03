# Hey Nex

Hey Nex adalah asisten suara pribadi untuk Android yang selalu mendengarkan perintah suara.

## Fitur Utama

- **Wake word**: kata bangun "Hey Nex"
- **Mode hibrid**: offline (SpeechRecognizer + TTS) dan online (Groq API)
- **Perintah suara**:
  - Buka aplikasi
  - Telepon kontak
  - Kirim WhatsApp
  - Atur alarm dan timer
  - Baca notifikasi
  - Kontrol WiFi, Bluetooth, volume, kecerahan
  - Putar/jeda/lanjut musik
  - Cari web
  - Tanya jawab umum via Groq
  - Baca acara kalender

## Persyaratan

- Android 8.0 (API 26) atau lebih tinggi
- Kunci API Groq (opsional untuk pertanyaan umum)

## Izin

Aplikasi meminta izin berikut saat runtime:

- `RECORD_AUDIO`
- `READ_CONTACTS`
- `CALL_PHONE`
- `SEND_SMS`
- `READ_CALENDAR`
- `READ_NOTIFICATIONS` (melalui Notification Listener)
- Overlay permission

## Setup

1. Clone repositori.
2. Buka di Android Studio.
3. Masukkan kunci API Groq di Pengaturan.
4. Build dan jalankan.

## Build dengan Gradle

```bash
./gradlew assembleDebug
```

## CI/CD

GitHub Actions otomatis membangun APK debug setiap push ke `main` dan mengunggahnya sebagai artifact.

## Catatan Keamanan

Kunci API Groq disimpan menggunakan `EncryptedSharedPreferences`.
