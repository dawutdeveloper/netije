# Development guide

## Android setup

Open the repository root in Android Studio and let Gradle sync. The checked-in configuration uses SDK 37, Gradle 9.6.0, and a JDK 25 daemon toolchain, while Java and Kotlin compile to JVM 17. Use an Android Studio installation compatible with the Android Gradle Plugin version in `gradle/libs.versions.toml`.

Android Studio creates the ignored `local.properties` file with your SDK path. Optional build settings can be added to that file:

```properties
BASE_URL=https://your-api.example.com/api/
GOOGLE_SERVER_CLIENT_ID=your-google-oauth-web-client-id
```

The URL must point to your own server. The equivalent environment variables are `NETIJE_BASE_URL` and `NETIJE_GOOGLE_CLIENT_ID`. Values in `local.properties` take precedence. The OAuth client ID must match the backend's `GOOGLE_CLIENT_ID`; Android OAuth configuration also needs the app package and signing certificate fingerprint. Debug builds use `com.example.netije.debug`; release builds use `com.example.netije`.

Without configuration, the base URL defaults to `http://10.0.2.2:3000/api/`, the Android emulator's host address. The current main manifest does not permit cleartext traffic. Use an HTTPS endpoint for backend integration; setting `ALLOW_HTTP_CLEARTEXT` alone does not change manifest policy. A physical device also needs an endpoint reachable from that device.

## Backend setup

Use a Node.js environment compatible with NestJS 10 and the native `bcrypt` dependency, plus a running PostgreSQL database.

From `backend/`, run:

```powershell
npm install
Copy-Item .env.example .env
```

Create the PostgreSQL database named in `.env`, then configure its connection values. Set a private `JWT_SECRET` and configure Google OAuth before testing sign-in. The example values are intended for local development.

```powershell
npm run start:dev
```

The API listens on port `3000` by default, under `/api`. Modules cover authentication, people, transactions, synchronization, and reports.

The current TypeORM configuration uses `synchronize: true`. Use a disposable development database; deployment to an existing or production database requires a deliberate migration strategy. Review the server's CORS and TLS settings before deployment.

## App name and compatibility

The app is named **Gazna**. Kotlin source packages and the Android namespace use `com.gazna` (for example, `com.gazna.domain.model`). The layer structure and dependency boundaries are unchanged. There is no requirement to use `android.com.gazna`; package names should describe the app's own namespace.

The application ID remains `com.example.netije` (`com.example.netije.debug` for debug builds) so the rename preserves installed app identity, app data, FileProvider authorities, and existing OAuth registrations. Changing this ID would create a separate Android app and require updated OAuth configuration. Existing installations still need the same signing key when updating.

The old launcher component is retained as an activity alias. A small worker class at its original package name keeps previously scheduled WorkManager backups runnable. Database filenames, preference keys, backup folders (`Netije/backups`), and attachment paths intentionally retain their original names to preserve existing data. Newly exported backup filenames and report branding use Gazna. Room schema history lives under `app/schemas/com.gazna.data.local.db.NetijeDatabase/`; the schema contents and database version are unchanged.

The GitHub repository URL and existing `NETIJE_*` environment variables also remain valid. A branding change does not require renaming the repository or server database.

After pulling the package rename into an existing checkout, run `.\gradlew.bat :app:clean` once before building to remove generated Hilt classes that reference the old source packages.

## Validation

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires a device or emulator. Test sources live in `app/src/test/` and `app/src/androidTest/`.

From `backend/`:

```powershell
npm test
```

This builds the TypeScript source and runs the Node.js behavior tests in `backend/test/`.

## Release signing

Release signing reads these optional values from the ignored `local.properties` file:

```properties
SIGNING_STORE_FILE=absolute/path/to/your-release-keystore
SIGNING_STORE_PASSWORD=your-store-password
SIGNING_KEY_ALIAS=your-key-alias
SIGNING_KEY_PASSWORD=your-key-password
```

Use your own key. Keep signing material and credentials outside version control. The `/keystore/` directory is ignored, including files without a keystore extension. The Gradle `storeFile` path resolves relative to the `app/` module when it is not absolute.

## Screenshots

See the [screenshot guide](screenshots/README.md) for the asset layout and capture checklist.
