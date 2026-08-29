import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getConfirmation } from '../api';
import Spinner from '../components/Spinner';

const STATUS_BADGE = {
  PAID:      'bg-green-900/50 text-green-400',
  CANCELLED: 'bg-red-900/50 text-red-400',
};

export default function OrderConfirmation() {
  const { id } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getConfirmation(id).then(setOrder).finally(() => setLoading(false));
  }, [id]);

  if (loading) return (
    <div className="flex justify-center items-center py-40">
      <Spinner size="lg" />
    </div>
  );

  if (!order) return (
    <div className="text-center py-40 text-gray-500">Order not found.</div>
  );

  const fmt = (v) => `₹${Number(v).toFixed(2)}`;

  return (
    <div className="min-h-screen bg-gray-950">
      <div className="max-w-2xl mx-auto px-4 py-16 animate-fade-in">

        {/* Success header */}
        <div className="text-center mb-10">
          <div className="w-20 h-20 bg-green-900/40 border-2 border-green-500 rounded-full
               flex items-center justify-center mx-auto mb-6">
            <svg className="w-10 h-10 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h1 className="font-serif text-3xl font-bold text-white mb-2">Order Confirmed!</h1>
          <p className="text-gray-400">{order.confirmationMessage}</p>
        </div>

        {/* Order detail card */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden">

          {/* Meta row */}
          <div className="px-6 py-5 grid grid-cols-2 gap-4 border-b border-gray-800">
            <div>
              <p className="text-xs text-gray-500 mb-0.5">Order Number</p>
              <p className="font-mono font-semibold text-white">#{order.orderId}</p>
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-0.5">Status</p>
              <span className={`badge ${STATUS_BADGE[order.status] || 'bg-gray-800 text-gray-400'}`}>
                {order.status}
              </span>
            </div>
            <div className="col-span-2">
              <p className="text-xs text-gray-500 mb-0.5">Estimated Delivery</p>
              <p className="text-sm text-white flex items-center gap-1.5">
                <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
                {order.estimatedDeliveryDate}
              </p>
            </div>
          </div>

          {/* Items */}
          <div className="px-6 py-4 space-y-2 border-b border-gray-800">
            {order.items?.map(item => (
              <div key={item.bookId} className="flex justify-between text-sm">
                <span className="text-gray-300 truncate max-w-xs">
                  {item.title}
                  <span className="text-gray-600 ml-1">×{item.quantity}</span>
                </span>
                <span className="text-gray-300 ml-3 shrink-0">{fmt(item.lineTotal)}</span>
              </div>
            ))}
          </div>

          {/* Price breakdown */}
          <div className="px-6 py-4 space-y-2 text-sm border-b border-gray-800">
            <div className="flex justify-between text-gray-400">
              <span>Subtotal</span>
              <span>{fmt(order.basketTotal)}</span>
            </div>
            <div className="flex justify-between text-gray-400">
              <span>Delivery</span>
              {Number(order.deliveryCharge) === 0
                ? <span className="text-green-400 font-medium">FREE</span>
                : <span>{fmt(order.deliveryCharge)}</span>
              }
            </div>
            {order.giftPointsRedeemed > 0 && (
              <div className="flex justify-between text-brand-300">
                <span>Gift Points Redeemed</span>
                <span>−₹{order.giftPointsRedeemed}</span>
              </div>
            )}
            <div className="border-t border-gray-800 pt-2 flex justify-between font-bold text-white text-base">
              <span>Total Paid</span>
              <span>{fmt(order.totalAmount)}</span>
            </div>
          </div>

          {/* Gift points earned */}
          {order.pointsAwarded > 0 && (
            <div className="px-6 py-4 border-b border-gray-800">
              <div className="bg-brand-900/30 border border-brand-700/50 rounded-xl px-4 py-3
                   flex items-center gap-3 text-sm text-brand-300">
                <svg className="w-5 h-5 shrink-0 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V5.5A2.5 2.5 0 109.5 8H12zm-7 4h14M5 12a2 2 0 110-4h14a2 2 0 110 4M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
                </svg>
                You earned <strong className="text-brand-200 mx-1">{order.pointsAwarded}</strong> gift points on this order!
              </div>
            </div>
          )}

          {/* Delivery address */}
          <div className="px-6 py-5">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Delivering to</p>
            <div className="text-sm text-gray-400 space-y-0.5">
              <p className="font-medium text-gray-200">{order.deliveryAddress?.recipientName}</p>
              <p>{order.deliveryAddress?.line1}{order.deliveryAddress?.line2 ? `, ${order.deliveryAddress.line2}` : ''}</p>
              <p>{order.deliveryAddress?.city}, {order.deliveryAddress?.state} — {order.deliveryAddress?.pincode}</p>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-4 justify-center mt-8">
          <Link to="/orders" className="btn-outline">View All Orders</Link>
          <Link to="/books" className="btn-primary">Continue Shopping</Link>
        </div>
      </div>
    </div>
  );
}
