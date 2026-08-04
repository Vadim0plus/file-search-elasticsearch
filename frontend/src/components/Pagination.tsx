interface PaginationProps {
  page: number
  size: number
  total: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, size, total, onPageChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  if (totalPages <= 1) {
    return null
  }

  return (
    <div className="pagination">
      <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        Назад
      </button>
      <span>
        Страница {page + 1} из {totalPages}
      </span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        Вперёд
      </button>
    </div>
  )
}
