import { useEffect, useState } from 'react'
import { getFileDetail } from '../api/client'
import type { FileDetail } from '../api/types'

interface FilePreviewModalProps {
  fileId: string
  onClose: () => void
}

// Non-renderable content (docx/xlsx/... and anything else) falls back to the extracted text in
// a <pre>, rendered as plain React text - never dangerouslySetInnerHTML - so a .html file's
// content can't execute as markup even though it's shown verbatim.
export function FilePreviewModal({ fileId, onClose }: FilePreviewModalProps) {
  const [detail, setDetail] = useState<FileDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setDetail(null)
    setError(null)
    getFileDetail(fileId)
      .then(setDetail)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Не удалось загрузить файл'))
  }, [fileId])

  const previewUrl = `/api/files/${fileId}/preview`
  const downloadUrl = `/api/files/${fileId}/download`

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="modal-close" onClick={onClose} aria-label="Закрыть">
          ×
        </button>

        {error && (
          <div className="form-error" role="alert">
            {error}
          </div>
        )}
        {!detail && !error && <div className="search-status">Загрузка...</div>}

        {detail && (
          <>
            <h2 className="preview-title">{detail.fileName}</h2>

            {(detail.title || detail.author || detail.documentCreatedAt) && (
              <dl className="preview-metadata">
                {detail.title && (
                  <>
                    <dt>Заголовок</dt>
                    <dd>{detail.title}</dd>
                  </>
                )}
                {detail.author && (
                  <>
                    <dt>Автор</dt>
                    <dd>{detail.author}</dd>
                  </>
                )}
                {detail.documentCreatedAt && (
                  <>
                    <dt>Создан</dt>
                    <dd>{new Date(detail.documentCreatedAt).toLocaleString('ru-RU')}</dd>
                  </>
                )}
              </dl>
            )}

            <div className="preview-body">
              {detail.contentType.startsWith('image/') ? (
                <img src={previewUrl} alt={detail.fileName} />
              ) : detail.contentType === 'application/pdf' ? (
                <iframe src={previewUrl} title={detail.fileName} />
              ) : (
                <>
                  <pre className="preview-text">{detail.content}</pre>
                  {detail.truncated && <p className="preview-truncated-note">Показана только часть содержимого файла.</p>}
                </>
              )}
            </div>

            <a className="result-download" href={downloadUrl} download={detail.fileName}>
              Скачать
            </a>
          </>
        )}
      </div>
    </div>
  )
}
