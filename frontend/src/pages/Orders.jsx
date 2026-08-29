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
  const [cancelling, setCancelling] = useState(null);
  const [buyingAgain, setBuyingAgain] = useState(null);
  const { refresh } = useBasket();

  const load = () => getOrders().then(setOrders).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const handleCancel = async (id) => {
    if (!confirm('Cancel this order?')) return;
    setCancelling(id);
    try {
      await cancelOrder(id);
      toast.success('Order cancelled');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setCancelling(null);
    }
  };

  const handleBuyAgain = async (id) => {
    setBuyingAgain(id);
    try {
      await buyAgain(id);
      await refresh();
      toast.success('Items re-added to your basket!');
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBuyingAgain(null);
    }
  };

  if (loading) return (
    <div className="flex justify-center items-center py-40">
      <Spinner size="lg" />
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-950">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
        <h1 className="font-serif text-3xl font-bold text-white mb-8">My Orders</h1>

        {orders.length === 0 ? (
          /* ── Empty state ── */
          <div className="text-center py-24">
            <div className="w-20 h-20 bg-gray-900 border border-gray-800 rounded-full flex items-center justify-center mx-auto mb-6">
              <svg className="w-9 h-9 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                  d="M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2zm-9 6h2m-1-3v6" />
              </svg>
            </div>
            <h2 className="font-serif text-2xl font-bold text-white mb-2">No orders yet</h2>
            <p className="text-gray-400 mb-8">Your order history will appear here once you place your first order.</p>
            <Link to="/books" className="btn-primary">Start Shopping</Link>
          </div>
        ) : (
          /* ── Order list ── */
          <div className="space-y-4">
            {orders.map(o => (
              <div key={o.orderId} className="bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden hover:border-gray-700 transition-colors">

                {/* Card header */}
                <div className="px-5 py-4 flex flex-wrap items-start justify-between gap-3 border-b border-gray-800">
                  <div>
                    <p className="text-xs text-gray-500">Order</p>
                    <p className="font-mono font-semibold text-white mt-0.5">#{o.orderId}</p>
                    <p className="text-xs text-gray-500 mt-1">{o.orderDate?.split('T')[0]}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`badge ${STATUS_COLORS[o.status] || 'bg-gray-800 text-gray-400'}`}>
                      {o.status}
                    </span>
                    <Link to={`/orders/${o.orderId}/confirmation`}
                      className="text-sm text-brand-400 hover:text-brand-300 transition-colors flex items-center gap-1">
                      Details
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </Link>
                  </div>
                </div>

                {/* Items preview */}
                <div className="px-5 py-3 space-y-0 divide-y divide-gray-800/60">
                  {o.items?.slice(0, 3).map(item => (
                    <div key={item.bookId} className="flex justify-between py-2 text-sm">
                      <span className="text-gray-400 truncate max-w-xs">
                        {item.title}
                        <span className="text-gray-600 ml-1">×{item.quantity}</span>
                      </span>
                      <span className="text-gray-300 ml-3 shrink-0">₹{Number(item.lineTotal).toFixed(2)}</span>
                    </div>
                  ))}
                  {o.items?.length > 3 && (
                    <p className="py-2 text-xs text-gray-600">
                      +{o.items.length - 3} more {o.items.length - 3 === 1 ? 'item' : 'items'}
                    </p>
                  )}
                </div>

                {/* Footer */}
                <div className="px-5 py-4 flex flex-wrap items-center justify-between gap-3 border-t border-gray-800 bg-gray-900/50">
                  <p className="font-bold text-white">
                    Total: ₹{Number(o.totalAmount).toFixed(2)}
                  </p>
                  <div className="flex items-center gap-3">
                    {/* Buy Again */}
                    <button
                      onClick={() => handleBuyAgain(o.orderId)}
                      disabled={buyingAgain === o.orderId}
                      className="btn-outline text-sm py-1.5 px-4 flex items-center gap-1.5 disabled:opacity-50">
                      {buyingAgain === o.orderId
                        ? <Spinner size="sm" />
                        : <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                          </svg>
                      }
                      Buy Again
                    </button>

                    {/* Cancel */}
                    {o.status === 'PAID' && (
                      <button
                        onClick={() => handleCancel(o.orderId)}
                        disabled={cancelling === o.orderId}
                        className="text-sm text-red-400 hover:text-red-300
                          border border-red-900 hover:border-red-700
                          px-4 py-1.5 rounded-xl transition-all disabled:opacity-50
                          flex items-center gap-1.5">
                        {cancelling === o.orderId
                          ? <Spinner size="sm" />
                          : <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        }
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
    </div>
  );
}
