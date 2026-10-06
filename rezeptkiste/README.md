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
- [x] Einkaufsliste mit Sync, Abschnittstitel werden ausgelassen
- [x] Zeitangaben im Rezept als Timer (Android: Uhr-App, Windows: eingebauter Timer)
- [x] Rezepteingabe mit Freitext und Live-Vorschau, Zwischenüberschriften
- [ ] Schritt 6: Web- und Scan-Import (Text- und Recipe-Keeper-Import sind fertig)
- [ ] Schritt 7: Signierte APK, Backup, Monitoring

## Server aktualisieren

Neue Funktionen brauchen manchmal eine neue Server-Version (zuletzt: Einkaufsliste). Auf dem VPS:

```
rezeptkiste-update
```

Bis dahin bleibt die Einkaufsliste auf dem jeweiligen Gerät; Rezepte synchronisieren weiter.

## Bildschirmfotos

Jeder Build legt Bildschirmfotos (Handy, Tablet, Windows) im Zweig `cookfolio-screens` ab.

## App installieren

- **Android:** `composeApp-debug.apk` aus dem Artefakt `cookfolio-android-apk` auf das Gerät laden und öffnen. Einmalig die Installation aus dieser Quelle erlauben. Ab Version 1.0.22 ist die APK immer mit demselben Schlüssel signiert und ersetzt die vorige ohne Deinstallation (einmalig vorher deinstallieren, wenn eine ältere Version installiert ist).
- **Windows:** `Cookfolio-1.0.<Build>.msi` aus dem Artefakt `cookfolio-windows-msi` ausführen. Die Installation erfolgt für den aktuellen Benutzer und benötigt keine Administratorrechte. Der Installer wird bewusst nicht signiert. SmartScreen warnt deshalb mit "Unbekannter Herausgeber": "Weitere Informationen", dann "Trotzdem ausführen".

Beim ersten Start fragt die App nach Server-Adresse, Benutzername, Passwort und Gerätename.
