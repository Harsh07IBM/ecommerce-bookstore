import { createContext, useContext, useState, useEffect } from 'react';
import { login as apiLogin, register as apiRegister } from '../api';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('user')); } catch { return null; }
  });

  const doLogin = async (email, password) => {
    const data = await apiLogin({ email, password });
    localStorage.setItem('token', data.token);
    // backend returns { token, user: { id, firstName, lastName, email } }
    const u = { email: data.user.email, firstName: data.user.firstName, lastName: data.user.lastName };
    localStorage.setItem('user', JSON.stringify(u));
    setUser(u);
  };

  const doRegister = async (body) => {
    const data = await apiRegister(body);
    // register now returns LoginResponse { token, user: { id, firstName, lastName, email } }
    localStorage.setItem('token', data.token);
    const u = { email: data.user.email, firstName: data.user.firstName, lastName: data.user.lastName };
    localStorage.setItem('user', JSON.stringify(u));
    setUser(u);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  return (
    <AuthCtx.Provider value={{ user, doLogin, doRegister, logout }}>
      {children}
    </AuthCtx.Provider>
  );
}

export const useAuth = () => useContext(AuthCtx);
