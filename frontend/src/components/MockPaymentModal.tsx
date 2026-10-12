import { useState } from 'react'
import type { PaymentOrder } from '../api/types'
import { rupees } from '../utils/format'

interface Props {
  order: PaymentOrder
  onPay: (success: boolean) => Promise<void>
  onClose: () => void
}

/**
 * Stands in for the Razorpay checkout popup when the backend runs the mock gateway.
 * Nothing typed here is sent anywhere; the card fields are only for the look of it.
 */
export default function MockPaymentModal({ order, onPay, onClose }: Props) {
  const [busy, setBusy] = useState(false)

  const pay = async (success: boolean) => {
    setBusy(true)
    try {
      await onPay(success)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Test payment">
      <div className="modal pay-modal">
        <div className="pay-header">
          <div>
            <b>SeatSure Test Pay</b>
            <p className="small">{order.description}</p>
          </div>
          <span className="pay-amount">{rupees(order.amount)}</span>
        </div>
        <div className="pay-body">
          <div className="alert alert-info small">
            Test mode. No real money moves. Use the buttons below to simulate a successful or a failed payment.
          </div>
          <label>
            Card number
            <input value="4111 1111 1111 1111" readOnly />
          </label>
          <div className="form-row">
            <label>
              Expiry
              <input value="12/30" readOnly />
            </label>
            <label>
              CVV
              <input value="123" readOnly />
            </label>
          </div>
          <p className="muted small">Order {order.orderId}</p>
          <button className="btn btn-block" disabled={busy} onClick={() => pay(true)}>
            {busy ? 'Processing...' : `Pay ${rupees(order.amount)}`}
          </button>
          <button className="btn btn-outline btn-block" disabled={busy} onClick={() => pay(false)}>
            Simulate a failed payment
          </button>
          <button className="link-button center-block" disabled={busy} onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  )
}
