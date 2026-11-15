# witch-social

Social-Media-Bausteine auf Basis von [witch-base](https://github.com/FrankS68/witch-base):
Konten verbinden, einen Beitrag gleichzeitig auf mehreren Netzwerken veröffentlichen
(sofort oder geplant) und die Feeds aller Konten an einer Stelle lesen.

Jede App wählt selbst, **welche Netzwerke** sie kann – einfach über Maven-Abhängigkeiten.

## Module

```
witch-social-parent/   Parent-POM fuer Apps: alles aus witch-parent + witch-social-core,
                       Versionen aller Netzwerk-Module (Alternative zu witch-parent)
witch-social-core/     Kern (Paket de.witchcafe.social): Konten, Beitraege, Scheduler, Bilder,
                       Token-Verschluesselung, Views, SPI fuer Netzwerk-Module
witch-bluesky/         Bluesky
witch-mastodon/        Mastodon
witch-meta/            Facebook-Seiten + Instagram (eine Meta-App, ein OAuth-Flow)
witch-pinterest/       Pinterest
app/                   Die Social-Media-Zentrale "witch-social" mit allen Netzwerken
```

`./mvnw install` im Wurzelverzeichnis baut alles und installiert Parent, Core und Netzwerk-Module
ins lokale Maven-Repository (vorher witch-base installieren, wegen `witch-parent`).

| Modul             | Verbinden                         | Posten                       | Feed                    |
|-------------------|-----------------------------------|------------------------------|-------------------------|
| `witch-meta`      | OAuth über Meta-App               | Facebook nur auf **Seiten**, Text/Link/Bild; Instagram nur mit **JPEG-Bild**, Business-/Creator-Konto | Beiträge der Seite / eigene Beiträge |
| `witch-pinterest` | OAuth über Pinterest-App          | Pin mit Bild auf gewähltes Board | eigene Pins         |
| `witch-bluesky`   | Handle + **App-Passwort**         | Text (300 Zeichen), Bild ≤ 976 KB, Links klickbar | Timeline |
| `witch-mastodon`  | OAuth, App registriert sich pro Instanz selbst | Text (500 Zeichen), Bild | Home-Timeline |

## Eine App auf witch-social-parent aufsetzen

Statt `witch-parent` als Parent `witch-social-parent` nehmen und die gewünschten Netzwerke
eintragen (ohne Version):

```xml
<parent>
    <groupId>de.witchcafe</groupId>
    <artifactId>witch-social-parent</artifactId>
    <version>1.0.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>de.witchcafe</groupId>
        <artifactId>witch-ui</artifactId>
    </dependency>
    <!-- nur die Netzwerke, die die App koennen soll -->
    <dependency>
        <groupId>de.witchcafe</groupId>
        <artifactId>witch-bluesky</artifactId>
    </dependency>
    <dependency>
        <groupId>de.witchcafe</groupId>
        <artifactId>witch-mastodon</artifactId>
    </dependency>
</dependencies>
```

Plugins und Repository wie in witch-base beschrieben, Beispiel: [`app/pom.xml`](app/pom.xml).
Die App muss `de.witchcafe` scannen (`@ComponentScan`, `@EnableVaadin`, `@EntityScan`,
`@EnableJpaRepositories`, `vaadin.allowed-packages`) – wie die Vorlage aus witch-base.
Dann sind die Views Feeds (`/feeds`, zugleich Startseite `/`), Neuer Beitrag (`/beitrag`),
Beiträge (`/beitraege`) und Konten (`/konten`) da; die Kontenübersicht bietet genau die
eingebundenen Netzwerke an.

Braucht die App `/` für ihre eigene Startseite, `social.feeds-start-page=false` setzen;
die Feeds bleiben unter `/feeds` (Beispiel: das Quiz).

Wird ein Netzwerk-Modul später entfernt, bleiben dessen Konten in der Datenbank; sie werden als
„Netzwerk-Modul nicht eingebunden“ angezeigt und lassen sich trennen.

## Ein neues Netzwerk-Modul schreiben

Ein Modul `witch-xyz` hängt von `witch-social-core` ab, liegt im Paket `de.witchcafe.social.xyz`
und stellt Spring-Beans bereit:

- **`SocialNetwork`** (`de.witchcafe.social.client`): `id()` (stabil, wird bei jedem Konto
  gespeichert), Anzeigename, Farbe, Textlänge, `validate`, `publish`, `feed`.
  Hilfen: `Json`, `Multipart`, `Urls`, `SocialApiException`.
- **`AccountConnector`** (`de.witchcafe.social.ui`): „verbinden“-Button für die Kontenübersicht,
  optional Details und Aktionen je Konto.
- Bei OAuth einen `@Controller` mit `/connect/xyz` und `/connect/xyz/callback`; `ConnectSupport`
  liefert state-Prüfung, Redirect-URLs und Rücksprung zur Kontenübersicht. `/connect/**`
  erfordert automatisch eine Anmeldung.
- Konten speichern mit `AccountService.upsertMine(...)`; Felder von `ConnectedAccount`
  (Token, Handle, Instanz, ...) sind allgemein gehalten.
- Eigene Einstellungen als `@ConfigurationProperties("social.xyz")` plus `@Configuration`
  mit `@EnableConfigurationProperties`.

Vorbild: `witch-bluesky` (ohne OAuth) bzw. `witch-mastodon` (mit OAuth und eigener Entity).

## Die App witch-social einrichten

1. `app/application-local.properties.example` nach `app/application-local.properties` kopieren.
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
5. Auf dem Server `social.public-base-url` (bzw. `PUBLIC_BASE_URL`) auf die öffentliche Adresse
   setzen. Lokal leer lassen, dann gilt die Adresse aus dem Browser. Instagram holt Bilder selbst
   von dort ab, lokal mit `localhost` funktioniert Instagram-Posten deshalb nicht (z.B. mit
   einem Tunnel wie ngrok testen).

Geplante Beiträge veröffentlicht ein Scheduler alle 30 Sekunden (`social.scheduler-delay`).
Daten liegen in `app/data` (H2-Datei, nicht eingecheckt).

## Starten

```bash
cd app && ../mvnw
```

Docker-Image (aus dem Wurzelverzeichnis): `docker build .`

## Lizenz

[Apache License 2.0](LICENSE) – Copyright 2026 Frank Segieth.
