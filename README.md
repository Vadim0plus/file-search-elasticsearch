# Поиск файлов на диске (Spring Boot + React + Elasticsearch)

Приложение индексирует файлы на диске (текст, PDF, DOCX, XLSX и др.) и даёт быстрый полнотекстовый поиск по содержимому с подсветкой совпадений, поиском «по мере ввода» и скачиванием найденного файла. Индекс остаётся актуальным в реальном времени: изменения файлов на диске подхватываются автоматически, без ручной переиндексации.

Полная спецификация и обоснование архитектурных решений — в [SPEC.md](SPEC.md).

## Возможности

- Рекурсивная индексация одной или нескольких директорий
- Извлечение текста из документов (PDF, DOCX, XLSX, PPTX, HTML и др.) через Apache Tika
- Живое отслеживание файловой системы (`WatchService`) — добавление/изменение/удаление файла обновляет индекс автоматически
- Полнотекстовый поиск с fuzzy-матчингом, фильтрами по расширению/пути/дате и подсветкой совпадений
- Поиск «по мере ввода» на фронтенде (debounce, без кнопки «Найти»)
- Скачивание найденного файла по ссылке из результатов
- Управление отслеживаемыми директориями (добавление/переиндексация/удаление) с живым статусом и прогрессом сканирования
- Интерфейс на русском языке

## Стек

| Слой | Технологии |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web, Data Elasticsearch, Actuator, Validation), Gradle (Kotlin DSL), Apache Tika, Lombok |
| Поиск | Elasticsearch 9.x (через официальный `elasticsearch-java` клиент — прямые запросы с multi_match, fuzziness, highlighting) |
| Frontend | React 19, TypeScript, Vite, обычный CSS (без UI-кита) |
| Тесты | JUnit 5, Testcontainers (реальный Elasticsearch), MockMvc — backend; Vitest, React Testing Library, msw — frontend |
| Инфраструктура | Docker Compose (Elasticsearch + backend + nginx-фронтенд) |

## Быстрый старт (Docker Compose)

Нужен установленный Docker.

```bash
docker compose up --build
```

Поднимутся три контейнера: `elasticsearch`, `backend` (порт `7007`) и `frontend` (порт `7006`, nginx с проксированием `/api` и `/actuator` на backend). По умолчанию в контейнер backend монтируется директория `./sample-data` (несколько демонстрационных файлов на русском) как `/data:ro`.

Открыть приложение: **http://localhost:7006**

Проиндексировать другую директорию с хоста:

```bash
INDEX_ROOT=/путь/к/вашим/файлам docker compose up --build
```

Зарегистрировать директорию для индексации можно либо через вкладку «Управление индексом» в UI, либо через API:

```bash
curl -X POST localhost:7007/api/roots \
  -H 'content-type: application/json' \
  -d '{"path":"/data"}'

curl 'localhost:7007/api/search?q=Elasticsearch'
```

Остановить и удалить контейнеры (данные Elasticsearch останутся в volume):

```bash
docker compose down
```

Добавить `-v`, чтобы удалить и данные индекса.

## API

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/roots` | Добавить директорию для отслеживания (`{"path": "/data"}`) |
| `GET` | `/api/roots` | Список отслеживаемых директорий со статусом/прогрессом |
| `GET` | `/api/roots/{id}` | Статус одной директории |
| `POST` | `/api/roots/{id}/reindex` | Полная переиндексация директории |
| `DELETE` | `/api/roots/{id}` | Прекратить отслеживание и удалить документы из индекса |
| `GET` | `/api/search?q=&extension=&path=&from=&to=&page=&size=` | Полнотекстовый поиск с подсветкой |
| `GET` | `/api/files/{id}/download` | Скачать оригинальный файл по id найденного документа |
| `GET` | `/actuator/health` | Доступность приложения и Elasticsearch |

## Локальная разработка (без Docker)

Понадобится отдельно запущенный Elasticsearch 9.x на `localhost:9200`.

### Backend

```bash
cd backend
./gradlew bootRun
```

Backend поднимется на `localhost:8080`. Переменные окружения: `SPRING_ELASTICSEARCH_URIS`, `APP_CORS_ALLOWED_ORIGINS` (см. `application.yml`).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite-сервер (`localhost:5173`) проксирует `/api/*` на `localhost:8080` (см. `vite.config.ts`).

## Тесты

```bash
# backend: юнит, @WebMvcTest и полный e2e-тест на Testcontainers с реальным Elasticsearch
cd backend && ./gradlew test

# frontend: Vitest + React Testing Library + msw
cd frontend && npm test
```

## Структура проекта

```
.
├── SPEC.md                 # подробная спецификация и архитектура
├── docker-compose.yml
├── backend/                 # Spring Boot API (индексация, поиск, скачивание)
├── frontend/                 # React SPA (поиск, управление индексом)
└── sample-data/              # демонстрационные файлы для docker compose up
```

## Безопасность

- Подсветка совпадений передаётся с бэкенда не как сырой HTML, а как список текстовых фрагментов (`{text, matched}`) — фронтенд рендерит их как обычный React-текст, оборачивая совпадения в `<mark>`. Даже если содержимое файла — валидный HTML/JS, оно не будет интерпретировано как разметка.
- Скачивание файла адресуется по непрозрачному id документа (хэш пути), а не по сырому пути — это исключает path traversal за пределы проиндексированных файлов.
