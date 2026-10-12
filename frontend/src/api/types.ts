export type Role = 'USER' | 'ADMIN'
export type MovieKind = 'MOVIE' | 'EVENT'
export type SeatCategory = 'NORMAL' | 'PREMIUM'
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'
export type BookingStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'PAYMENT_FAILED'
export type ShowStatus = 'SCHEDULED' | 'CANCELLED'

export interface ApiErrorBody {
  status: number
  message: string
  fields?: Record<string, string>
}

export interface User {
  id: number
  fullName: string
  email: string
  phone?: string | null
  role: Role
}

export interface AuthResponse {
  token: string
  expiresIn: number
  user: User
}

export interface City {
  id: number
  name: string
  state: string
}

export interface Movie {
  id: number
  title: string
  description?: string | null
  kind: MovieKind
  language: string
  genre: string
  durationMinutes: number
  certificate: string
  posterUrl?: string | null
  releaseDate?: string | null
  active: boolean
}

export interface ShowSlot {
  id: number
  startTime: string
  screenName: string
  priceNormal: number
  pricePremium: number
  availableSeats: number
  totalSeats: number
}

export interface TheatreShows {
  theatreId: number
  theatreName: string
  address: string
  shows: ShowSlot[]
}

export interface ShowDetail {
  id: number
  movie: Movie
  theatreId: number
  theatreName: string
  theatreAddress: string
  city: string
  screenName: string
  startTime: string
  endTime: string
  priceNormal: number
  pricePremium: number
  status: ShowStatus
}

export interface Seat {
  id: number
  number: number
  label: string
  category: SeatCategory
  status: SeatStatus
  price: number
}

export interface SeatRow {
  label: string
  category: SeatCategory
  seats: Seat[]
}

export interface SeatMap {
  showId: number
  holdMinutes: number
  maxSeats: number
  rows: SeatRow[]
}

export interface SeatUpdate {
  showId: number
  seats: { seatId: number; status: SeatStatus }[]
}

export interface Booking {
  id: number
  bookingCode: string
  status: BookingStatus
  showId: number
  movieId: number
  movieTitle: string
  kind: MovieKind
  language: string
  certificate: string
  posterUrl?: string | null
  theatreName: string
  theatreAddress: string
  city: string
  screenName: string
  startTime: string
  seats: string[]
  seatCount: number
  totalAmount: number
  refundAmount?: number | null
  holdExpiresAt: string
  createdAt: string
  confirmedAt?: string | null
  cancelledAt?: string | null
  cancellable: boolean
}

export interface RefundPreview {
  bookingId: number
  cancellable: boolean
  refundPercent: number
  refundAmount: number
  message: string
}

export interface PaymentOrder {
  gateway: 'MOCK' | 'RAZORPAY'
  keyId?: string | null
  orderId: string
  amount: number
  amountPaise: number
  currency: string
  bookingId: number
  bookingCode: string
  holdExpiresAt: string
  description: string
}

export interface MockCheckoutResult {
  orderId: string
  paymentId?: string | null
  signature?: string | null
  success: boolean
  error?: string | null
}

export interface Theatre {
  id: number
  name: string
  address: string
  cityId: number
  cityName: string
  screenCount: number
}

export interface Screen {
  id: number
  theatreId: number
  theatreName: string
  name: string
  rows: number
  seatsPerRow: number
  seatCount: number
  premiumRows: string[]
}

export interface AdminShow {
  id: number
  movieId: number
  movieTitle: string
  theatreId: number
  theatreName: string
  screenId: number
  screenName: string
  startTime: string
  endTime: string
  priceNormal: number
  pricePremium: number
  status: ShowStatus
  seatsBooked: number
  capacity: number
  version: number
}

export interface Dashboard {
  totalRevenue: number
  confirmedBookings: number
  cancelledBookings: number
  activeHolds: number
  upcomingShows: number
  users: number
  revenueByDay: { day: string; revenue: number; bookings: number }[]
  mostBookedShows: {
    showId: number
    movieTitle: string
    theatreName: string
    startTime: string
    seatsSold: number
    capacity: number
    revenue: number
  }[]
  topMovies: { title: string; seatsSold: number; revenue: number }[]
  recentBookings: {
    bookingCode: string
    userName: string
    movieTitle: string
    seatCount: number
    amount: number
    status: BookingStatus
    confirmedAt: string
  }[]
}

export interface TicketCheck {
  result: 'VALID' | 'ALREADY_USED' | 'CANCELLED' | 'INVALID'
  message: string
  bookingCode?: string | null
  movieTitle?: string | null
  theatreName?: string | null
  startTime?: string | null
  seats: string[]
  holder?: string | null
}
