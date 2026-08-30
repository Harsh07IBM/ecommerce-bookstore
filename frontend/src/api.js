const BASE = 'http://localhost:8080/api';

const getToken = () => localStorage.getItem('token');

const headers = (json = true) => {
  const h = {};
  if (json) h['Content-Type'] = 'application/json';
  const t = getToken();
  if (t) h['Authorization'] = `Bearer ${t}`;
  return h;
};

// credentials:'include' sends the session cookie on every request so the
// guest basket is consistently identified across GET and POST calls.
const req = (url, opts = {}) =>
  fetch(url, { credentials: 'include', ...opts }).then(handle);

const handle = async (res) => {
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `HTTP ${res.status}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
};

// Books
export const getBooks    = (params = '') => req(`${BASE}/books?${params}`, { headers: headers(false) });
export const getBook     = (id)          => req(`${BASE}/books/${id}`,     { headers: headers(false) });
export const getRelated  = (id)          => req(`${BASE}/books/${id}/related`, { headers: headers(false) });
export const getCategories = ()          => req(`${BASE}/categories`,      { headers: headers(false) });

// Auth
export const register = (body) => req(`${BASE}/auth/register`, { method: 'POST', headers: headers(), body: JSON.stringify(body) });
export const login    = (body) => req(`${BASE}/auth/login`,    { method: 'POST', headers: headers(), body: JSON.stringify(body) });

// Basket — credentials included so guest session cookie is sent on every call
export const getBasket    = ()         => req(`${BASE}/basket`,              { headers: headers(false) });
export const addToBasket  = (body)     => req(`${BASE}/basket/items`,        { method: 'POST',   headers: headers(), body: JSON.stringify(body) });
export const updateItem   = (id, body) => req(`${BASE}/basket/items/${id}`,  { method: 'PUT',    headers: headers(), body: JSON.stringify(body) });
export const removeItem   = (id)       => req(`${BASE}/basket/items/${id}`,  { method: 'DELETE', headers: headers(false) });
export const clearBasket  = ()         => req(`${BASE}/basket`,              { method: 'DELETE', headers: headers(false) });

// Addresses
export const getAddresses = ()     => fetch(`${BASE}/addresses`,     { headers: headers(false) }).then(handle);
export const addAddress   = (body) => fetch(`${BASE}/addresses`,     { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(handle);
export const deleteAddress = (id)  => fetch(`${BASE}/addresses/${id}`, { method: 'DELETE', headers: headers(false) }).then(handle);

// Checkout
export const getCheckout = (addressId) => fetch(`${BASE}/checkout/summary?addressId=${addressId}`, { headers: headers(false) }).then(handle);

// Orders
export const placeOrder      = (body)  => fetch(`${BASE}/orders`,                    { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(handle);
export const getOrders       = ()      => fetch(`${BASE}/orders`,                    { headers: headers(false) }).then(handle);
export const getOrder        = (id)    => fetch(`${BASE}/orders/${id}`,              { headers: headers(false) }).then(handle);
export const getConfirmation = (id)    => fetch(`${BASE}/orders/${id}/confirmation`, { headers: headers(false) }).then(handle);
export const cancelOrder     = (id)    => fetch(`${BASE}/orders/${id}/cancel`,       { method: 'POST', headers: headers(false) }).then(handle);
export const buyAgain        = (id)    => fetch(`${BASE}/orders/${id}/buy-again`,    { method: 'POST', headers: headers(false) }).then(handle);

// Gift points
export const getGiftPoints   = ()      => fetch(`${BASE}/users/me/gift-points`,      { headers: headers(false) }).then(handle);

// Recommendations
export const getRecommendations = ()   => fetch(`${BASE}/recommendations`,           { headers: headers(false) }).then(handle);
