# Rezeptkiste - Server

Sync-, Datei- und Import-Server für die Rezeptkiste-Apps (Android, Windows). FastAPI, PostgreSQL 17, Docker Compose.

Stand: Umsetzungsschritte 1 und 2 der Spezifikation (Server-Grundgerüst, Recipe-Keeper-Import).

## Inhalt

- `app/` - Anwendung
    - `api.py` - Endpunkte
    - `sync.py` - Push/Pull, Konfliktregel, Versionshistorie
    - `recipekeeper.py` - Import des Recipe-Keeper-Exports
    - `files.py` - Bildablage nach SHA-256, Vorschaubilder 400 px und 1200 px
    - `models.py` - Datenmodell
- `alembic/` - Datenbankmigrationen
- `tests/` - 23 Tests gegen PostgreSQL

## Auf dem Hetzner-VPS in Betrieb nehmen

1. Ordner auf den VPS kopieren, z. B. nach `/opt/rezeptkiste`.
2. `.env.example` nach `.env` kopieren und zwei starke Passwörter setzen (`POSTGRES_PASSWORD`, `RK_ADMIN_PASSWORD`).
3. Netz von Nginx Proxy Manager ermitteln: `docker network ls`. Den Namen als `PROXY_NETWORK` in `.env` eintragen.
4. Starten: `docker compose up -d --build`. Beim Start laufen die Migrationen, das Benutzerkonto und die sieben Standard-Gänge werden angelegt.
5. In Nginx Proxy Manager einen Proxy Host anlegen:
    - Domain: z. B. `rezepte.example.de`
    - Forward: `http`, Host `api` (Name des Containers bzw. Dienstes), Port `8000`
    - SSL: Let's-Encrypt-Zertifikat, Force SSL
    - Advanced: `client_max_body_size 200m;` (für den Recipe-Keeper-Export und große Fotos)
6. Prüfen: `https://rezepte.example.de/health` liefert `{"status":"ok"}`.

Das Passwort in `.env` gilt nur für den Erststart. Später ändert es sich nicht mehr, wenn die Datei geändert wird.

## Recipe-Keeper-Bestand übernehmen

Bis die Apps fertig sind, geht der Import per curl. Erst ein Geräte-Token holen:

```sh
curl -s -X POST https://rezepte.example.de/auth/device \
  -H 'content-type: application/json' \
  -d '{"username":"admin","password":"...","device_name":"Einrichtung"}'
```

Probelauf (schreibt nichts, liefert den Bericht):

```sh
curl -s -X POST https://rezepte.example.de/import/recipekeeper \
  -H "Authorization: Bearer <token>" -F "file=@RecipeKeeper_Export.zip"
```

Import: dieselbe Anfrage mit `?commit=true`. Ein erneuter Import überspringt bereits übernommene Rezepte.

Kategorien, Gänge und Sammlungen, die sich nur in Groß- und Kleinschreibung oder durch einen Tippfehler unterscheiden (z. B. "dessert" und "Desser"), werden zu einem Eintrag zusammengeführt. Der Probelauf zeigt diese Zusammenführungen vorab.

## Sync-Protokoll (für die Apps)

- Jeder Datensatz hat `id` (UUID, vom Gerät erzeugt), `updated_at`, `deleted` und `data`.
- `updated_at` ist ein Hybrid-Logical-Clock-String: `<Millisekunden, 15 Stellen>-<Zähler, 5 Stellen>-<Knoten>`, z. B. `001759216800000-00003-pixel`. Der Server vergleicht Zeichenketten; jüngere Fassung gewinnt.
- Typen: `course`, `category`, `collection`, `recipe`, `photo`.
- `POST /sync/push` mit bis zu 500 vollständigen Datensätzen. Antwort:
    - `accepted`: übernommen, mit `server_rev`
    - `rejected`: Server-Fassung ist jünger, wird mitgeliefert und ersetzt die lokale
    - `errors`: ungültige Daten, andere Datensätze sind nicht betroffen
- Gleiche Fassung erneut gesendet: gilt als übernommen, keine neue Revision.
- `GET /sync/pull?since=<cursor>&limit=500`: alle Datensätze mit `server_rev > since`, aufsteigend. `cursor` der Antwort speichern, solange `has_more` wahr ist, weiter abrufen.
- Fotos: erst `PUT /files/<sha256>` mit dem Bild, dann den `photo`-Datensatz pushen. `HEAD` prüft, ob der Server die Datei schon hat.
- Jede überschriebene Rezeptfassung landet in der Historie (`GET /recipes/<id>/history`, letzte 20). Wiederherstellen = alte Daten mit neuem `updated_at` pushen.
- Revisionen werden unter einer Zeilensperre vergeben. Dadurch werden sie in Vergabereihenfolge sichtbar, und ein Pull verpasst keinen Datensatz.

Die vollständige API-Beschreibung liegt unter `/docs`.

## Backup

Nächtlich per cron:

```sh
docker compose exec -T db pg_dump -U rezeptkiste -Fc rezeptkiste > /opt/rezeptkiste/backup/db.dump
# plus Volume "files" per restic sichern
```

## Tests

Benötigt eine leere PostgreSQL-Datenbank:

```sh
pip install -r requirements-dev.txt
RK_DATABASE_URL="postgresql+psycopg://user:pw@localhost/rezeptkiste_test" pytest
```

## Noch nicht enthalten

- `/import/url`, `/import/text`, `/import/ocr` (Schritt 6)
- Endgültiges Entfernen gelöschter Datensätze nach 90 Tagen
- Aufräumen verwaister Bilddateien
