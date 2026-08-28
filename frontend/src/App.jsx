import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import { BasketProvider } from './context/BasketContext';
import Navbar from './components/Navbar';
import Footer from './components/Footer';

import Home             from './pages/Home';
import Books            from './pages/Books';
import BookDetail       from './pages/BookDetail';
import Basket           from './pages/Basket';
import Checkout         from './pages/Checkout';
import OrderConfirmation from './pages/OrderConfirmation';
import Orders           from './pages/Orders';
import Login            from './pages/Login';
import Register         from './pages/Register';

function RequireAuth({ children }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

function AppRoutes() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1">
        <Routes>
          <Route path="/"             element={<Home />} />
          <Route path="/books"        element={<Books />} />
          <Route path="/books/:id"    element={<BookDetail />} />
          <Route path="/basket"       element={<Basket />} />
          <Route path="/login"        element={<Login />} />
          <Route path="/register"     element={<Register />} />
          <Route path="/checkout"     element={<RequireAuth><Checkout /></RequireAuth>} />
          <Route path="/orders"       element={<RequireAuth><Orders /></RequireAuth>} />
          <Route path="/orders/:id/confirmation" element={<RequireAuth><OrderConfirmation /></RequireAuth>} />
          <Route path="*"             element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <BasketProvider>
          <AppRoutes />
          <Toaster
            position="top-right"
            toastOptions={{
              style: { background: '#1f2937', color: '#f3f4f6', border: '1px solid #374151' },
              success: { iconTheme: { primary: '#a72ecb', secondary: '#fff' } },
            }}
          />
        </BasketProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
