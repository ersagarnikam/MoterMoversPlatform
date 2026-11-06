import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { Induction } from '../api'
import { Lessons } from './TrainingPage'

const lesson = (videoUrl: string | null, filmHeld: boolean): Induction['lessons'][number] => ({
  id: 1,
  position: 1,
  title: 'Warm up',
  summary: 'The warm up',
  paragraphs: ['Start slowly.'],
  minutes: 5,
  videoUrl,
  filmHeld,
  done: false,
})

const render = (l: Induction['lessons'][number]) =>
  renderToStaticMarkup(<Lessons lessons={[l]} onComplete={() => {}} />)

describe('Lessons', () => {
  it('plays a film the person may watch', () => {
    const html = render(lesson('https://films.test/a.mp4', false))
    expect(html).toContain('<video')
    expect(html).not.toContain('Motor Movers licence')
  })

  it('says who a held film is for and keeps the lesson', () => {
    const html = render(lesson(null, true))
    expect(html).not.toContain('<video')
    expect(html).toContain('Motor Movers licence')
    expect(html).toContain('Start slowly.')
    expect(html).toContain('Mark as done')
  })

  it('shows nothing where a lesson has no film', () => {
    const html = render(lesson(null, false))
    expect(html).not.toContain('<video')
    expect(html).not.toContain('Motor Movers licence')
  })
})
