import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { SearchBar } from './SearchBar'

function ControlledSearchBar({ onChangeSpy }: { onChangeSpy: (value: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <SearchBar
      value={value}
      onChange={(next) => {
        onChangeSpy(next)
        setValue(next)
      }}
    />
  )
}

describe('SearchBar', () => {
  it('fires onChange for every keystroke as the user types', async () => {
    const user = userEvent.setup()
    const onChangeSpy = vi.fn()
    render(<ControlledSearchBar onChangeSpy={onChangeSpy} />)

    const input = screen.getByRole('searchbox', { name: /поиск файлов/i })
    await user.type(input, 'ab')

    expect(onChangeSpy).toHaveBeenNthCalledWith(1, 'a')
    expect(onChangeSpy).toHaveBeenNthCalledWith(2, 'ab')
    expect(input).toHaveValue('ab')
  })
})
