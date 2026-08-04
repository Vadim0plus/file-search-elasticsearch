import { describe, expect, it } from 'vitest'
import { getFileIcon } from './fileIcon'

describe('getFileIcon', () => {
  it('maps known extensions to a distinct icon', () => {
    expect(getFileIcon('pdf')).toBe('📕')
    expect(getFileIcon('docx')).toBe('📘')
    expect(getFileIcon('xlsx')).toBe('📗')
    expect(getFileIcon('png')).toBe('🖼')
  })

  it('is case-insensitive', () => {
    expect(getFileIcon('PDF')).toBe(getFileIcon('pdf'))
  })

  it('falls back to a generic icon for unknown extensions', () => {
    expect(getFileIcon('xyz')).toBe('📁')
    expect(getFileIcon('')).toBe('📁')
  })
})
