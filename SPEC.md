# File Indexer & Search — Spec

Приложение индексирует файлы на диске (текст + документы PDF/DOCX/XLSX/...) в Elasticsearch и даёт быстрый полнотекстовый поиск по содержимому с подсветкой совпадений и скачиванием оригинального файла. Бэкенд — Spring Boot (Java 21, Gradle), фронтенд — React + TypeScript (Vite), поисковый движок — Elasticsearch. Всё разворачивается через Docker Compose.

## 1. Цели и требования

- Рекурсивно индексировать содержимое одной или нескольких директорий на диске.
- Извлекать текст из документов (PDF, DOCX, XLSX, PPTX, HTML, TXT, MD, код и т.д.) через Apache Tika.
- Поддерживать актуальность индекса в реальном времени: добавление/изменение/удаление файла в отслеживаемой директории обновляет индекс без ручного переиндексирования.
- Полнотекстовый поиск по содержимому и имени файла с fuzzy-матчингом, фильтрами (расширение, путь, дата изменения) и подсветкой найденных фрагментов.
- Поиск в реальном времени по мере ввода (live search, debounced) на фронтенде.
- Скачивание найденного файла по ссылке из результатов поиска.
- Управление списком отслеживаемых директорий (добавление/удаление/принудительная переиндексация) со статусом прогресса.
- Автоматические тесты на бэкенде и фронтенде.

## 2. Архитектура

```
┌─────────────┐      HTTP/JSON      ┌──────────────────┐      ES REST API      ┌────────────────┐
│   React SPA │ ───────────────────▶│  Spring Boot API  │ ─────────────────────▶│  Elasticsearch  │
│  (Vite, TS) │◀─────────────────── │  (Java 21/Gradle)  │◀───────────────────── │   (single node) │
└─────────────┘   search/index/dl   └──────────┬────────┘                       └────────────────┘
                                                │
                                        Files.walk / WatchService
                                                │
                                                ▼
                                     Индексируемая директория(и)
                                        на диске (или в volume
                                        контейнера backend)
```

Всё поднимается через `docker compose up`: контейнер `elasticsearch`, контейнер `backend` (со смонтированной директорией с файлами для индексации), контейнер `frontend` (nginx, отдаёт статику и проксирует `/api/*` на backend).

## 3. Модель данных (Elasticsearch)

Индекс `files`, `_id` = SHA-256 от абсолютного пути файла (детерминированный ключ — переиндексация того же файла обновляет тот же документ, а не создаёт дубликат).

| Поле | Тип ES | Назначение |
|---|---|---|
| `path` | `keyword` | абсолютный путь, уникальный ключ |
| `fileName` | `text` (+ `keyword` subfield) | поиск и точная фильтрация по имени |
| `extension` | `keyword` | фильтр по типу файла |
| `directory` | `keyword` | родительская директория — префиксные фильтры, удаление по root |
| `content` | `text`, standard analyzer | извлечённый текст, основное поле поиска |
| `contentType` | `keyword` | MIME-тип, определённый Tika |
| `sizeBytes` | `long` | размер файла |
| `modifiedAt` | `date` | mtime файла |
| `indexedAt` | `date` | время последней индексации |
| `rootId` | `keyword` | id отслеживаемого root'а, к которому относится файл |

## 4. Backend — Spring Boot

### 4.1 Индексация

