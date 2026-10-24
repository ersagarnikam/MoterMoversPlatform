import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getToken, setToken } from './api'

const fetchMock = vi.fn()
const assign = vi.fn()

function reply(status: number, body: unknown, type = 'application/json') {
  const text = typeof body === 'string' ? body : JSON.stringify(body)
  return new Response(text, { status, statusText: `status ${status}`, headers: { 'Content-Type': type } })
}

beforeEach(() => {
  const session = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => session.get(k) ?? null,
    setItem: (k: string, v: string) => session.set(k, v),
    removeItem: (k: string) => session.delete(k),
  })
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('window', { location: { assign } })
  fetchMock.mockReset()
  assign.mockReset()
})

afterEach(() => vi.unstubAllGlobals())

describe('the API client', () => {
  it('sends the signed-in token with every request', async () => {
    setToken('t-123')
    fetchMock.mockResolvedValue(reply(200, { paidPence: 0, outstandingPence: 0, payments: [] }))
    await api.myPayments()
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/payments/mine')
    expect(init.method).toBe('GET')
    expect(init.headers.Authorization).toBe('Bearer t-123')
  })

  it('sends a JSON body only when there is one', async () => {
    setToken('t')
    fetchMock.mockResolvedValue(reply(200, {}))
    await api.markPresent(5, 9, true)
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/admin/live/5/register/9')
    expect(init.method).toBe('PUT')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ present: true })

    fetchMock.mockResolvedValue(reply(200, {}))
    await api.joinLive(5)
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined()
    expect(fetchMock.mock.calls[1][1].headers['Content-Type']).toBeUndefined()
  })

  it('turns a problem detail into an error with its user-safe message', async () => {
    setToken('t')
    fetchMock.mockResolvedValue(reply(400, { status: 400, detail: 'This session opens 15 minutes before it starts' }))
    const error = await api.joinLive(5).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('This session opens 15 minutes before it starts')
  })

  it('falls back to the status text when the error is not JSON', async () => {
    setToken('t')
    fetchMock.mockResolvedValue(reply(502, '<html>Bad gateway</html>', 'text/html'))
    await expect(api.myPayments()).rejects.toThrow('status 502')
  })

  it('signs out and goes to the sign-in page when the session has expired', async () => {
    setToken('expired')
    fetchMock.mockResolvedValue(reply(401, { detail: 'Unauthorized' }))
    await expect(api.myPayments()).rejects.toBeInstanceOf(ApiError)
    expect(getToken()).toBeNull()
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it('does not redirect when a sign-in attempt itself is refused', async () => {
    fetchMock.mockResolvedValue(reply(401, { detail: 'Email or password is incorrect' }))
    await expect(api.login('a@b.test', 'wrong')).rejects.toThrow('Email or password is incorrect')
    expect(assign).not.toHaveBeenCalled()
  })

  it('returns text for downloads such as CSV exports', async () => {
    setToken('t')
    fetchMock.mockResolvedValue(reply(200, 'class,children\nYear 2 R,28\n', 'text/csv'))
    expect(await api.exportCsv(1, 'governor-summary')).toBe('class,children\nYear 2 R,28\n')
  })
})
