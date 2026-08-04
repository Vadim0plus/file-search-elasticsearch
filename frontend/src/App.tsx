import { useState } from 'react'
import './App.css'
import type { SearchFilters } from './api/types'
import { Filters } from './components/Filters'
import { IndexManager } from './components/IndexManager'
import { Pagination } from './components/Pagination'
import { SearchBar } from './components/SearchBar'
import { SearchResults } from './components/SearchResults'
import { useDebouncedValue } from './hooks/useDebouncedValue'
import { useHealth } from './hooks/useHealth'
import { useLiveSearch } from './hooks/useLiveSearch'

const EMPTY_FILTERS: SearchFilters = { extensions: [], path: '', from: '', to: '' }
const PAGE_SIZE = 20
const DEBOUNCE_MS = 280

type Tab = 'search' | 'index'

function App() {
  const [tab, setTab] = useState<Tab>('search')
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState<SearchFilters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const debouncedQuery = useDebouncedValue(query, DEBOUNCE_MS)
  const healthy = useHealth()

  const { data, loading, error } = useLiveSearch(debouncedQuery, filters, page, PAGE_SIZE)

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
        <h1>File Search</h1>
        <nav className="tabs">
          <button type="button" className={tab === 'search' ? 'active' : ''} onClick={() => setTab('search')}>
            Search
          </button>
          <button type="button" className={tab === 'index' ? 'active' : ''} onClick={() => setTab('index')}>
            Index Manager
          </button>
        </nav>
        <span className={`health-badge ${healthy ? 'healthy' : 'unhealthy'}`}>
          {healthy ? 'Elasticsearch OK' : 'Elasticsearch unavailable'}
        </span>
      </header>

      <main>
        {tab === 'search' ? (
          <section className="search-section">
            <SearchBar value={query} onChange={handleQueryChange} />
            <Filters filters={filters} onChange={handleFiltersChange} />
            <SearchResults
              results={data?.results ?? []}
              loading={loading}
              error={error}
              hasQuery={debouncedQuery.trim().length > 0}
            />
            {data && <Pagination page={data.page} size={data.size} total={data.total} onPageChange={setPage} />}
          </section>
        ) : (
          <IndexManager />
        )}
      </main>
    </div>
  )
}

export default App
