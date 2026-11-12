# Libre Radio #

**Libre Radio** is a free (as in freedom), ad-free and tracker-free internet radio player for Android,
Android Auto, Android TV and Android Automotive.

It is a fork of **Open Radio** by Yurii Chernyshov, licensed under Apache 2.0.
The original repository (`ChernyshovYuriy/OpenRadio`) was deleted in 2024 after the Google Play listing
passed to a different publisher, which added ads and paid purchases. This fork starts from the last
author's commit (January 28, 2024), recovered via [Software Heritage](https://archive.softwareheritage.org/browse/origin/directory/?origin_url=https://github.com/ChernyshovYuriy/OpenRadio)
and [jukofyork/OpenRadio](https://github.com/jukofyork/OpenRadio).

Libre Radio is not affiliated with or endorsed by the author of Open Radio or the current publisher
of "Open Radio" on Google Play. It uses its own application ID (`io.github.solomag.libreradio`)
and can be installed alongside Open Radio.

### Changes from Open Radio ###

* Renamed to **Libre Radio** with application ID `io.github.solomag.libreradio`.
* Removed Firebase entirely: Analytics, Crashlytics, Auth and Firestore. No data is sent to Google by the app itself.
* Removed the "Featured" list and Cloud Storage, which depended on the original author's Firebase project (shut down).
  Use **File Storage** (export / import of Favorites) instead.
* Sending logs by email is disabled unless SMTP credentials are supplied at build time.
* Builds without `sign.properties` (debug key for debug builds, unsigned release).

### Two editions ###

Both are built from the same code as Gradle product flavors and share the application ID,
so only one can be installed at a time.

| Edition | APK | Android | UI | Notes |
|---|---|---|---|---|
| **Libre Radio** (`modern`) | `LibreRadio-<version>.apk` | 7.0+ (API 24) | Material 3, dynamic color on Android 12+ | Current libraries (e.g. OkHttp 4) |
| **Libre Radio Legacy** (`legacy`) | `LibreRadio-<version>-legacy.apk` | 4.2+ (API 17) | Classic | Libraries pinned to the last versions that support API 17 |

The TV app is built on `legacy` (API 21+), the Automotive app on `modern`.

### Build ###

```
git clone https://github.com/Solomag/LibreRadio
cd LibreRadio
./gradlew :app:assembleModernDebug    # or :app:assembleLegacyDebug
```

Issues and suggestions: https://github.com/Solomag/LibreRadio/issues

---

# Original Open Radio README #

The text below is the original project's README, kept for reference.


### What is this ? ###

* **Open Radio** is the project which is use media framework of Android to provide live streaming of the world wide Radio Stations. Open Radio supported on Mobile, Android Auto, Android TV and Android Automotive.
* This project is use [Community Radio Browser's API](http://www.radio-browser.info) and [Web Radio](https://jcorporation.github.io/webradiodb) - services that provide a list of radio stations broadcasting their live stream on the Internet.
* Playlist parser is provided by [William Seemann](https://github.com/wseemann/JavaPlaylistParser)
* Playback powered by [Exo Player](https://github.com/google/ExoPlayer)
* Android requirements : Android 4.2 (API level 17) (new APIs for implementing audio playback that is compatible with Auto) or newer.

### Permissions used ###

* INTERNET - To access internet connection.
* ACCESS_NETWORK_STATE - To monitor Internet connection state, detect connect and reconnect states.
* WAKE_LOCK - To keep screen on while playing Radio Station.
* ACCESS_COARSE_LOCATION - On user's demand only - to select Country for user based on Location. This helps to navigate local Radio Stations.
* READ_EXTERNAL_STORAGE (Android 12 and older), READ_MEDIA_IMAGES (Android 13 and newer, on user's demand only) to read image from phone's memory when set it as image for Local Radio Station.
* FOREGROUND_SERVICE - To keep service active while playing stream.
* BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT - On user's demand only - to handle connection with a Bluetooth device.
* RECORD_AUDIO - On user's demand only - to use voice search engine on Android TV.

### Delivery files ###

* [Google Play](https://play.google.com/store/apps/details?id=com.yuriy.openradio) - this application is suitable now for the Android Media Browser simulator as well as for the Android Auto.

**Application is fully compatible with vehicle's system.**

In order to run application just like it does on vehicle it is necessary to install [Android Auto for Mobile](https://play.google.com/store/apps/details?id=com.google.android.projection.gearhead&hl=en).

**Enjoy!**
