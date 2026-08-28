import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getBook, getRelated } from '../api';
import { useBasket } from '../context/BasketContext';
import { useAuth } from '../context/AuthContext';
import BookCard from '../components/BookCard';
import Spinner from '../components/Spinner';
import toast from 'react-hot-toast';

export default function BookDetail() {
  const { id } = useParams();
  const [book, setBook] = useState(null);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const { add } = useBasket();
  const { user } = useAuth();

  useEffect(() => {
    window.scrollTo(0, 0);
    setLoading(true);
    Promise.all([getBook(id), getRelated(id)])
      .then(([b, r]) => { setBook(b); setRelated(r || []); })
      .finally(() => setLoading(false));
  }, [id]);

  const handleAdd = async () => {
    if (!book) return;
    setAdding(true);
    try {
      await add(book.id, qty);
      toast.success(`"${book.title}" added to basket!`);
    } catch (e) {
      toast.error(e.message);
    } finally { setAdding(false); }
  };

  if (loading) return <div className="flex justify-center py-32"><Spinner size="lg" /></div>;
  if (!book) return <div className="text-center py-32 text-gray-500">Book not found.</div>;

  const inStock = book.availability === 'IN_STOCK';

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
      {/* Breadcrumb */}
      <nav className="text-sm text-gray-500 mb-8 flex items-center gap-2">
        <Link to="/" className="hover:text-white transition-colors">Home</Link>
        <span>/</span>
        <Link to="/books" className="hover:text-white transition-colors">Books</Link>
        <span>/</span>
        <span className="text-gray-300 truncate max-w-xs">{book.title}</span>
      </nav>

      <div className="grid md:grid-cols-[300px_1fr] gap-12">
        {/* Cover */}
        <div className="space-y-4">
          <div className="rounded-2xl overflow-hidden shadow-2xl shadow-black/50 bg-gray-900">
            {book.coverImageUrl
              ? <img src={book.coverImageUrl} alt={book.title} className="w-full object-cover" />
              : <div className="w-full h-80 flex items-center justify-center text-6xl bg-gradient-to-br from-brand-900 to-gray-900">📖</div>
            }
          </div>
          {/* Add to basket */}
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-3xl font-bold text-white">₹{Number(book.price).toFixed(2)}</span>
              <span className={`badge text-sm px-3 py-1 ${inStock ? 'bg-green-900/60 text-green-400' : 'bg-red-900/60 text-red-400'}`}>
                {inStock ? 'In Stock' : 'Out of Stock'}
              </span>
            </div>
            {inStock && (
              <>
                <div className="flex items-center gap-3">
                  <label className="text-sm text-gray-400">Qty</label>
                  <div className="flex items-center gap-2 bg-gray-800 rounded-lg px-3 py-1.5">
                    <button onClick={() => setQty(q => Math.max(1, q - 1))} className="text-gray-400 hover:text-white w-5 text-center">−</button>
                    <span className="w-6 text-center font-medium">{qty}</span>
                    <button onClick={() => setQty(q => Math.min(7, q + 1))} className="text-gray-400 hover:text-white w-5 text-center">+</button>
                  </div>
                </div>
                <button onClick={handleAdd} disabled={adding}
                  className="btn-primary w-full text-center disabled:opacity-60">
                  {adding ? 'Adding…' : '🛒 Add to Basket'}
                </button>
              </>
            )}
            {user && (
              <Link to="/basket" className="btn-outline w-full text-center block text-sm">View Basket</Link>
            )}
          </div>
        </div>

        {/* Details */}
        <div className="space-y-6">
          {book.category && (
            <Link to={`/books?category=${book.category.toLowerCase()}`}
              className="badge bg-brand-900 text-brand-300 text-sm px-3 py-1 inline-block">
              {book.category}
            </Link>
          )}
          <h1 className="font-serif text-4xl font-bold text-white leading-tight">{book.title}</h1>
          <p className="text-lg text-gray-400">{(book.authors || []).join(', ')}</p>

          <div className="prose prose-invert max-w-none text-gray-300 leading-relaxed text-sm border-t border-gray-800 pt-6">
            {book.description || 'No description available.'}
          </div>

          {/* Meta */}
          <div className="grid grid-cols-2 gap-4 border-t border-gray-800 pt-6">
            {[
              ['Publisher', book.publisher],
              ['Published', book.publishedDate],
              ['Pages', book.pageCount],
              ['Language', book.language],
              ['ISBN', book.isbn],
            ].filter(([, v]) => v).map(([k, v]) => (
              <div key={k}>
                <p className="text-xs text-gray-500 uppercase tracking-wider">{k}</p>
                <p className="text-sm text-gray-300 mt-0.5">{v}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Related */}
      {related.length > 0 && (
        <section className="mt-16">
          <h2 className="font-serif text-2xl font-bold text-white mb-6">You Might Also Like</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
            {related.map(b => <BookCard key={b.id} book={b} />)}
          </div>
        </section>
      )}
    </div>
  );
}