1. `POST /api/roots {path}` — регистрирует root: проверяет, что путь существует и читаем, создаёт `rootId`, сохраняет в `IndexRootStore` (in-memory), асинхронно запускает полное сканирование, затем передаёт root в `FileWatchService` для живого отслеживания.
2. Полное сканирование: `Files.walk(root)`, пропускаются служебные директории (`.git`, `node_modules`, `target`, `build`, `dist`, `.idea`) и файлы больше `app.indexing.max-file-size-mb`; текст извлекается через Tika `AutoDetectParser`; ошибка парсинга одного файла логируется и не прерывает сканирование; документы отправляются в ES бакетами по `app.indexing.batch-size` через bulk API.
3. Статус каждого root'а (`IDLE|SCANNING|WATCHING|ERROR`, `totalFiles`, `processedFiles`, `docCount`, `lastError`) доступен через `GET /api/roots`/`GET /api/roots/{id}` — фронтенд опрашивает его во время сканирования.
4. `FileWatchService`: рекурсивная регистрация `WatchService` на root и все поддиректории (новые поддиректории регистрируются динамически при `ENTRY_CREATE`); события дебаунсятся по пути (~500мс) перед реиндексацией/удалением документа — это гасит двойные события от редакторов, которые пишут во временный файл и переименовывают его.
5. `DELETE /api/roots/{id}` — останавливает watcher, массово удаляет документы с этим `rootId`.
6. `POST /api/roots/{id}/reindex` — полное пересканирование root'а.

### 4.2 Поиск

`GET /api/search?q=&extension=&path=&from=&to=&page=&size=`

- `multi_match` по `fileName^3` и `content`, `fuzziness=AUTO`.
- Фильтры: `extension` (terms), `path` (префикс по `directory`), диапазон `modifiedAt`.
- Подсветка (`highlight`) по `content` не отдаётся клиенту как сырой HTML — ES оборачивает совпадения в `<em>`, но НЕ экранирует остальной текст фрагмента, поэтому файл с содержимым `<script>` мог бы инъецировать HTML в браузер. Вместо этого бэкенд разбивает фрагмент по маркерам подсветки и возвращает `List<HighlightFragment>{text, matched}` — фронтенд рендерит каждый фрагмент как экранированный текст, оборачивая совпавшие в `<mark>`, без `dangerouslySetInnerHTML`.
- Ответ: `total`, `page`, `results[]` (`id`, `path`, `fileName`, `extension`, `sizeBytes`, `modifiedAt`, `highlights[]`, `downloadUrl`).
- Дебаунс запроса — на фронтенде (~250-300мс на каждое нажатие клавиши), сам эндпоинт достаточно дешёвый.

### 4.3 Скачивание файла

`GET /api/files/{id}/download` — по `id` находит документ в ES, проверяет, что файл всё ещё существует на диске (иначе 404), стримит его с `Content-Disposition: attachment` и сохранённым `contentType`. Используется непрозрачный `id` (хэш), а не путь в query-параметре — это исключает path traversal: нет способа передать `../../etc/passwd`, потому что ключ поиска — хэш, а не строка пути, и резолвятся только файлы, реально попавшие в индекс.

### 4.4 Конфигурация (`application.yml`)

```yaml
spring.elasticsearch.uris: http://elasticsearch:9200
app.indexing.max-file-size-mb: 50
app.indexing.batch-size: 200
app.indexing.watch-debounce-ms: 500
app.indexing.excluded-dirs: [.git, node_modules, target, build, dist, .idea]
```

CORS открыт для origin фронтенда. `spring-boot-starter-actuator` → `/actuator/health` используется фронтендом как индикатор доступности ES.

### 4.5 Зависимости

`spring-boot-starter-webmvc`, `spring-boot-starter-data-elasticsearch`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, `org.apache.tika:tika-core` + `tika-parsers-standard-package`, `lombok` (Spring Boot 4.1 / Java 21 / Gradle Kotlin DSL).

## 5. Frontend — React + TypeScript (Vite)

- **Вкладка «Поиск»**: `SearchBar` реагирует на каждое нажатие клавиши, дебаунс ~250-300мс (`useDebouncedValue`); `useLiveSearch` шлёт `/api/search` по дебаунсированному значению, игнорируя устаревшие ответы, если пришёл более новый запрос. Рядом — `Filters` (чекбоксы расширений, префикс пути, диапазон дат). Результаты — список `ResultItem`: имя файла, путь, размер, дата изменения, подсвеченный фрагмент (рендерится из списка экранированных фрагментов, `matched` — в `<mark>`), ссылка «Скачать» → `/api/files/{id}/download`. Плюс `Pagination`.
- **Вкладка «Индексация»**: `IndexManager` — добавление root'а (путь внутри контейнера backend, например `/data/...`), таблица root'ов с живым статусом/прогрессом (поллинг раз в ~2с во время `SCANNING`), кнопки «Переиндексировать»/«Удалить».
- Индикатор доступности ES в шапке на основе `/actuator/health`.
- Стек: Vite + React 18 + TypeScript, обычный CSS (без тяжёлого UI-кита).

