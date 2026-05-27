# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Legado (阅读T)** — enhanced fork of [gedoor/legado](https://github.com/gedoor/legado), an open-source Android novel reader. The app is a rule-driven web scraping engine: users define "book sources" (书源) with CSS/XPath/JSONPath/Regex/JS rules that tell the app how to scrape novel content from websites. This fork adds cron-driven automation, an enhanced JS runtime, HTTP debugging, callbacks, and editor improvements.

## Build & Dev Commands

```bash
# Build debug APK
./gradlew assembleAppDebug

# Build release APK
./gradlew assembleAppRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Web frontend dev (requires app running as backend)
cd modules/web && pnpm dev
```

The app uses a Gradle version catalog at `gradle/libs.versions.toml`. Versions: AGP 9.1.1, Kotlin 2.3.0, KSP 2.3.4, Room 2.7.1, targetSdk 36, minSdk 26. Package name: `io.legado.app.releaseT`.

## Project Structure

```
app/                          # Main Android app (Kotlin)
  src/main/java/io/legado/app/
    App.kt                    # Application class — init Rhino, Cronet, notifications, crash handler
    api/                      # Content Provider API (ReaderProvider)
    base/                     # Base Activity/Fragment/ViewModel classes
    constant/                 # App constants, preference keys
    data/                     # Room DB, DAOs, entities
      entities/rule/          # Sub-entities for book source rules (SearchRule, ContentRule, etc.)
    exception/                # Custom exception types
    help/                     # Utility & infrastructure classes
      http/                   # OkHttp client, Cronet, HTTP logging (HttpLogger)
      rhino/                  # Rhino JS integration: NativeBaseSource, bindings
      source/                 # Source import/export utilities
      storage/                # Backup/restore, WebDAV sync
      book/                   # Book/content caching logic
      config/                 # App preferences wrappers
      coroutine/              # Coroutine helpers
    lib/                      # Reusable library components (theme, permissions, dialogs, WebDAV client)
    model/                    # Business logic layer
      analyzeRule/            # Core rule parsing engine (AnalyzeRule + JSoup/XPath/JSONPath/Regex analyzers)
      webBook/                # Web novel content fetching
      localBook/              # Local file parsing (TXT, EPUB, UMD, PDF, MOBI)
      remote/                 # Remote book sync (WebDAV)
      AutoTaskRule.kt         # Cron task rule model
      HttpLogger.kt / HttpRecord.kt  # HTTP request/response logging
      SourceCallBack.kt       # Book source callback event system
    receiver/                 # Broadcast receivers
    service/                  # Android services (download, TTS, AutoTask, WebService)
    ui/                       # Activities & fragments, organized by feature
      autoTask/               # Cron task editor & management UI
      book/                   # Reading interface
      browser/                # WebView browser for showBrowser callback
    utils/                    # General utility functions
    web/                      # Embedded HTTP server (NanoHTTPd) + WebSocket for remote API/debugging
modules/
  book/                       # EPUB/UMD parsing library (Java, me.ag2s package)
  rhino/                      # JS engine: htmlunit-core-js fork wrapper (com.script package)
  web/                        # Vue 3 web frontend (bookshelf + source editor)
```

## Architecture: Rule Parsing Engine

The heart of the app is `AnalyzeRule` (`model/analyzeRule/AnalyzeRule.kt`). It takes HTML/JSON content plus a rule string and returns extracted data. Rules are chained DSL strings composed of multiple "source rules" separated by implicit boundaries:

- **`@XPath:` prefix** or `/` → XPath mode
- **`@Json:` prefix** or `$.`/`$[` → JSONPath mode
- **`@CSS:` prefix** or `@@` → JSoup CSS-selector mode (default)
- **`<js>...</js>` tag** → embedded JavaScript
- **`##regex##replacement[##]` suffix** → regex replace on result
- **`@put:{...}` prefix** → save extracted values as variables
- **`{{...}}` inline JS**, **`@get:{key}`**, **`$N` backrefs** → dynamic rule composition

Source rules cascade: the output of one becomes the input of the next. This enables extraction pipelines like "select elements → filter with JS → extract attributes → regex clean".

## Architecture: JS Runtime

The Rhino module wraps `org.htmlunit:htmlunit-core-js` (a Rhino fork). Key classes:

- `RhinoScriptEngine` — script compilation & scope creation (runtime bindings for `java`, `cookie`, `cache`, `source`, `book`, `result`, `baseUrl`, etc.)
- `RhinoWrapFactory` — converts Java objects exposed to JS; uses `NativeBaseSource` for mutable book source objects and `ReadOnlyJavaObject` for rules/config
- `CryptoJS` is available in the shared scope (`SharedJsScope.getCryptoScope()`)
- `JsExtensions` interface provides `ajax()`, `http.get/post/head()`, `showBrowser()`, `copyText()`, `ocr()`, `log()` methods callable from JS

## Architecture: Data Layer

Room database (`AppDatabase`) with DAO per entity. Key entities:

- **BookSource** — scraping rules (search, book info, content, etc.) with JSON-embedded rule fields
- **Book** / **BookChapter** — bookshelf items and their chapter lists
- **RssSource** / **RssArticle** — RSS subscription sources and articles
- **AutoTaskRule** — cron-driven automation tasks (Room-stored, not preferences)
- **ReplaceRule** / **TxtTocRule** / **DictRule** / **HttpTTS** — various customization rules

## Architecture: Web Service & API

`WebService` starts an embedded NanoHTTPd server on port configurable in settings. Provides:
- REST API for CRUD on book sources, RSS sources, books, replace rules (`api.md`)
- WebSocket endpoints for book search, source debugging
- Web frontend served from `modules/web/` (Vue 3 SPA at `/` for bookshelf, `/#/bookSource` for source editor)

## Key Conventions

- **Companion object extension functions**: Properties like `AnalyzeRule.setCoroutineContext()` are defined as extension functions inside the class companion object, not as member functions.
- **JDK 17 target**, core library desugaring enabled (for `java.nio`, etc.)
- **ViewBinding** enabled (no Jetpack Compose — the UI is traditional View-based with Kotlin synthetics)
- **Preference keys** are string constants in `constant/PreferKey` or `constant/AppConst`
- The `bookSourceUrl` is the primary identity key for book sources — it's treated as unique across the app
- **Backup format** is JSON arrays of entity objects, compatible with the original Legado
