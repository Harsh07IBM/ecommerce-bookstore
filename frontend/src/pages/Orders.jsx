import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getOrders, cancelOrder, buyAgain } from '../api';
import { useBasket } from '../context/BasketContext';
import Spinner from '../components/Spinner';
import toast from 'react-hot-toast';

const STATUS_COLORS = {
  PAID:      'bg-green-900/50 text-green-400',
  CANCELLED: 'bg-red-900/50 text-red-400',
};

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const { refresh } = useBasket();

  const load = () => getOrders().then(setOrders).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const handleCancel = async (id) => {
    if (!confirm('Cancel this order?')) return;
    try {
      await cancelOrder(id);
      toast.success('Order cancelled');
      load();
    } catch (e) { toast.error(e.message); }
  };

  const handleBuyAgain = async (id) => {
    try {
      await buyAgain(id);
      await refresh();
      toast.success('Items re-added to basket!');
    } catch (e) { toast.error(e.message); }
  };

  if (loading) return <div className="flex justify-center py-32"><Spinner size="lg" /></div>;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
      <h1 className="font-serif text-3xl font-bold text-white mb-8">My Orders</h1>

      {orders.length === 0 ? (
        <div className="text-center py-24">
          <p className="text-5xl mb-4">📦</p>
          <h2 className="font-serif text-2xl font-bold text-white mb-2">No orders yet</h2>
          <p className="text-gray-400 mb-8">Your order history will appear here.</p>
          <Link to="/books" className="btn-primary">Start Shopping</Link>
        </div>
      ) : (
        <div className="space-y-4">
          {orders.map(o => (
            <div key={o.orderId} className="card p-5 space-y-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs text-gray-500">Order #{o.orderId}</p>
                  <p className="text-sm text-gray-400 mt-0.5">{o.orderDate?.split('T')[0]}</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className={`badge px-3 py-1 ${STATUS_COLORS[o.status] || 'bg-gray-800 text-gray-400'}`}>{o.status}</span>
                  <Link to={`/orders/${o.orderId}/confirmation`} className="text-sm text-brand-400 hover:text-brand-300 transition-colors">Details →</Link>
                </div>
              </div>

              {/* Items preview */}
              <div className="text-sm text-gray-400 divide-y divide-gray-800">
                {o.items?.slice(0, 3).map(item => (
                  <div key={item.bookId} className="flex justify-between py-1.5">
                    <span className="truncate max-w-xs">{item.title} ×{item.quantity}</span>
                    <span>₹{Number(item.lineTotal).toFixed(2)}</span>
                  </div>
                ))}
                {o.items?.length > 3 && <p className="py-1.5 text-gray-600">+{o.items.length - 3} more items</p>}
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 pt-1 border-t border-gray-800">
                <p className="font-bold text-white">Total: ₹{Number(o.totalAmount).toFixed(2)}</p>
                <div className="flex gap-3">
                  <button onClick={() => handleBuyAgain(o.orderId)} className="btn-outline text-sm py-1.5 px-4">🔁 Buy Again</button>
                  {o.status === 'PAID' && (
                    <button onClick={() => handleCancel(o.orderId)} className="text-sm text-red-400 hover:text-red-300 border border-red-900 hover:border-red-600 px-4 py-1.5 rounded-xl transition-all">
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
