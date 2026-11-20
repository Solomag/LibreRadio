# Libre Radio — состояние

Форк Open Radio для Android. Репозиторий Solomag/LibreRadio, ветка master.
Текущая версия: **15.14.1** (versionCode 791×10+flavor), опубликована 2026-11-20.

## Сборки
- 4 APK: phone (modern, Compose), legacy (Android 4.2+), TV, Automotive. applicationId у всех `io.github.solomag.libreradio`.
- Подпись: сертификат SHA-256 начинается с `17a9f78b`.

## Выпуск версии X
1. Коммит с изменениями, затем коммит «Release X»: `versionName` в `constants.gradle`, `VERSION_CODE` в `version.properties` (+1).
2. Сборка:
   `./gradlew --no-daemon -q -Dorg.gradle.jvmargs=-Xmx1200m -Pkotlin.compiler.execution.strategy=in-process :app:assembleModernRelease :app:assembleLegacyRelease :tv:assembleRelease :automotive:assembleRelease`
   (сбой на merge dex обычно лечится повтором). Удалить `hs_err_pid*.log`, `replay_pid*.log`.
3. Проверка: `aapt dump badging`, `apksigner verify --print-certs` (build-tools в `E:/Android_Studio/build-tools/36.0.0`).
4. Заметки: прошлые заметки + новые разделы «Изменения» / «Changes» (таблица файлов, ссылка на Open Radio, отпечаток сертификата).
5. `git push`, `gh release create vX --target master --title "Libre Radio X" --notes-file … <4 apk>`; проверить HEAD = origin = тег, 4 файла, Latest, CI (`gh run watch`) зелёный.

## Где что
- Сеть (прокси, встроенный ByeDPI, белое имя сервера): общий диалог `common-ui/.../view/dialog/NetworkDialog.kt` + `dialog_network.xml`, строки в common-ui. Используется в legacy, TV, Automotive; в modern — `SettingsScreen.kt`.
- Проверка станций («не подключается» / «обрывается»): `common/.../model/net/StationCheck.kt`, метки `check_not_working`, `check_cut_off` в `app/src/main/res`.
- Название трека: `OpenRadioPlayer.onMetadata`, разбор тегов iHeart — `common/.../utils/StreamTitle.kt` (+ тест).

## Тестирование
- Телефон «Палас» (бронь через `E:\Devices\phones.py`), эмуляторы LibreTV (android-28 TV) и LibreCar (android-34 automotive), запуск без окна.
- Скриншот с машины: `screencap -p /sdcard/s.png` + pull.

## Открытые вопросы пользователю
- Локальные коммиты со STATE.md не отправлены на GitHub (push только по прямой просьбе). Спросить, пушить ли.
- Задач в работе нет.

## Известное
- Станции, которые играют с домашнего интернета, но не с мобильного (например, 181.fm), — это сеть оператора режет зарубежные серверы; помогают прокси / ByeDPI / белое имя сервера.
