import { clock, duration, isoDate, nextDays, rupees, secondsLeft, showTime } from './format'

describe('format helpers', () => {
  it('formats rupees the Indian way', () => {
    expect(rupees(450)).toBe('₹450')
    expect(rupees(125000)).toBe('₹1,25,000')
  })

  it('reads show times as local theatre time', () => {
    expect(showTime('2026-10-12T20:30:00')).toBe('8:30 PM')
    expect(showTime('2026-10-12T10:00:00')).toBe('10:00 AM')
  })

  it('formats durations', () => {
    expect(duration(142)).toBe('2h 22m')
    expect(duration(45)).toBe('45m')
  })

  it('counts down the hold timer', () => {
    const now = Date.parse('2026-10-12T10:00:00Z')
    expect(secondsLeft('2026-10-12T10:05:00Z', now)).toBe(300)
    expect(secondsLeft('2026-10-12T09:59:00Z', now)).toBe(0)
    expect(clock(299)).toBe('4:59')
    expect(clock(5)).toBe('0:05')
  })

  it('builds yyyy-mm-dd dates', () => {
    expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(nextDays(3, new Date(2026, 9, 30))).toEqual(['2026-10-30', '2026-10-31', '2026-11-01'])
  })
})
