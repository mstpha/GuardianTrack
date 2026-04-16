# GuardianTrack 🛡️

GuardianTrack is a proactive personal safety and fall detection application built with modern Android development practices. It monitors user movement in real-time to detect falls, manage battery critical events, and provide manual emergency triggers.

## 🚀 Features

- **Fall Detection:** Uses an accelerometer-based algorithm to detect free-fall and impact patterns.
- **Battery Critical Alerts:** Automatically logs and notifies when the device battery reaches a critical level (15%).
- **Manual Panic Button:** Quick-access trigger for immediate emergency alerts.
- **Offline-First Storage:** Uses Room database to ensure no incident data is lost, even without internet.
- **Cloud Sync:** Background synchronization with **Supabase** via WorkManager for centralized monitoring.
- **SMS Alerts:** Notifies emergency contacts automatically during high-risk incidents.
- **Map Integration:** Quick links to view incident locations directly in Google Maps.
- **Export Data:** Export your incident history to CSV for external analysis.

## 🛠️ Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **Dependency Injection:** Hilt
- **Local DB:** Room
- **Background Tasks:** WorkManager
- **Networking:** Retrofit + Ktor (for Supabase)
- **Concurrency:** Kotlin Coroutines & Flow
- **Location Services:** Google Play Services Location

## 🏗️ Project Structure

- `data/local`: Room entities, DAOs, and database configuration.
- `data/remote`: Retrofit API definitions and DTOs for Supabase integration.
- `repository`: The single source of truth managing data flow between local and remote.
- `ui`: Compose screens, ViewModels, and state management.
- `worker`: Background jobs for synchronization and system events.
- `service`: Foreground surveillance service for real-time sensor monitoring.

## 🔧 Setup & Installation

1. **Clone the repository.**
2. **Add Configuration (API Security):** 
   Create a `local.properties` file in the root directory. This file contains sensitive API keys and **must not be versioned** (it is excluded in `.gitignore`). Add your Supabase credentials and API base URL:
   ```properties
   SUPABASE_URL="your_supabase_url"
   SUPABASE_ANON_KEY="your_anon_key"
   api.base.url="https://your-api-endpoint.com/"
   ```
3. **Build & Run:** Open the project in Android Studio (Koala or newer) and run the `app` module.

## 📜 Permissions

GuardianTrack requires the following permissions to function effectively:
- `ACCESS_FINE_LOCATION`: To tag incidents with precise coordinates.
- `SEND_SMS`: To alert your emergency contacts.
- `RECEIVE_BOOT_COMPLETED`: To restart monitoring automatically after a device reboot.
- `POST_NOTIFICATIONS`: To provide immediate feedback and alarm controls.

---
*Built with ❤️ for safety.*
