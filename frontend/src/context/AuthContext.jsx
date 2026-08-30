import { createContext, useContext, useState, useRef } from 'react';
import { login as apiLogin, register as apiRegister, clearBasket } from '../api';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('user')); } catch { return null; }
  });

  // Holds a reference to BasketContext's refresh() so AuthContext can
  // trigger a basket reload after login/register without a circular import.
  const basketRefreshRef = useRef(null);
  const registerBasketRefresh = (fn) => { basketRefreshRef.current = fn; };

  // After a guest signs in or registers:
  //   1. Clear the guest session basket on the backend (wipes the cookie-based basket)
  //   2. Reload the basket — now authenticated, backend returns the user's own basket
  const resetGuestBasket = async () => {
    try { await clearBasket(); } catch {}
    if (basketRefreshRef.current) await basketRefreshRef.current();
  };

  const doLogin = async (email, password) => {
    const data = await apiLogin({ email, password });
    localStorage.setItem('token', data.token);
    const u = { email: data.user.email, firstName: data.user.firstName, lastName: data.user.lastName };
    localStorage.setItem('user', JSON.stringify(u));
    setUser(u);
    await resetGuestBasket();
  };

  const doRegister = async (body) => {
    const data = await apiRegister(body);
    localStorage.setItem('token', data.token);
    const u = { email: data.user.email, firstName: data.user.firstName, lastName: data.user.lastName };
    localStorage.setItem('user', JSON.stringify(u));
    setUser(u);
    await resetGuestBasket();
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  return (
    <AuthCtx.Provider value={{ user, doLogin, doRegister, logout, registerBasketRefresh }}>
      {children}
    </AuthCtx.Provider>
  );
}

export const useAuth = () => useContext(AuthCtx);
