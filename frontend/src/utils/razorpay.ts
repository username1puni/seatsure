import type { PaymentOrder } from '../api/types'

interface RazorpayResponse {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

interface RazorpayInstance {
  open: () => void
  on: (event: string, handler: (response: { error: { description: string } }) => void) => void
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance
  }
}

function loadScript(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Could not load Razorpay checkout'))
    document.body.appendChild(script)
  })
}

/** Opens Razorpay's checkout (test mode keys) and reports back what happened. */
export async function openRazorpay(
  order: PaymentOrder,
  user: { name: string; email: string },
  handlers: { onSuccess: (r: RazorpayResponse) => void; onFailure: (reason: string) => void; onDismiss: () => void },
) {
  await loadScript()
  const checkout = new window.Razorpay!({
    key: order.keyId,
    amount: order.amountPaise,
    currency: order.currency,
    name: 'SeatSure',
    description: order.description,
    order_id: order.orderId,
    prefill: { name: user.name, email: user.email },
    theme: { color: '#e11d48' },
    handler: handlers.onSuccess,
    // closing the popup keeps the hold, the user can still pay before the timer runs out
    modal: { ondismiss: handlers.onDismiss },
  })
  checkout.on('payment.failed', (response) => handlers.onFailure(response.error.description))
  checkout.open()
}
