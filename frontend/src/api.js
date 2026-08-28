const BASE = 'http://localhost:8080/api';

const getToken = () => localStorage.getItem('token');

const headers = (json = true) => {
  const h = {};
  if (json) h['Content-Type'] = 'application/json';
  const t = getToken();
  if (t) h['Authorization'] = `Bearer ${t}`;
  return h;
};

const handle = async (res) => {
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `HTTP ${res.status}`);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
};

// Books
export const getBooks    = (params = '') => fetch(`${BASE}/books?${params}`, { headers: headers(false) }).then(handle);
export const getBook     = (id)          => fetch(`${BASE}/books/${id}`,     { headers: headers(false) }).then(handle);
export const getRelated  = (id)          => fetch(`${BASE}/books/${id}/related`, { headers: headers(false) }).then(handle);
export const getCategories = ()          => fetch(`${BASE}/categories`,      { headers: headers(false) }).then(handle);

// Auth
export const register = (body) => fetch(`${BASE}/auth/register`, { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(handle);
export const login    = (body) => fetch(`${BASE}/auth/login`,    { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(handle);

// Basket
export const getBasket    = ()         => fetch(`${BASE}/basket`,                  { headers: headers(false) }).then(handle);
export const addToBasket  = (body)     => fetch(`${BASE}/basket/items`,            { method: 'POST', headers: headers(), body: JSON.stringify(body) }).then(handle);
export const updateItem   = (id, body) => fetch(`${BASE}/basket/items/${id}`,      { method: 'PUT',  headers: headers(), body: JSON.stringify(body) }).then(handle);
export const removeItem   = (id)       => fetch(`${BASE}/basket/items/${id}`,      { method: 'DELETE', headers: headers(false) }).then(handle);
export const clearBasket  = ()         => fetch(`${BASE}/basket`,                  { method: 'DELETE', headers: headers(false) }).then(handle);

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
