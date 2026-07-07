# Extensions Komik

Repository ini berisi perbaikan untuk ekstensi sumber manga/scrap bahasa Indonesia yang dibuat oleh
[Keiyoushi](https://github.com/keiyoushi/extensions-source) dan
[Yūzōnō](https://github.com/yuzono/tachiyomi-extensions).

Ekstensi tersedia untuk [Mihon](https://github.com/mihonapp/mihon),
[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi), dan fork lainnya.

## Cara Pakai

### Build APK (Android Studio)

1. Clone repo ini
2. Buka project di **Android Studio**
3. Tunggu Gradle sync selesai
4. Di terminal jalankan:
   ```
   ./gradlew :src:id:mikoroku:assembleDebug
   ```
5. APK hasil build ada di:
   ```
   src/id/mikoroku/build/outputs/apk/debug/
   ```

### Install di Mihon/Tachiyomi

1. Buka Mihon → **Browse** → **Extensions**
2. Tap ikon **+** (atau menu titik tiga → **Extension repos**)
3. Tambah URL repo ini:
   ```
   https://raw.githubusercontent.com/Albar19/Extensions-komik/repo/index.min.json
   ```
   *(belum tersedia — build & deploy manual dulu lewat APK)*

Atau langsung install file `.apk` dari hasil build di atas.

## License

    Copyright 2015 Javier Tomás

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Disclaimer

This project does not have any affiliation with the content providers available.

This project is not affiliated with Komikku/Mihon/Tachiyomi.
All credits to the codebase goes to the original contributors.

The developer of this application does not have any affiliation with the content providers available.
