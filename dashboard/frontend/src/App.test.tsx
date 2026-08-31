import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AssessmentTime, StatusChip } from './App'

describe('StatusChip', () => {
  it.each(['PASS', 'WARNING', 'BLOCKED', 'UNKNOWN', 'NOT_REQUIRED'])('renders %s as text', state => {
    render(<StatusChip state={state} />)
    expect(screen.getByText(state.replace('_', ' '))).toBeVisible()
  })
})

describe('AssessmentTime', () => {
  it('renders the operator-published assessment timestamp', () => {
    const { container } = render(<AssessmentTime value="2026-08-31T10:45:12.345Z" />)

    const time = container.querySelector('time')
    expect(time).toHaveAttribute('datetime', '2026-08-31T10:45:12.345Z')
    expect(time).not.toHaveTextContent('Not published')
  })

  it('shows the fallback only when the operator has not published a timestamp', () => {
    render(<AssessmentTime value={null} />)
    expect(screen.getByText('Not published')).toBeVisible()
  })
})
