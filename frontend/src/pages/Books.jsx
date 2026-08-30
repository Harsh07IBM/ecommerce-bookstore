import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getBooks, getCategories } from '../api';
import BookCard from '../components/BookCard';
import Spinner from '../components/Spinner';

const SORTS = [
  { value: '', label: 'Newest' },
  { value: 'price_asc', label: 'Price: Low → High' },
  { value: 'price_desc', label: 'Price: High → Low' },
];

export default function Books() {
  const [params, setParams] = useSearchParams();
  const [books, setBooks] = useState([]);
  const [meta, setMeta] = useState({});
  const [cats, setCats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);

  const q = params.get('q') || '';
  const category = params.get('category') || '';
  const sort = params.get('sort') || '';
  const availOnly = params.get('available') === '1';

  const load = useCallback(async (pg = 0) => {
    setLoading(true);
    const qp = new URLSearchParams({ page: pg, size: 12 });
    if (q) qp.set('q', q);
    if (category) qp.set('category', category);
    if (sort) qp.set('sort', sort);
    if (availOnly) qp.set('available', true);
    try {
      const data = await getBooks(qp.toString());
      setBooks(data.content || []);
      setMeta(data);
      setPage(pg);
    } catch (e) {
      setBooks([]);
    } finally { setLoading(false); }
  }, [q, category, sort, availOnly]);

  useEffect(() => { load(0); }, [load]);

  useEffect(() => {
    getCategories().then(c => setCats(c || []));
  }, []);

  const set = (key, val) => {
    const p = new URLSearchParams(params);
    if (val) p.set(key, val); else p.delete(key);
    setParams(p);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <h1 className="font-serif text-3xl font-bold text-white">
          {q ? `Results for "${q}"` : category ? `${category}` : 'All Books'}
        </h1>
        <div className="flex gap-3 flex-wrap">
          {/* Sort */}
          <select value={sort} onChange={e => set('sort', e.target.value)}
            className="bg-gray-800 border border-gray-700 rounded-xl px-3 py-2 text-sm text-gray-300 outline-none focus:border-brand-500">
            {SORTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          {/* Available toggle */}
          <button onClick={() => set('available', availOnly ? '' : '1')}
            className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all ${availOnly ? 'bg-brand-600 border-brand-500 text-white' : 'bg-gray-800 border-gray-700 text-gray-400 hover:border-brand-600'}`}>
            In Stock Only
          </button>
        </div>
      </div>

      <div className="flex gap-8">
        {/* Sidebar */}
        <aside className="hidden lg:block w-48 shrink-0">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Category</p>
          <ul className="space-y-1">
            <li>
              <button onClick={() => set('category', '')}
                className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${!category ? 'bg-brand-900 text-brand-300' : 'text-gray-400 hover:text-white hover:bg-gray-800'}`}>
                All
              </button>
            </li>
            {cats.map(c => (
              <li key={c.id}>
                <button onClick={() => set('category', c.slug)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${category === c.slug ? 'bg-brand-900 text-brand-300' : 'text-gray-400 hover:text-white hover:bg-gray-800'}`}>
                  {c.name}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        {/* Grid */}
        <div className="flex-1">
          {loading ? (
            <div className="flex justify-center py-24"><Spinner size="lg" /></div>
          ) : books.length === 0 ? (
            <div className="text-center py-24 text-gray-500">
              <p className="text-4xl mb-3">🔍</p>
              <p className="text-lg">No books found</p>
              <p className="text-sm mt-1">Try adjusting your filters</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-5">
                {books.map(b => <BookCard key={b.id} book={b} />)}
              </div>
              {/* Pagination */}
              {meta.totalPages > 1 && (
                <div className="flex justify-center gap-2 mt-10">
                  <button disabled={page === 0} onClick={() => load(page - 1)}
                    className="btn-outline disabled:opacity-30 px-4 py-2 text-sm">← Prev</button>
                  <span className="flex items-center px-4 text-sm text-gray-400">
                    {page + 1} / {meta.totalPages}
                  </span>
                  <button disabled={page + 1 >= meta.totalPages} onClick={() => load(page + 1)}
                    className="btn-outline disabled:opacity-30 px-4 py-2 text-sm">Next →</button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
