import { describe, expect, it } from 'vitest'
import { sessionTime, startsIn, ZOOM_SDK } from './live'
import { gbp } from './money'
import { CONTACT_EMAIL, mailto, signInRole, SIGN_IN_ROLES } from './site'
import { pct, stageInfo, STAGES } from './stages'

describe('gbp', () => {
  it('shows whole pounds without pence', () => {
    expect(gbp(18_000)).toBe('£180')
    expect(gbp(4_500)).toBe('£45')
    expect(gbp(0)).toBe('£0')
  })

  it('shows pence only when there are some', () => {
    expect(gbp(148_750)).toBe('£1,487.50')
    expect(gbp(1_505)).toBe('£15.05')
    expect(gbp(99)).toBe('£0.99')
  })

  it('groups thousands', () => {
    expect(gbp(1_000_000)).toBe('£10,000')
  })
})

describe('sessionTime', () => {
  const tuesdayEvening = '2026-11-03T19:00:00Z'

  it('shows the time in the zone asked for, with its name', () => {
    expect(sessionTime(tuesdayEvening, 'Europe/London')).toBe('Tue 3 Nov, 19:00 GMT')
  })

  it('shows Australian learners their own morning for a UK evening session', () => {
    expect(sessionTime(tuesdayEvening, 'Australia/Sydney')).toMatch(/^Wed 4 Nov, 06:00 (GMT\+11|AEDT)$/)
  })

  it('follows British Summer Time', () => {
    expect(sessionTime('2026-07-07T18:00:00Z', 'Europe/London')).toBe('Tue 7 Jul, 19:00 BST')
  })
})

describe('startsIn', () => {
  const at = '2026-11-03T19:00:00Z'
  const minutesBefore = (m: number) => new Date(Date.parse(at) - m * 60_000)

  it('counts down in the hour before', () => {
    expect(startsIn(at, 60, minutesBefore(25))).toBe('Starts in 25 minutes')
    expect(startsIn(at, 60, minutesBefore(1))).toBe('Starts in 1 minute')
  })

  it('says On now during the session', () => {
    expect(startsIn(at, 60, minutesBefore(0))).toBe('On now')
    expect(startsIn(at, 60, minutesBefore(-59))).toBe('On now')
  })

  it('says nothing further off, or once it is over', () => {
    expect(startsIn(at, 60, minutesBefore(90))).toBe('')
    expect(startsIn(at, 60, minutesBefore(-61))).toBe('')
  })
})

describe('ZOOM_SDK', () => {
  it('loads only from Zoom\'s CDN, over https', () => {
    for (const { src } of ZOOM_SDK) expect(src).toMatch(/^https:\/\/source\.zoom\.us\//)
  })

  it('pins every file to one SDK version', () => {
    const versions = new Set(ZOOM_SDK.map(({ src }) => src.match(/source\.zoom\.us\/(\d+\.\d+\.\d+)\//)?.[1]))
    expect(versions.size).toBe(1)
    expect([...versions][0]).toMatch(/^\d+\.\d+\.\d+$/)
  })

  it('checks every file against a sha384 hash', () => {
    for (const { integrity } of ZOOM_SDK) expect(integrity).toMatch(/^sha384-[A-Za-z0-9+/]{64}$/)
  })

  it('loads the SDK last, after the libraries it needs as page globals', () => {
    expect(ZOOM_SDK.at(-1)?.src).toMatch(/zoom-meeting-embedded-[\d.]+\.min\.js$/)
    expect(ZOOM_SDK.slice(0, -1).map(({ src }) => src.split('/').pop())).toEqual([
      'react.min.js',
      'react-dom.min.js',
      'redux.min.js',
      'redux-thunk.min.js',
      'lodash.min.js',
      'react-redux.min.js',
    ])
  })
})

describe('mailto', () => {
  it('opens an email to the contact address with spaces kept as spaces', () => {
    const link = mailto('Apply for Motor Movers', 'Name:\nSchool:')
    expect(link.startsWith(`mailto:${CONTACT_EMAIL}?`)).toBe(true)
    const query = new URLSearchParams(link.split('?')[1])
    expect(query.get('subject')).toBe('Apply for Motor Movers')
    expect(query.get('body')).toBe('Name:\nSchool:')
    expect(link).not.toContain('+')
  })
})

describe('signInRole', () => {
  it('finds each role the website offers', () => {
    for (const role of SIGN_IN_ROLES) expect(signInRole(role.key)).toBe(role)
  })

  it('finds nothing for an unknown or missing role', () => {
    expect(signInRole('janitor')).toBeUndefined()
    expect(signInRole(null)).toBeUndefined()
  })

  it('calls the trust role "trust or group lead", never "trust lead"', () => {
    expect(signInRole('trust')?.label).toBe('Trust or group lead')
  })
})

describe('stages', () => {
  it('lists the seven colour stages in rainbow order', () => {
    expect(STAGES.map((s) => s.label)).toEqual(['Red', 'Orange', 'Yellow', 'Green', 'Blue', 'Indigo', 'Violet'])
  })

  it('looks a stage up, and gives nothing for no stage', () => {
    expect(stageInfo('GREEN')?.label).toBe('Green')
    expect(stageInfo(null)).toBeUndefined()
    expect(stageInfo(undefined)).toBeUndefined()
  })

  it('rounds fractions to whole percentages', () => {
    expect(pct(0)).toBe('0%')
    expect(pct(0.7)).toBe('70%')
    expect(pct(0.666)).toBe('67%')
    expect(pct(1)).toBe('100%')
  })
})
