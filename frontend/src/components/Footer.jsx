export default function Footer() {
  return (
    <footer className="mt-24 border-t border-gray-800 bg-gray-950">
      <div className="max-w-7xl mx-auto px-4 py-12 grid grid-cols-1 sm:grid-cols-3 gap-8 text-sm text-gray-500">
        <div>
          <p className="font-serif font-bold text-white text-lg mb-2">📚 Ink&amp;Pages</p>
          <p>Your premium destination for discovering and purchasing great books.</p>
        </div>
        <div>
          <p className="font-semibold text-gray-300 mb-2">Explore</p>
          <ul className="space-y-1">
            <li><a href="/books" className="hover:text-brand-400 transition-colors">All Books</a></li>
            <li><a href="/books?category=fiction" className="hover:text-brand-400 transition-colors">Fiction</a></li>
            <li><a href="/books?category=technology" className="hover:text-brand-400 transition-colors">Technology</a></li>
          </ul>
        </div>
        <div>
          <p className="font-semibold text-gray-300 mb-2">Account</p>
          <ul className="space-y-1">
            <li><a href="/login" className="hover:text-brand-400 transition-colors">Sign In</a></li>
            <li><a href="/register" className="hover:text-brand-400 transition-colors">Create Account</a></li>
            <li><a href="/orders" className="hover:text-brand-400 transition-colors">My Orders</a></li>
          </ul>
        </div>
      </div>
      <div className="border-t border-gray-800 py-4 text-center text-xs text-gray-600">
        © {new Date().getFullYear()} Ink&amp;Pages. Built with Spring Boot + React.
      </div>
    </footer>
  );
}
