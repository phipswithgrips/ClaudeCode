# Cookfolio

Cookfolio ist eine private Rezeptverwaltung für Android und Windows mit Synchronisation über einen eigenen Server. Funktional an Recipe Keeper angelehnt, mit eigenem Namen, eigenem Design und dunklem Farbschema. Nicht für die kommerzielle Nutzung gedacht.

| Ordner | Inhalt |
| --- | --- |
| `server/` | FastAPI, PostgreSQL, Docker Compose: Anmeldung, Sync, Fotos, Recipe-Keeper-Import |
| `app/` | Kotlin Multiplatform mit Compose Multiplatform: eine Codebasis für Android und Windows |

## Builds

GitHub Actions baut bei jedem Push auf den Branch `rezeptkiste` (Workflow `.github/workflows/rezeptkiste.yml`):

- Server-Tests gegen PostgreSQL 17, Migrationsprüfung und Docker-Image
- Tests der App (Sync-Engine gegen einen simulierten Server) und Android-APK
- Windows-Installer (MSI)

APK und MSI liegen danach unter **Actions** beim jeweiligen Lauf als Artefakte zum Download.

## Stand

- [x] Schritt 1: Server-Grundgerüst
- [x] Schritt 2: Recipe-Keeper-Import
- [x] Schritt 3: App-Grundgerüst mit Anmeldung, Vollabgleich, Rezeptliste und Detailansicht
- [x] Schritt 4: Rezepte bearbeiten, Fotos, Filter, Sortierung
- [x] Schritt 5: Portionsumrechnung, Kochmodus, Teilen als Text
- [ ] Schritt 6: Web- und Scan-Import (Text- und Recipe-Keeper-Import sind fertig)
- [ ] Schritt 7: Signierte APK, Backup, Monitoring

## Offen

- Rezepteingabe neu gestalten: funktioniert im Test auf Android und Windows nicht und soll klüger, einfacher und schöner werden (Rückmeldung vom 01.10.2026, wird später aufgegriffen)

## App installieren

- **Android:** `composeApp-debug.apk` aus dem Artefakt `cookfolio-android-apk` auf das Gerät laden und öffnen. Einmalig die Installation aus dieser Quelle erlauben.
- **Windows:** `Cookfolio-1.0.<Build>.msi` aus dem Artefakt `cookfolio-windows-msi` ausführen. Die Installation erfolgt für den aktuellen Benutzer und benötigt keine Administratorrechte. Der Installer wird bewusst nicht signiert. SmartScreen warnt deshalb mit "Unbekannter Herausgeber": "Weitere Informationen", dann "Trotzdem ausführen".

Beim ersten Start fragt die App nach Server-Adresse, Benutzername, Passwort und Gerätename.
