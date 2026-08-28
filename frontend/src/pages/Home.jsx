import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getBooks, getCategories } from '../api';
import BookCard from '../components/BookCard';
import Spinner from '../components/Spinner';

const HERO_QUOTES = [
  '"A reader lives a thousand lives before he dies."',
  '"Not all those who wander are lost."',
  '"It is a truth universally acknowledged…"',
];

export default function Home() {
  const [featured, setFeatured] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [quote] = useState(HERO_QUOTES[Math.floor(Math.random() * HERO_QUOTES.length)]);

  useEffect(() => {
    Promise.all([
      getBooks('size=8&page=0'),
      getCategories(),
    ]).then(([b, c]) => {
      setFeatured(b.content || []);
      setCategories(c || []);
    }).finally(() => setLoading(false));
  }, []);

  return (
    <div className="animate-fade-in">
      {/* ── HERO ─────────────────────────────────────── */}
      <section className="relative overflow-hidden bg-gradient-to-br from-gray-950 via-brand-900/20 to-gray-950 min-h-[520px] flex items-center">
        {/* ambient glow */}
        <div className="absolute -top-40 -right-40 w-[600px] h-[600px] bg-brand-600/20 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-20 -left-20 w-[400px] h-[400px] bg-brand-800/15 rounded-full blur-3xl pointer-events-none" />

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24 grid md:grid-cols-2 gap-12 items-center">
          <div className="space-y-6 animate-slide-up">
            <span className="badge bg-brand-900 text-brand-300 text-sm px-3 py-1">✨ 10,000+ titles in stock</span>
            <h1 className="font-serif text-5xl md:text-6xl font-bold text-white leading-tight">
              Find Your Next<br /><span className="text-brand-400">Great Read</span>
            </h1>
            <p className="text-gray-400 text-lg max-w-md leading-relaxed">{quote}</p>
            <div className="flex gap-4 flex-wrap">
              <Link to="/books" className="btn-primary text-base px-7 py-3">Browse Books</Link>
              <Link to="/register" className="btn-outline text-base px-7 py-3">Join for Free</Link>
            </div>
          </div>
          {/* Floating book covers mosaic */}
          <div className="hidden md:grid grid-cols-3 gap-3 opacity-90">
            {featured.slice(0, 6).map((b, i) => (
              <Link key={b.id} to={`/books/${b.id}`}
                className="rounded-xl overflow-hidden shadow-2xl hover:scale-105 transition-transform duration-300"
                style={{ animationDelay: `${i * 80}ms` }}>
                {b.coverImageUrl
                  ? <img src={b.coverImageUrl} alt={b.title} className="w-full h-36 object-cover" />
                  : <div className="w-full h-36 bg-gradient-to-br from-brand-800 to-gray-800 flex items-center justify-center text-3xl">📖</div>
                }
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ── STATS BAR ────────────────────────────────── */}
      <section className="bg-gray-900 border-y border-gray-800 py-6">
        <div className="max-w-7xl mx-auto px-4 grid grid-cols-2 md:grid-cols-4 gap-6 text-center">
          {[['10,000+', 'Books'], ['50+', 'Categories'], ['Free Delivery', '₹500+'], ['24h', 'Support']].map(([v, l]) => (
            <div key={l}>
              <p className="text-2xl font-bold text-brand-400">{v}</p>
              <p className="text-sm text-gray-500">{l}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── CATEGORIES ───────────────────────────────── */}
      {categories.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <h2 className="font-serif text-3xl font-bold text-white mb-8">Browse by Category</h2>
          <div className="flex flex-wrap gap-3">
            {categories.map(c => (
              <Link key={c.id} to={`/books?category=${c.slug}`}
                className="px-5 py-2.5 bg-gray-800 hover:bg-brand-800 border border-gray-700 hover:border-brand-600 rounded-full text-sm font-medium text-gray-300 hover:text-white transition-all duration-200">
                {c.name}
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── FEATURED ─────────────────────────────────── */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-16">
        <div className="flex items-center justify-between mb-8">
          <h2 className="font-serif text-3xl font-bold text-white">New Arrivals</h2>
          <Link to="/books" className="text-sm text-brand-400 hover:text-brand-300 transition-colors">View all →</Link>
        </div>
        {loading ? (
          <div className="flex justify-center py-16"><Spinner size="lg" /></div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-5">
            {featured.map(b => <BookCard key={b.id} book={b} />)}
          </div>
        )}
      </section>

      {/* ── CTA BANNER ───────────────────────────────── */}
      <section className="bg-gradient-to-r from-brand-900 via-brand-800 to-brand-900 py-16 text-center px-4">
        <h2 className="font-serif text-3xl font-bold text-white mb-3">Earn Gift Points on Every Purchase</h2>
        <p className="text-brand-200 mb-6 max-w-lg mx-auto">Get 5% of your order value back as points. Redeem them on your next order.</p>
        <Link to="/register" className="btn-primary text-base px-8 py-3 inline-block">Get Started Free</Link>
      </section>
    </div>
  );
}
