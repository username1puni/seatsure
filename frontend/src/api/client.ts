import type {
  AdminShow, ApiErrorBody, AuthResponse, Booking, City, Dashboard, MockCheckoutResult, Movie, MovieKind,
  PaymentOrder, RefundPreview, Screen, SeatMap, ShowDetail, TheatreShows, Theatre, TicketCheck, User,
} from './types'

const TOKEN_KEY = 'seatsure.token'

export class ApiError extends Error {
  status: number
  fields?: Record<string, string>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.status = body.status
    this.fields = body.fields
  }
}

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function send(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body) headers.set('Content-Type', 'application/json')

  let response: Response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new ApiError({ status: 0, message: 'Cannot reach the server. Check your internet connection.' })
  }
  if (response.status === 401 && token) {
    // token expired, log out everywhere
    tokenStore.clear()
    window.dispatchEvent(new Event('seatsure:logout'))
  }
  if (!response.ok) {
    let body: ApiErrorBody = { status: response.status, message: 'Something went wrong. Please try again.' }
    try {
      body = await response.json()
    } catch {
      // not JSON, keep the generic message
    }
    throw new ApiError(body)
  }
  return response
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function download(path: string, filename: string) {
  const response = await send(path)
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const json = (data: unknown) => JSON.stringify(data)
const query = (params: Record<string, string | number | undefined | null>) => {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  })
  return q.toString()
}

export const api = {
  register: (fullName: string, email: string, password: string, phone?: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: json({ fullName, email, password, phone }) }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: json({ email, password }) }),
  me: () => request<User>('/api/auth/me'),

  cities: () => request<City[]>('/api/cities'),
  movies: (cityId: number, date?: string, kind?: MovieKind) =>
    request<Movie[]>(`/api/movies?${query({ cityId, date, kind })}`),
  movie: (id: number) => request<Movie>(`/api/movies/${id}`),
  movieDates: (id: number, cityId: number) => request<string[]>(`/api/movies/${id}/dates?cityId=${cityId}`),
  movieShows: (id: number, cityId: number, date: string) =>
    request<TheatreShows[]>(`/api/movies/${id}/shows?${query({ cityId, date })}`),
  show: (id: number) => request<ShowDetail>(`/api/shows/${id}`),
  seatMap: (id: number) => request<SeatMap>(`/api/shows/${id}/seats`),

  hold: (showId: number, seatIds: number[]) =>
    request<Booking>('/api/bookings/hold', { method: 'POST', body: json({ showId, seatIds }) }),
  releaseHold: (bookingId: number) => request<void>(`/api/bookings/${bookingId}/hold`, { method: 'DELETE' }),
  myBookings: () => request<Booking[]>('/api/bookings'),
  booking: (id: number) => request<Booking>(`/api/bookings/${id}`),
  refundPreview: (id: number) => request<RefundPreview>(`/api/bookings/${id}/refund-preview`),
  cancelBooking: (id: number) => request<Booking>(`/api/bookings/${id}/cancel`, { method: 'POST' }),
  downloadTicket: (booking: Booking) =>
    download(`/api/bookings/${booking.id}/ticket.pdf`, `ticket-${booking.bookingCode}.pdf`),
  qrImage: async (id: number) => URL.createObjectURL(await (await send(`/api/bookings/${id}/qr.png`)).blob()),

  createOrder: (bookingId: number) =>
    request<PaymentOrder>('/api/payments/order', { method: 'POST', body: json({ bookingId }) }),
  verifyPayment: (orderId: string, paymentId: string, signature: string) =>
    request<Booking>('/api/payments/verify', { method: 'POST', body: json({ orderId, paymentId, signature }) }),
  paymentFailed: (orderId: string, reason: string) =>
    request<void>('/api/payments/failure', { method: 'POST', body: json({ orderId, reason }) }),
  mockCheckout: (orderId: string, success: boolean) =>
    request<MockCheckoutResult>('/api/payments/mock/checkout', { method: 'POST', body: json({ orderId, success }) }),

  dashboard: () => request<Dashboard>('/api/admin/dashboard'),
  createCity: (name: string, state: string) =>
    request<City>('/api/admin/cities', { method: 'POST', body: json({ name, state }) }),
  theatres: (cityId?: number) => request<Theatre[]>(`/api/admin/theatres?${query({ cityId })}`),
  createTheatre: (data: { cityId: number; name: string; address: string }) =>
    request<Theatre>('/api/admin/theatres', { method: 'POST', body: json(data) }),
  screens: (theatreId: number) => request<Screen[]>(`/api/admin/theatres/${theatreId}/screens`),
  createScreen: (data: { theatreId: number; name: string; rows: number; seatsPerRow: number; premiumRows: string[] }) =>
    request<Screen>('/api/admin/screens', { method: 'POST', body: json(data) }),
  adminMovies: () => request<Movie[]>('/api/admin/movies'),
  saveMovie: (movie: Partial<Movie>, id?: number) =>
    request<Movie>(id ? `/api/admin/movies/${id}` : '/api/admin/movies', { method: id ? 'PUT' : 'POST', body: json(movie) }),
  adminShows: (date: string) => request<AdminShow[]>(`/api/admin/shows?date=${date}`),
  createShow: (data: { movieId: number; screenId: number; startTime: string; priceNormal: number; pricePremium: number }) =>
    request<AdminShow>('/api/admin/shows', { method: 'POST', body: json(data) }),
  updatePricing: (id: number, priceNormal: number, pricePremium: number, version: number) =>
    request<AdminShow>(`/api/admin/shows/${id}/pricing`, { method: 'PUT', body: json({ priceNormal, pricePremium, version }) }),
  cancelShow: (id: number) =>
    request<{ bookingsRefunded: number }>(`/api/admin/shows/${id}/cancel`, { method: 'POST' }),
  verifyTicket: (code: string) =>
    request<TicketCheck>('/api/admin/tickets/verify', { method: 'POST', body: json({ code }) }),
}
