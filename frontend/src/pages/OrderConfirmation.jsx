import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getConfirmation } from '../api';
import Spinner from '../components/Spinner';

export default function OrderConfirmation() {
  const { id } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getConfirmation(id).then(setOrder).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="flex justify-center py-32"><Spinner size="lg" /></div>;
  if (!order) return <div className="text-center py-32 text-gray-500">Order not found.</div>;

  return (
    <div className="max-w-2xl mx-auto px-4 py-16 animate-fade-in text-center">
      {/* Success badge */}
      <div className="w-20 h-20 bg-green-900/40 border-2 border-green-500 rounded-full flex items-center justify-center text-4xl mx-auto mb-6 animate-pulse-slow">
        ✅
      </div>
      <h1 className="font-serif text-3xl font-bold text-white mb-2">Order Confirmed!</h1>
      <p className="text-gray-400 mb-8">{order.confirmationMessage}</p>

      <div className="card text-left p-6 space-y-4">
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Order #</span>
          <span className="font-mono text-white">{order.orderId}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Status</span>
          <span className="badge bg-green-900/50 text-green-400">{order.status}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Estimated Delivery</span>
          <span className="text-white">{order.estimatedDeliveryDate}</span>
        </div>

        <div className="border-t border-gray-700 pt-4 space-y-2">
          {order.items?.map(item => (
            <div key={item.bookId} className="flex justify-between text-sm text-gray-300">
              <span className="truncate max-w-xs">{item.title} ×{item.quantity}</span>
              <span>₹{Number(item.lineTotal).toFixed(2)}</span>
            </div>
          ))}
        </div>

        <div className="border-t border-gray-700 pt-3 space-y-1 text-sm">
          <div className="flex justify-between text-gray-400"><span>Subtotal</span><span>₹{Number(order.basketTotal).toFixed(2)}</span></div>
          <div className="flex justify-between text-gray-400"><span>Delivery</span>
            <span>{Number(order.deliveryCharge) === 0 ? <span className="text-green-400">FREE</span> : `₹${Number(order.deliveryCharge).toFixed(2)}`}</span>
          </div>
          {order.giftPointsRedeemed > 0 && (
            <div className="flex justify-between text-brand-300"><span>Gift Points</span><span>-₹{order.giftPointsRedeemed}</span></div>
          )}
          <div className="flex justify-between font-bold text-white text-base pt-1">
            <span>Total Paid</span><span>₹{Number(order.totalAmount).toFixed(2)}</span>
          </div>
        </div>

        {order.pointsAwarded > 0 && (
          <div className="bg-brand-900/30 border border-brand-700/50 rounded-xl p-3 text-sm text-brand-300">
            🎁 You earned <strong>{order.pointsAwarded}</strong> gift points on this order!
          </div>
        )}

        <div className="border-t border-gray-700 pt-3 text-sm text-gray-400">
          <p className="font-medium text-gray-300 mb-1">Delivering to</p>
          <p>{order.deliveryAddress?.recipientName}</p>
          <p>{order.deliveryAddress?.line1}{order.deliveryAddress?.line2 ? `, ${order.deliveryAddress.line2}` : ''}</p>
          <p>{order.deliveryAddress?.city}, {order.deliveryAddress?.state} — {order.deliveryAddress?.pincode}</p>
        </div>
      </div>

      <div className="flex gap-4 justify-center mt-8">
        <Link to="/orders" className="btn-outline">View All Orders</Link>
        <Link to="/books" className="btn-primary">Continue Shopping</Link>
      </div>
    </div>
  );
}
