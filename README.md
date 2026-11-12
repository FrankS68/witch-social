# witch-social

Social-Media-Zentrale auf Basis von [witch-base](https://github.com/FrankS68/witch-base):
Konten verbinden, einen Beitrag gleichzeitig auf mehreren Netzwerken veröffentlichen
(sofort oder geplant) und die Feeds aller Konten an einer Stelle lesen.

| Netzwerk  | Verbinden                         | Posten                       | Feed                    |
|-----------|-----------------------------------|------------------------------|-------------------------|
| Facebook  | OAuth über Meta-App               | nur auf **Seiten**, Text/Link/Bild | Beiträge der Seite |
| Instagram | über dieselbe Meta-App            | nur mit **JPEG-Bild**, Business-/Creator-Konto | eigene Beiträge |
| Pinterest | OAuth über Pinterest-App          | Pin mit Bild auf gewähltes Board | eigene Pins         |
| Bluesky   | Handle + **App-Passwort**         | Text (300 Zeichen), Bild ≤ 976 KB, Links klickbar | Timeline |
| Mastodon  | OAuth, App registriert sich pro Instanz selbst | Text (500 Zeichen), Bild | Home-Timeline |

## Oberfläche

- **Feeds** (`/`) – zusammengeführte, zeitlich sortierte Feeds aller Konten
- **Neuer Beitrag** (`/beitrag`) – Text, Link, Bild, Zielkonten, optional Zeitpunkt; prüft live die Grenzen der Netzwerke
- **Beiträge** (`/beitraege`) – Status je Konto mit Links, erneut senden, Planung abbrechen
- **Konten** (`/konten`) – Netzwerke verbinden/trennen, Pinterest-Board wählen
- Admin: Benutzer und OAuth-Provider (aus witch-base)

Geplante Beiträge veröffentlicht ein Scheduler alle 30 Sekunden (`social.scheduler-delay`).

## Struktur

```
src/main/java/de/witchcafe/app/social
├── AccountService, PostService, FeedService   Anwendungslogik
├── MediaController                            liefert Bilder unter /images/social/{uuid} aus
├── connect/ConnectController                  OAuth-Flows /connect/{meta|pinterest|mastodon}
├── client/                                    ein NetworkClient pro Netzwerk (RestClient + Jackson)
├── domain/                                    Entities, Repositories, Token-Verschlüsselung
└── ui/                                        Vaadin-Views
```

## Einrichten

1. `application-local.properties.example` nach `application-local.properties` kopieren.
2. Login-Provider für die App selbst eintragen (wie in witch-base).
3. `social.crypto-key` setzen (`openssl rand -base64 32`). Damit werden Zugangstoken in der
   Datenbank verschlüsselt. Später nicht mehr ändern.
4. Netzwerke nach Bedarf:
   - **Meta** (Facebook + Instagram): App unter <https://developers.facebook.com/apps/> anlegen,
     Produkt „Facebook Login for Business“, Redirect-URI `<public-base-url>/connect/meta/callback`.
     Benötigte Berechtigungen: `pages_show_list`, `pages_read_engagement`, `pages_manage_posts`,
     `business_management`, `instagram_basic`, `instagram_content_publish`. Für andere Nutzer als
     die App-Entwickler ist ein App-Review bei Meta nötig.
   - **Pinterest**: App unter <https://developers.pinterest.com/apps/>, Redirect-URI
     `<public-base-url>/connect/pinterest/callback`. Mit Trial-Zugang nur gegen die Sandbox
     (`social.pinterest.api-base-url=https://api-sandbox.pinterest.com`).
   - **Mastodon** und **Bluesky**: nichts einzurichten.
5. `social.public-base-url` auf die öffentliche Adresse setzen. Instagram holt Bilder selbst
   von dort ab, lokal mit `localhost` funktioniert Instagram-Posten deshalb nicht (z.B. mit
   einem Tunnel wie ngrok testen).

Daten liegen in `./data` (H2-Datei, nicht eingecheckt).

## Starten

```bash
./mvnw
```

## Lizenz

[Apache License 2.0](LICENSE) – Copyright 2026 Frank Segieth.
