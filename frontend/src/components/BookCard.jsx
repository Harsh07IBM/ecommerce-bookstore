import { Link } from 'react-router-dom';

const stars = (n) => '★'.repeat(Math.max(0, Math.min(5, n)));

export default function BookCard({ book }) {
  const inStock = book.availability === 'IN_STOCK';
  const rating = 3 + ((book.id % 3));   // deterministic fake rating 3-5

  return (
    <Link to={`/books/${book.id}`} className="card group flex flex-col animate-fade-in">
      {/* Cover */}
      <div className="relative bg-gray-800 overflow-hidden" style={{ paddingTop: '140%' }}>
        {book.coverImageUrl ? (
          <img
            src={book.coverImageUrl}
            alt={book.title}
            className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            loading="lazy"
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-5xl bg-gradient-to-br from-brand-900 to-gray-900">
            📖
          </div>
        )}
        {!inStock && (
          <div className="absolute inset-0 bg-gray-950/70 flex items-center justify-center">
            <span className="badge bg-red-900/80 text-red-300 text-sm px-3 py-1">Out of Stock</span>
          </div>
        )}
        {/* Category pill */}
        {book.category && (
          <span className="absolute top-2 left-2 badge bg-brand-900/80 text-brand-300 backdrop-blur-sm">
            {book.category}
          </span>
        )}
      </div>

      {/* Info */}
      <div className="p-4 flex flex-col gap-1 flex-1">
        <h3 className="font-semibold text-gray-100 line-clamp-2 text-sm leading-snug group-hover:text-brand-300 transition-colors">
          {book.title}
        </h3>
        <p className="text-xs text-gray-500 truncate">{(book.authors || []).join(', ')}</p>
        <div className="text-amber-400 text-xs tracking-wider mt-0.5">{stars(rating)}</div>
        <div className="flex items-center justify-between mt-auto pt-2">
          <span className="font-bold text-white text-sm">₹{Number(book.price).toFixed(2)}</span>
          <span className={`badge text-xs ${inStock ? 'bg-green-900/60 text-green-400' : 'bg-red-900/60 text-red-400'}`}>
            {inStock ? 'In Stock' : 'Sold Out'}
          </span>
        </div>
      </div>
    </Link>
  );
}
