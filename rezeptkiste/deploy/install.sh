#!/usr/bin/env bash
# Rezeptkiste: Einrichtung bzw. Aktualisierung auf einem frischen Debian- oder Ubuntu-VPS.
#
#   curl -fsSL https://raw.githubusercontent.com/phipswithgrips/ClaudeCode/rezeptkiste/rezeptkiste/deploy/install.sh | bash -s -- rezepte.example.de admin
#
# Mehrfach ausführbar: Bereits Eingerichtetes bleibt erhalten, der Code wird aktualisiert.
set -euo pipefail

step() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FEHLER]\033[0m %s\n' "$*"; exit 1; }

# Alles in main(): bash liest so das ganze Skript ein, bevor es startet.
# Wichtig bei "curl ... | bash", damit kein Befehl den Rest des Skripts von stdin liest.
main() {

DOMAIN="${1:-}"
ADMIN_USER="${2:-admin}"
REPO="https://github.com/phipswithgrips/ClaudeCode.git"
BRANCH="rezeptkiste"
BASE=/opt/rezeptkiste
SRC=$BASE/src
ENV_FILE=$BASE/.env
COMPOSE="docker compose --env-file $ENV_FILE -f $SRC/rezeptkiste/deploy/docker-compose.yml -p rezeptkiste"


[ "$(id -u)" -eq 0 ] || fail "Bitte als root ausführen."
command -v apt-get >/dev/null || fail "Nur Debian/Ubuntu werden unterstützt."
. /etc/os-release
ok "System: $PRETTY_NAME"

step "Pakete aktualisieren und Grundwerkzeuge installieren"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git ufw unattended-upgrades openssl >/dev/null
ok "Pakete installiert"

if ! command -v docker >/dev/null; then
  step "Docker installieren"
  curl -fsSL https://get.docker.com | sh >/dev/null
fi
systemctl enable --now docker >/dev/null 2>&1 || true
ok "$(docker --version)"

step "Code holen ($BRANCH)"
mkdir -p "$BASE" "$BASE/import" "$BASE/backup"
if [ -d "$SRC/.git" ]; then
  git -C "$SRC" fetch -q --depth 1 origin "$BRANCH"
  git -C "$SRC" reset -q --hard FETCH_HEAD
else
  git clone -q --depth 1 --branch "$BRANCH" --filter=blob:none --sparse "$REPO" "$SRC"
  git -C "$SRC" sparse-checkout set rezeptkiste/server rezeptkiste/deploy
fi
ok "Stand $(git -C "$SRC" log -1 --format='%h %s')"

if [ ! -f "$ENV_FILE" ]; then
  step "Konfiguration anlegen"
  if [ -z "$DOMAIN" ]; then
    read -rp "Domain (z. B. rezepte.example.de): " DOMAIN </dev/tty
  fi
  [ -n "$DOMAIN" ] || fail "Keine Domain angegeben."
  while true; do
    read -rsp "Passwort für die App-Anmeldung als '$ADMIN_USER' (mind. 10 Zeichen): " P1 </dev/tty; echo
    read -rsp "Passwort wiederholen: " P2 </dev/tty; echo
    if [ "$P1" != "$P2" ]; then echo "Die Eingaben stimmen nicht überein."; continue; fi
    if [ "${#P1}" -lt 10 ]; then echo "Zu kurz."; continue; fi
    case "$P1" in *"'"*) echo "Bitte ohne Apostroph (')."; continue;; esac
    break
  done
  umask 077
  cat > "$ENV_FILE" <<EOF
RK_DOMAIN=$DOMAIN
RK_ADMIN_USER=$ADMIN_USER
RK_ADMIN_PASSWORD='$P1'
POSTGRES_PASSWORD=$(openssl rand -hex 24)
RK_IMPORT_DIR=$BASE/import
EOF
  unset P1 P2
  ok "Gespeichert in $ENV_FILE (nur für root lesbar)"
else
  ok "Konfiguration vorhanden: $ENV_FILE"
fi
DOMAIN=$(grep '^RK_DOMAIN=' "$ENV_FILE" | cut -d= -f2)

step "Firewall: SSH, HTTP, HTTPS"
SSH_PORT=$(sshd -T 2>/dev/null | awk '/^port /{print $2; exit}'); SSH_PORT=${SSH_PORT:-22}
ufw allow "$SSH_PORT/tcp" >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw allow 443/udp >/dev/null
ufw --force enable >/dev/null
ok "ufw aktiv (SSH-Port $SSH_PORT offen)"

step "Automatische Sicherheitsupdates"
dpkg-reconfigure -f noninteractive unattended-upgrades >/dev/null 2>&1 || true
ok "unattended-upgrades aktiv"

step "Container bauen und starten (dauert beim ersten Mal einige Minuten)"
$COMPOSE up -d --build --remove-orphans

step "Warten, bis die API antwortet"
for i in $(seq 1 60); do
  if $COMPOSE exec -T api python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8000/health')" >/dev/null 2>&1; then
    ok "API läuft"
    break
  fi
  [ "$i" -eq 60 ] && { $COMPOSE logs --tail 50 api; fail "API antwortet nicht."; }
  sleep 3
done

# Das Admin-Passwort wird nur beim Erststart gebraucht; danach aus der Datei entfernen
if grep -q "^RK_ADMIN_PASSWORD='.\+'" "$ENV_FILE"; then
  sed -i "s/^RK_ADMIN_PASSWORD=.*/RK_ADMIN_PASSWORD=''/" "$ENV_FILE"
  ok "App-Passwort aus $ENV_FILE entfernt (liegt nur noch als Hash in der Datenbank)"
fi

step "Nächtliches Backup einrichten"
cat > /usr/local/bin/rezeptkiste-backup <<EOF
#!/usr/bin/env bash
# Datenbank und Fotos sichern; 14 Tage aufbewahren
set -euo pipefail
cd $BASE/backup
stamp=\$(date +%F)
$COMPOSE exec -T db pg_dump -U rezeptkiste -Fc rezeptkiste > db-\$stamp.dump
docker run --rm -v rezeptkiste_files:/files:ro -v $BASE/backup:/out alpine tar czf /out/files-\$stamp.tar.gz -C /files .
find $BASE/backup -type f -mtime +14 -delete
EOF
chmod +x /usr/local/bin/rezeptkiste-backup
echo "17 3 * * * root /usr/local/bin/rezeptkiste-backup >> /var/log/rezeptkiste-backup.log 2>&1" > /etc/cron.d/rezeptkiste-backup
ok "Backup täglich um 03:17 nach $BASE/backup"

cat > /usr/local/bin/rezeptkiste-import <<EOF
#!/usr/bin/env bash
# Recipe-Keeper-Export importieren: rezeptkiste-import <datei.zip> [--commit]
set -euo pipefail
f=\$(readlink -f "\$1"); shift
cp "\$f" $BASE/import/
$COMPOSE exec -T api python -m app.cli import-recipekeeper "/import/\$(basename "\$f")" "\$@"
EOF
chmod +x /usr/local/bin/rezeptkiste-import

cat > /usr/local/bin/rezeptkiste-update <<EOF
#!/usr/bin/env bash
# Code aktualisieren und Container neu bauen
curl -fsSL https://raw.githubusercontent.com/phipswithgrips/ClaudeCode/$BRANCH/rezeptkiste/deploy/install.sh | bash
EOF
chmod +x /usr/local/bin/rezeptkiste-update

step "DNS und HTTPS prüfen"
PUBLIC_IP=$(curl -fsS4 https://api.ipify.org || true)
DNS_IP=$(getent ahostsv4 "$DOMAIN" | awk 'NR==1{print $1}')
if [ -n "$PUBLIC_IP" ] && [ "$DNS_IP" = "$PUBLIC_IP" ]; then
  ok "$DOMAIN zeigt auf diesen Server ($PUBLIC_IP)"
  for i in $(seq 1 20); do
    if curl -fsS "https://$DOMAIN/health" >/dev/null 2>&1; then ok "https://$DOMAIN/health antwortet"; break; fi
    sleep 5
  done
else
  echo "Hinweis: $DOMAIN zeigt noch auf ${DNS_IP:-nichts}, dieser Server hat ${PUBLIC_IP:-?}."
  echo "A-Eintrag setzen; Caddy holt das Zertifikat automatisch, sobald der Eintrag greift."
fi

# Recipe-Keeper-Export im Home-Verzeichnis anbieten
for zip in /root/RecipeKeeper*.zip; do
  [ -f "$zip" ] || continue
  step "Recipe-Keeper-Export gefunden: $zip"
  if [ -z "$(docker exec rezeptkiste-db-1 psql -U rezeptkiste -tAc 'select 1 from recipe limit 1' 2>/dev/null)" ]; then
    /usr/local/bin/rezeptkiste-import "$zip" --commit
  else
    echo "Datenbank enthält bereits Rezepte; Import übersprungen. Manuell: rezeptkiste-import $zip --commit"
  fi
done

printf '\n\033[1;32m==== FERTIG ====\033[0m\n'
echo "Server-Adresse für die Apps: https://$DOMAIN"
echo "Benutzer: $(grep '^RK_ADMIN_USER=' "$ENV_FILE" | cut -d= -f2)"
echo "Befehle: rezeptkiste-update, rezeptkiste-import, rezeptkiste-backup"

}

main "$@" </dev/null
