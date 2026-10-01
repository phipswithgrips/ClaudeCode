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
- [ ] Schritt 4: Rezepte bearbeiten, Fotos, Filter, Sortierung
- [ ] Schritt 5: Portionsumrechnung, Kochmodus, Teilen als Text
- [ ] Schritt 6: Web-, Text- und Scan-Import
- [ ] Schritt 7: Signierte APK, Backup, Monitoring

## Für die nächste Version vorgemerkt

- **Windows-Installer signieren.** Beim Ausführen von `Cookfolio-1.0.13.msi` meldet SmartScreen "Der Computer wurde durch Windows geschützt" mit "Herausgeber: Unbekannter Herausgeber". MSI und `Cookfolio.exe` sollen deshalb im CI-Lauf mit `signtool` signiert werden. Möglichkeiten:
  - eigenes Zertifikat, einmalig auf dem eigenen PC als vertrauenswürdig importiert (kostenlos; die Warnung verschwindet nur auf diesem PC)
  - Azure Trusted Signing (ca. 10 $ im Monat; der Herausgeber wird angezeigt, und SmartScreen-Vertrauen baut sich auf)
  - gekauftes Code-Signing-Zertifikat (OV/EV, ca. 200–400 $ im Jahr)

## App installieren

- **Android:** `composeApp-debug.apk` aus dem Artefakt `cookfolio-android-apk` auf das Gerät laden und öffnen. Einmalig die Installation aus dieser Quelle erlauben.
- **Windows:** `Cookfolio-1.0.<Build>.msi` aus dem Artefakt `cookfolio-windows-msi` ausführen. Die Installation erfolgt für den aktuellen Benutzer und benötigt keine Administratorrechte. Da der Installer nicht signiert ist, zeigt SmartScreen beim ersten Start eine Warnung: "Weitere Informationen", dann "Trotzdem ausführen".

Beim ersten Start fragt die App nach Server-Adresse, Benutzername, Passwort und Gerätename.
