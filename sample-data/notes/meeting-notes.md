# Weekly Sync — Search Platform

## Attendees
Backend, frontend and infra folks.

## Discussion

We reviewed the new file indexing service. Apache Tika now extracts text from
PDF, DOCX and XLSX attachments in addition to plain text and markdown files.
The Spring Boot backend watches tracked directories with `WatchService` so
edits show up in search results within a second, without a manual reindex.

## Action items

- Document the download endpoint for the frontend team.
- Load test the bulk indexing path with a directory of 100k files.
