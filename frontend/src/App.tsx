import { useState } from 'react'
import './App.css'
import type { SearchFilters } from './api/types'
import { FilePreviewModal } from './components/FilePreviewModal'
import { Filters } from './components/Filters'
import { IndexManager } from './components/IndexManager'
import { LoginPage } from './components/LoginPage'
import { Pagination } from './components/Pagination'
import { SearchBar } from './components/SearchBar'
import { SearchResults } from './components/SearchResults'
import { useAuth } from './hooks/useAuth'
import { useDebouncedValue } from './hooks/useDebouncedValue'
import { useHealth } from './hooks/useHealth'
import { useLiveSearch } from './hooks/useLiveSearch'

const EMPTY_FILTERS: SearchFilters = { extensions: [], path: '', from: '', to: '' }
const SEARCH_PAGE_SIZE = 20
// Landing on the page with no query yet is "browsing", not searching - a shorter page keeps
// the first thing a new user sees to a quick top-10 glance rather than a full paginated list.
const BROWSE_PAGE_SIZE = 10
const DEBOUNCE_MS = 280

type Tab = 'search' | 'index'

// Only mounted once authenticated, so its useLiveSearch effect fires for the first time
// post-login - if it lived in App directly, the hook would fire (and 401) on the very first
// render while still logged out, and that stale error would never clear since nothing about
// the query/filters/page changes once the user actually logs in.
function AuthenticatedApp({ username, healthy, onLogout }: { username: string; healthy: boolean; onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('search')
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState<SearchFilters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [previewFileId, setPreviewFileId] = useState<string | null>(null)
  const debouncedQuery = useDebouncedValue(query, DEBOUNCE_MS)
  const hasQuery = debouncedQuery.trim().length > 0
  const pageSize = hasQuery ? SEARCH_PAGE_SIZE : BROWSE_PAGE_SIZE

  const { data, loading, error } = useLiveSearch(debouncedQuery, filters, page, pageSize)

  const handleQueryChange = (value: string) => {
    setQuery(value)
    setPage(0)
  }

  const handleFiltersChange = (next: SearchFilters) => {
    setFilters(next)
    setPage(0)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Поиск файлов</h1>
        <nav className="tabs">
          <button type="button" className={tab === 'search' ? 'active' : ''} onClick={() => setTab('search')}>
            Поиск
          </button>
          <button type="button" className={tab === 'index' ? 'active' : ''} onClick={() => setTab('index')}>
            Управление индексом
          </button>
        </nav>
        <span className={`health-badge ${healthy ? 'healthy' : 'unhealthy'}`}>
          {healthy ? 'Elasticsearch доступен' : 'Elasticsearch недоступен'}
        </span>
        <a className="api-docs-link" href="/swagger-ui/index.html" target="_blank" rel="noopener noreferrer">
          API docs
        </a>
        <span className="current-user">{username}</span>
        <button type="button" className="logout-button" onClick={onLogout}>
          Выйти
        </button>
      </header>

      <main>
        {tab === 'search' ? (
          <section className="search-section">
            <SearchBar value={query} onChange={handleQueryChange} />
            <Filters filters={filters} onChange={handleFiltersChange} />
            <SearchResults
              results={data?.results ?? []}
              total={data?.total ?? 0}
              loading={loading}
              error={error}
              hasQuery={hasQuery}
              onPreview={setPreviewFileId}
            />
            {data && <Pagination page={data.page} size={data.size} total={data.total} onPageChange={setPage} />}
          </section>
        ) : (
          <IndexManager />
        )}
      </main>

      {previewFileId && <FilePreviewModal fileId={previewFileId} onClose={() => setPreviewFileId(null)} />}
    </div>
  )
}

function App() {
  const { username, loading: authLoading, logout } = useAuth()
  const healthy = useHealth()

  if (authLoading) {
    return null
  }
  if (!username) {
    return <LoginPage />
  }

  return <AuthenticatedApp username={username} healthy={healthy} onLogout={() => logout()} />
}

export default App
