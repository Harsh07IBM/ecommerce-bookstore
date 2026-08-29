import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import * as api from '../api';

const BasketCtx = createContext(null);

export function BasketProvider({ children }) {
  const [basket, setBasket] = useState({ items: [], totalItems: 0, basketTotal: 0 });
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const data = await api.getBasket();
      setBasket(data);
    } catch {}
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const add = async (bookId, quantity = 1) => {
    setLoading(true);
    try {
      const data = await api.addToBasket({ bookId, quantity });
      setBasket(data);
      return data;
    } finally { setLoading(false); }
  };

  const update = async (bookId, quantity) => {
    const data = await api.updateItem(bookId, { quantity });
    setBasket(data);
  };

  const remove = async (bookId) => {
    const data = await api.removeItem(bookId);
    setBasket(data);
  };

  const clear = async () => {
    const data = await api.clearBasket();
    setBasket(data);
  };

  // Called on logout so stale basket items are not visible to the next user/guest
  const reset = () => setBasket({ items: [], totalItems: 0, basketTotal: 0 });

  return (
    <BasketCtx.Provider value={{ basket, loading, refresh, add, update, remove, clear, reset }}>
      {children}
    </BasketCtx.Provider>
  );
}

export const useBasket = () => useContext(BasketCtx);