## 6. Docker Compose

- `elasticsearch` — single-node 9.x, `xpack.security.enabled=false` (упрощение для dev), named volume для данных, healthcheck.
- `backend` — сборка из `backend/Dockerfile` (multi-stage Gradle → JRE 21), `depends_on: elasticsearch (healthy)`, монтирует `${INDEX_ROOT:-./sample-data}:/data:ro` — пользователь может указать любую директорию хоста через переменную окружения, `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200`, публикуется на хост-порт `${BACKEND_PORT:-7007}`.
- `frontend` — сборка из `frontend/Dockerfile` (multi-stage Vite build → nginx), nginx проксирует `/api/*` и `/actuator/*` на `backend:8080`, публикуется на хост-порт `${FRONTEND_PORT:-7006}`.
- `sample-data/` — несколько демонстрационных файлов (txt/md/csv), чтобы `docker compose up` сразу давало что индексировать.

## 7. Тестирование

**Backend (JUnit 5):**
- Юнит-тесты: `TextExtractionService` (извлечение текста из фикстур .txt/.pdf/.docx; корректная обработка «битого» файла без исключения), `SearchService` (построение запроса/фильтров, разбиение фрагментов подсветки — включая XSS-кейс с `<script>` в содержимом).
- `@WebMvcTest` для контроллеров с замоканными сервисами — ошибки валидации, 404 при скачивании удалённого файла.
- Интеграционный тест на Testcontainers (реальный Elasticsearch): регистрация root'а на временную директорию с фикстурами → сканирование → проверка `/api/search` → проверка, что watcher подхватывает добавление/изменение/удаление файла после первого скана (через bounded poll, не через `Thread.sleep`) → проверка `/api/files/{id}/download`.

**Frontend (Vitest + React Testing Library):**
- `useDebouncedValue`/`useLiveSearch` — фейковые таймеры, устаревший ответ не перезаписывает актуальный.
- `SearchBar` — ввод текста триггерит дебаунсированный колбэк.
- `ResultItem` — фрагменты с `<script>`-подобным текстом рендерятся как инертный текст, а не разметка; ссылка скачивания указывает на правильный URL.
- `IndexManager` — добавление root'а вызывает API и отображает статус; поллинг обновляет прогресс.
- API-клиент — тесты через `msw` (мок сети без реального сервера).

## 8. Порядок реализации

1. Спека (этот документ).
2. Backend: каркас Gradle-проекта, конфигурация, модель, репозиторий.
3. Backend: сервисы индексации/наблюдения/поиска + контроллеры (включая скачивание).
4. Backend: тесты.
5. Frontend: каркас Vite+TS, API-клиент, live-search хук, компоненты.
6. Frontend: тесты.
7. Docker Compose, Dockerfile'ы, sample-data.
8. Сквозная проверка (см. ниже).

## 9. Проверка

- `./gradlew test` (backend, включая Testcontainers) и `npm test` (frontend) — зелёные.
- `docker compose up --build`, дождаться healthy у `elasticsearch` и `backend`.
- `curl -X POST localhost:7007/api/roots -H 'content-type: application/json' -d '{"path":"/data"}'` → статус `SCANNING → WATCHING`.
- `curl 'localhost:7007/api/search?q=quarterly'` → результаты с подсветкой и `downloadUrl`; `curl -OJ localhost:7007/api/files/{id}/download` отдаёт файл.
- Изменение/добавление файла в смонтированной директории → `GET /api/roots` показывает обновлённый `docCount` без ручной переиндексации.
- Открыть `http://localhost:7006` — в UI: ввод в поиск обновляет результаты по мере набора текста, подсветка рендерится безопасно (без сырого HTML), ссылка «Скачать» работает, управление root'ами в реальном времени.
