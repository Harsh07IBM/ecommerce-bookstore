import { Link, useNavigate } from 'react-router-dom';
import { useBasket } from '../context/BasketContext';
import { useAuth } from '../context/AuthContext';
import { useState } from 'react';
import toast from 'react-hot-toast';
import Spinner from '../components/Spinner';

export default function Basket() {
  const { basket, loading, update, remove, clear } = useBasket();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [removing, setRemoving] = useState(null);
  const [updating, setUpdating] = useState(null);

  const handleRemove = async (bookId) => {
    setRemoving(bookId);
    try { await remove(bookId); }
    catch (e) { toast.error(e.message); }
    finally { setRemoving(null); }
  };

  const handleQty = async (bookId, qty) => {
    setUpdating(bookId);
    try { await update(bookId, qty); }
    catch (e) { toast.error(e.message); }
    finally { setUpdating(null); }
  };

  const delivery = Number(basket.basketTotal) >= 500 ? 0 : 50;
  const total    = Number(basket.basketTotal) + delivery;

  if (loading) return (
    <div className="flex justify-center items-center py-40">
      <Spinner size="lg" />
    </div>
  );

  if (basket.items.length === 0) return (
    <div className="max-w-lg mx-auto text-center py-32 px-4 animate-fade-in">
      <div className="w-24 h-24 bg-gray-900 border border-gray-800 rounded-full flex items-center justify-center mx-auto mb-6">
        <svg className="w-10 h-10 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13l-1.4 7h12.8M9 19a1 1 0 1 0 2 0 1 1 0 0 0-2 0zm8 0a1 1 0 1 0 2 0 1 1 0 0 0-2 0z" />
        </svg>
      </div>
      <h2 className="font-serif text-2xl font-bold text-white mb-2">Your basket is empty</h2>
      <p className="text-gray-500 mb-8 text-sm">Add some books to get started.</p>
      <Link to="/books" className="btn-primary px-8">Discover Books</Link>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-950">
      {/* Page header */}
      <div className="border-b border-gray-800 bg-gray-900/50">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-serif text-2xl font-bold text-white">Shopping Basket</h1>
              <p className="text-sm text-gray-500 mt-0.5">{basket.totalItems} {basket.totalItems === 1 ? 'item' : 'items'}</p>
            </div>
            <button
              onClick={() => clear().catch(e => toast.error(e.message))}
              className="text-xs text-gray-600 hover:text-red-400 transition-colors flex items-center gap-1.5 border border-gray-800 hover:border-red-900 px-3 py-1.5 rounded-lg"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Clear basket
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="grid lg:grid-cols-[1fr_360px] gap-8 items-start">

          {/* ── ITEMS LIST ─────────────────────────────── */}
          <div className="space-y-3">
            {basket.items.map((item, idx) => (
              <div
                key={item.bookId}
                className={`bg-gray-900 border rounded-2xl p-5 transition-all duration-200
                  ${removing === item.bookId ? 'opacity-40 scale-95' : 'border-gray-800 hover:border-gray-700'}`}
                style={{ animationDelay: `${idx * 40}ms` }}
              >
                <div className="flex gap-5">
                  {/* Cover */}
                  <Link to={`/books/${item.bookId}`} className="shrink-0">
                    <div className="w-[72px] h-[100px] rounded-xl overflow-hidden bg-gray-800 shadow-lg">
                      {item.coverImageUrl
                        ? <img src={item.coverImageUrl} alt={item.title} className="w-full h-full object-cover hover:scale-105 transition-transform duration-300" />
                        : <div className="w-full h-full flex items-center justify-center text-2xl bg-gradient-to-br from-brand-900 to-gray-800">📖</div>
                      }
                    </div>
                  </Link>

                  {/* Details */}
                  <div className="flex-1 min-w-0 flex flex-col justify-between">
                    <div>
                      <Link to={`/books/${item.bookId}`}
                        className="font-semibold text-white hover:text-brand-300 transition-colors leading-snug line-clamp-2 text-sm">
                        {item.title}
                      </Link>
                      {item.author && (
                        <p className="text-xs text-gray-500 mt-1">{item.author}</p>
                      )}
                    </div>

                    <div className="flex items-center justify-between mt-4 flex-wrap gap-3">
                      {/* Qty stepper */}
                      <div className="flex items-center border border-gray-700 rounded-xl overflow-hidden">
                        <button
                          onClick={() => item.quantity === 1 ? handleRemove(item.bookId) : handleQty(item.bookId, item.quantity - 1)}
                          disabled={updating === item.bookId}
                          className="w-9 h-9 flex items-center justify-center text-gray-400 hover:text-white hover:bg-gray-800 transition-colors text-lg disabled:opacity-40"
                        >−</button>
                        <span className="w-9 h-9 flex items-center justify-center text-sm font-semibold text-white border-x border-gray-700 bg-gray-900">
                          {updating === item.bookId ? '…' : item.quantity}
                        </span>
                        <button
                          onClick={() => handleQty(item.bookId, item.quantity + 1)}
                          disabled={item.quantity >= 7 || updating === item.bookId}
                          className="w-9 h-9 flex items-center justify-center text-gray-400 hover:text-white hover:bg-gray-800 transition-colors text-lg disabled:opacity-30"
                        >+</button>
                      </div>

                      {/* Price */}
                      <div className="text-right">
                        <p className="font-bold text-white text-base">₹{Number(item.lineTotal).toFixed(2)}</p>
                        {item.quantity > 1 && (
                          <p className="text-xs text-gray-600">₹{Number(item.unitPrice).toFixed(2)} each</p>
                        )}
                      </div>

                      {/* Remove */}
                      <button
                        onClick={() => handleRemove(item.bookId)}
                        className="text-gray-700 hover:text-red-400 transition-colors p-1.5 rounded-lg hover:bg-red-950/30"
                        title="Remove"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))}

            {/* Continue shopping */}
            <Link to="/books"
              className="flex items-center gap-2 text-sm text-gray-500 hover:text-brand-400 transition-colors pt-2 group">
              <svg className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              Continue Shopping
            </Link>
          </div>

          {/* ── ORDER SUMMARY ──────────────────────────── */}
          <div className="lg:sticky lg:top-20 space-y-4">
            <div className="bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden">
              {/* Header */}
              <div className="px-6 py-4 border-b border-gray-800">
                <h2 className="font-semibold text-white">Order Summary</h2>
              </div>

              {/* Line items */}
              <div className="px-6 py-4 space-y-3 max-h-52 overflow-y-auto">
                {basket.items.map(item => (
                  <div key={item.bookId} className="flex justify-between text-sm">
                    <span className="text-gray-400 truncate max-w-[180px]">
                      {item.title}
                      <span className="text-gray-600 ml-1">×{item.quantity}</span>
                    </span>
                    <span className="text-gray-300 ml-3 shrink-0">₹{Number(item.lineTotal).toFixed(2)}</span>
                  </div>
                ))}
              </div>

              {/* Totals */}
              <div className="px-6 py-4 border-t border-gray-800 space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-400">Subtotal ({basket.totalItems} items)</span>
                  <span className="text-gray-300">₹{Number(basket.basketTotal).toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-400">Delivery</span>
                  {delivery === 0
                    ? <span className="text-green-400 font-medium">FREE</span>
                    : <span className="text-gray-300">₹{delivery.toFixed(2)}</span>
                  }
                </div>
                {delivery > 0 && (
                  <p className="text-xs text-gray-600 bg-gray-800/60 rounded-lg px-3 py-2">
                    💡 Add ₹{(500 - Number(basket.basketTotal)).toFixed(2)} more for free delivery
                  </p>
                )}
                <div className="border-t border-gray-800 pt-3 flex justify-between">
                  <span className="font-semibold text-white">Total</span>
                  <span className="font-bold text-white text-lg">₹{total.toFixed(2)}</span>
                </div>
              </div>

              {/* CTA */}
              <div className="px-6 pb-6 space-y-3">
                {user ? (
                  <button
                    onClick={() => navigate('/checkout')}
                    className="btn-primary w-full py-3 text-base flex items-center justify-center gap-2"
                  >
                    Checkout
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                    </svg>
                  </button>
                ) : (
                  <div className="space-y-2">
                    <Link to="/login" className="btn-primary w-full py-3 text-base flex items-center justify-center gap-2 block text-center">
                      Sign in to Checkout
                    </Link>
                    <p className="text-center text-xs text-gray-600">
                      No account?{' '}
                      <Link to="/register" className="text-brand-400 hover:underline">Create one free</Link>
                    </p>
                  </div>
                )}

                {/* Trust badges */}
                <div className="grid grid-cols-3 gap-2 pt-2 border-t border-gray-800">
                  {[
                    { icon: '🔒', label: 'Secure' },
                    { icon: '↩️', label: 'Easy Returns' },
                    { icon: '📦', label: 'Fast Delivery' },
                  ].map(b => (
                    <div key={b.label} className="flex flex-col items-center gap-1">
                      <span className="text-base">{b.icon}</span>
                      <span className="text-xs text-gray-600">{b.label}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
