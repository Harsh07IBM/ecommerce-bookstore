import { Link, useNavigate } from 'react-router-dom';
import { useBasket } from '../context/BasketContext';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import Spinner from '../components/Spinner';

export default function Basket() {
  const { basket, loading, update, remove, clear } = useBasket();
  const { user } = useAuth();
  const navigate = useNavigate();

  const handleRemove = async (bookId) => {
    try { await remove(bookId); } catch (e) { toast.error(e.message); }
  };

  const handleQty = async (bookId, qty) => {
    try { await update(bookId, qty); } catch (e) { toast.error(e.message); }
  };

  if (loading) return <div className="flex justify-center py-32"><Spinner size="lg" /></div>;

  if (basket.items.length === 0) return (
    <div className="max-w-lg mx-auto text-center py-32 px-4 animate-fade-in">
      <p className="text-6xl mb-4">🛒</p>
      <h2 className="font-serif text-2xl font-bold text-white mb-2">Your basket is empty</h2>
      <p className="text-gray-400 mb-8">Looks like you haven't added any books yet.</p>
      <Link to="/books" className="btn-primary">Browse Books</Link>
    </div>
  );

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
      <div className="flex items-center justify-between mb-8">
        <h1 className="font-serif text-3xl font-bold text-white">Your Basket</h1>
        <button onClick={() => clear().catch(e => toast.error(e.message))}
          className="text-sm text-gray-500 hover:text-red-400 transition-colors">
          Clear all
        </button>
      </div>

      <div className="grid lg:grid-cols-3 gap-8">
        {/* Items */}
        <div className="lg:col-span-2 space-y-4">
          {basket.items.map(item => (
            <div key={item.bookId} className="card flex gap-4 p-4">
              <div className="w-16 h-20 rounded-lg overflow-hidden bg-gray-800 shrink-0">
                {item.coverImageUrl
                  ? <img src={item.coverImageUrl} alt={item.title} className="w-full h-full object-cover" />
                  : <div className="w-full h-full flex items-center justify-center text-2xl">📖</div>
                }
              </div>
              <div className="flex-1 min-w-0">
                <Link to={`/books/${item.bookId}`} className="font-semibold text-gray-100 hover:text-brand-300 transition-colors line-clamp-2 text-sm">{item.title}</Link>
                <p className="text-xs text-gray-500 mt-0.5">{item.author}</p>
                <div className="flex items-center gap-4 mt-3">
                  {/* Qty stepper */}
                  <div className="flex items-center gap-2 bg-gray-800 rounded-lg px-2 py-1">
                    <button onClick={() => item.quantity === 1 ? handleRemove(item.bookId) : handleQty(item.bookId, item.quantity - 1)}
                      className="text-gray-400 hover:text-white w-5 text-center">−</button>
                    <span className="w-5 text-center text-sm font-medium">{item.quantity}</span>
                    <button onClick={() => handleQty(item.bookId, item.quantity + 1)}
                      className="text-gray-400 hover:text-white w-5 text-center">+</button>
                  </div>
                  <span className="text-sm font-bold text-white">₹{Number(item.lineTotal).toFixed(2)}</span>
                  <button onClick={() => handleRemove(item.bookId)} className="text-gray-600 hover:text-red-400 transition-colors ml-auto">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6 h-fit space-y-4">
          <h2 className="font-semibold text-white text-lg">Order Summary</h2>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between text-gray-400">
              <span>{basket.totalItems} items</span>
              <span>₹{Number(basket.basketTotal).toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-gray-400">
              <span>Delivery</span>
              <span className={Number(basket.basketTotal) >= 500 ? 'text-green-400' : ''}>
                {Number(basket.basketTotal) >= 500 ? 'FREE' : '₹50.00'}
              </span>
            </div>
            <div className="border-t border-gray-700 pt-2 flex justify-between font-bold text-white text-base">
              <span>Total</span>
              <span>₹{(Number(basket.basketTotal) + (Number(basket.basketTotal) >= 500 ? 0 : 50)).toFixed(2)}</span>
            </div>
          </div>
          {user
            ? <button onClick={() => navigate('/checkout')} className="btn-primary w-full text-center">Proceed to Checkout</button>
            : <Link to="/login" className="btn-primary w-full text-center block">Sign in to Checkout</Link>
          }
          <Link to="/books" className="btn-outline w-full text-center block text-sm">Continue Shopping</Link>
        </div>
      </div>
    </div>
  );
}
