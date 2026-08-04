export interface HighlightFragment {
  text: string
  matched: boolean
}

export interface SearchHit {
  id: string
  path: string
  fileName: string
  extension: string
  sizeBytes: number
  modifiedAt: string
  highlights: HighlightFragment[][]
  downloadUrl: string
}

export interface SearchResponse {
  total: number
  page: number
  size: number
  results: SearchHit[]
}

export interface SearchFilters {
  extensions: string[]
  path: string
  from: string
  to: string
}

export type IndexRootStatus = 'IDLE' | 'SCANNING' | 'WATCHING' | 'ERROR'

export interface IndexRoot {
  id: string
  path: string
  status: IndexRootStatus
  totalFiles: number
  processedFiles: number
  docCount: number
  lastError: string | null
  createdAt: string
}

export interface ApiError {
  message: string
}

export interface FileDetail {
  id: string
  path: string
  fileName: string
  extension: string
  contentType: string
  sizeBytes: number
  modifiedAt: string
  author: string | null
  title: string | null
  documentCreatedAt: string | null
  content: string
  truncated: boolean
}
