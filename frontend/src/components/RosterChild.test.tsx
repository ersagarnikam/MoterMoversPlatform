import { isValidElement, type ReactElement, type ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { RosterEntry } from '../api'
import { rosterChild } from './RosterChild'

const child = (name: string, initials: string): RosterEntry => ({
  id: 1,
  name,
  initials,
  yearGroup: 'Year 2',
  currentStage: 'YELLOW',
})

/** The visible texts of the avatar and the label. */
function texts(content: ReactNode): string[] {
  const fragment = content as ReactElement<{ children: ReactElement<{ children: string }>[] }>
  return fragment.props.children.map((span) => {
    expect(isValidElement(span)).toBe(true)
    return span.props.children
  })
}

describe('rosterChild', () => {
  it('shows initials and never a full name when the school uses initials only', () => {
    // With full names off, the API sends the initials in place of the name.
    const { props, content } = rosterChild(child('A.B.', 'A.B.'), 'screen')
    expect(props.title).toBeUndefined()
    expect(props['aria-label']).toBe('A.B., screen')
    expect(texts(content)).toEqual(['AB', 'A.B.'])
  })

  it('offers the full name on hover and to screen readers when the school shows names', () => {
    const { props, content } = rosterChild(child('Zara Zed', 'Z.Z.'), 'open readiness profile')
    expect(props.title).toBe('Zara Zed')
    expect(props['aria-label']).toBe('Zara Zed, open readiness profile')
    // The full name is never shown on the button itself.
    expect(texts(content)).toEqual(['ZZ', 'Z.Z.'])
  })

  it('keeps at most three letters in the avatar', () => {
    expect(texts(rosterChild(child('A.B.C.D.', 'A.B.C.D.'), 'x').content)[0]).toBe('ABC')
  })

  it('replaces the label when one is given, e.g. Absent', () => {
    expect(texts(rosterChild(child('A.B.', 'A.B.'), 'absent today', 'Absent').content)).toEqual(['AB', 'Absent'])
  })
})
