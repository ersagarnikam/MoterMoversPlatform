import { describe, expect, it } from 'vitest'
import { BRAND, HOME_PROGRAMME, TEAM } from './brand'

// The platform is the Professor Pat Preedy Programme (go-live task 84). Nothing people read on the website or the web
// app may still say "Motor Movers", and the name in full is written once, in brand.ts. Code names are not the brand
// and are allowed: the demo domains (motormovers.test). The API's BrandTest checks the API and the parent app the
// same way (a web test cannot read the parent app's sources without Expo installed).

/** Every source file of the website and the web app, as text, by its path from this folder. */
const SOURCES: Record<string, string> = {
  ...import.meta.glob<string>('./**/*.{ts,tsx,css}', { query: '?raw', import: 'default', eager: true }),
  ...import.meta.glob<string>('../index.html', { query: '?raw', import: 'default', eager: true }),
  ...import.meta.glob<string>('../public/*.{svg,js}', { query: '?raw', import: 'default', eager: true }),
}
const OLD_NAME = /motor[ -]?movers/i
const CODE_NAMES = ['motormovers']
/** Where the name in full may be written out: the brand file, and the files that cannot import it. */
const NAMED_IN = ['./brand.ts', './brand.test.ts', '../index.html', '../public/favicon.svg']

/** "path:line: text" for every line that matches, leaving out the files named. */
function linesWith(matches: (line: string) => boolean, skip: string[]): string[] {
  return Object.entries(SOURCES)
    .filter(([path]) => !skip.includes(path))
    .flatMap(([path, text]) =>
      text
        .split(/\r?\n/)
        .map((line, i) => (matches(line) ? `${path}:${i + 1}: ${line.trim()}` : ''))
        .filter(Boolean),
    )
}

describe('the brand', () => {
  it('is the Professor Pat Preedy Programme', () => {
    expect(BRAND).toBe('Professor Pat Preedy Programme')
    expect(BRAND.length).toBeLessThanOrEqual(30) // Apple's limit for an app's name
    expect(HOME_PROGRAMME).toBe('Programme at Home')
    expect(TEAM).toBe('Programme team')
  })

  it('reads the sources', () => {
    const paths = Object.keys(SOURCES)
    expect(paths).toContain('./components/Layout.tsx')
    expect(paths).toContain('../index.html')
    expect(paths).toContain('../public/favicon.svg')
  })

  it('has left no "Motor Movers" on the website or the web app', () => {
    const withoutCodeNames = (line: string) => CODE_NAMES.reduce((rest, name) => rest.split(name).join(''), line)
    expect(linesWith((line) => OLD_NAME.test(withoutCodeNames(line)), ['./brand.test.ts'])).toEqual([])
  })

  it('is written out in full only in the brand file', () => {
    expect(linesWith((line) => line.includes('Pat Preedy Programme'), NAMED_IN)).toEqual([])
  })

  it('names the home programme only in the brand file', () => {
    const inText = /['"`>][^'"`<]*Programme at Home|^\s+[^/*\s][^/]*Programme at Home/
    const comment = /^\s*(\/\/|\/?\*)/
    expect(linesWith((line) => inText.test(line) && !comment.test(line), NAMED_IN)).toEqual([])
  })

  it('is the page title', () => {
    expect(SOURCES['../index.html']).toContain(`<title>${BRAND}</title>`)
  })
})
