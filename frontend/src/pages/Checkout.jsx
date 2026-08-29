import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAddresses, addAddress, getCheckout, placeOrder, getGiftPoints } from '../api';
import { useBasket } from '../context/BasketContext';
import Spinner from '../components/Spinner';
import toast from 'react-hot-toast';

const BLANK = { recipientName: '', phoneNumber: '', line1: '', line2: '', city: '', state: '', pincode: '' };

const FIELD_LABELS = {
  recipientName: 'Recipient Name',
  phoneNumber: 'Phone Number',
  line1: 'Address Line 1',
  line2: 'Address Line 2 (optional)',
  city: 'City',
  state: 'State',
  pincode: 'Pincode',
};

export default function Checkout() {
  const navigate = useNavigate();
  const { refresh } = useBasket();
  const [step, setStep] = useState(1);
  const [addresses, setAddresses] = useState([]);
  const [selectedAddr, setSelectedAddr] = useState(null);
  const [summary, setSummary] = useState(null);
  const [giftPoints, setGiftPoints] = useState(0);
  const [redeemPoints, setRedeemPoints] = useState(0);
  const [card, setCard] = useState({ cardNumber: '', expiryMonth: '', expiryYear: '', cvv: '', cardholderName: '' });
  const [showNew, setShowNew] = useState(false);
  const [newAddr, setNewAddr] = useState(BLANK);
  const [placing, setPlacing] = useState(false);
  const [savingAddr, setSavingAddr] = useState(false);

  useEffect(() => {
    Promise.all([getAddresses(), getGiftPoints()]).then(([a, g]) => {
      setAddresses(a || []);
      setGiftPoints(g?.balance || 0);
      const def = (a || []).find(x => x.isDefault) || (a || [])[0];
      if (def) setSelectedAddr(def.id);
    }).catch(() => navigate('/login'));
  }, [navigate]);

  useEffect(() => {
    if (selectedAddr) {
      getCheckout(selectedAddr).then(setSummary).catch(() => {});
    }
  }, [selectedAddr]);

  const saveAddress = async (e) => {
    e.preventDefault();
    setSavingAddr(true);
    try {
      const saved = await addAddress(newAddr);
      setAddresses(a => [...a, saved]);
      setSelectedAddr(saved.id);
      setShowNew(false);
      setNewAddr(BLANK);
      toast.success('Address saved');
    } catch (e) {
      toast.error(e.message);
    } finally {
      setSavingAddr(false);
    }
  };

  const handlePlace = async (e) => {
    e.preventDefault();
    setPlacing(true);
    try {
      const order = await placeOrder({
        addressId: selectedAddr,
        cardNumber: card.cardNumber.replace(/\s/g, ''),
        expiryMonth: Number(card.expiryMonth),
        expiryYear: Number(card.expiryYear),
        cvv: card.cvv,
        cardholderName: card.cardholderName,
        giftPointsToRedeem: Math.min(redeemPoints, giftPoints),
      });
      await refresh();
      navigate(`/orders/${order.orderId}/confirmation`);
    } catch (e) {
      toast.error(e.message);
    } finally {
      setPlacing(false);
    }
  };

  const fmt = (v) => `₹${Number(v).toFixed(2)}`;
  const orderTotal = summary
    ? Math.max(0, Number(summary.basketTotal) + Number(summary.deliveryCharge) - Math.min(redeemPoints, giftPoints))
    : 0;

  return (
    <div className="min-h-screen bg-gray-950">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">

        {/* Page title */}
        <h1 className="font-serif text-3xl font-bold text-white mb-8">Checkout</h1>

        {/* Step indicator */}
        <div className="flex items-center gap-3 mb-10">
          {['Delivery Address', 'Payment'].map((label, i) => {
            const n = i + 1;
            const done = step > n;
            const active = step === n;
            return (
              <div key={label} className="flex items-center gap-2">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold transition-colors
                  ${done ? 'bg-green-600 text-white' : active ? 'bg-brand-600 text-white' : 'bg-gray-800 text-gray-500'}`}>
                  {done
                    ? <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" /></svg>
                    : n}
                </div>
                <span className={`text-sm font-medium ${active ? 'text-white' : 'text-gray-500'}`}>{label}</span>
                {i === 0 && <div className="w-10 h-0.5 bg-gray-700 mx-1" />}
              </div>
            );
          })}
        </div>

        <div className="grid lg:grid-cols-3 gap-8 items-start">

          {/* ── LEFT COLUMN ─────────────────────────── */}
          <div className="lg:col-span-2">

            {/* Step 1 — Address */}
            {step === 1 && (
              <div className="space-y-4 animate-fade-in">
                <h2 className="font-semibold text-white text-lg">Select Delivery Address</h2>

                {addresses.length === 0 && !showNew && (
                  <p className="text-sm text-gray-500 py-2">No saved addresses. Add one below.</p>
                )}

                {addresses.map(a => (
                  <label key={a.id}
                    className={`card flex gap-4 p-4 cursor-pointer transition-colors
                      ${selectedAddr === a.id ? 'border-brand-500 bg-brand-950/10' : 'hover:border-gray-700'}`}>
                    <input type="radio" className="mt-1 accent-brand-500 shrink-0" checked={selectedAddr === a.id}
                      onChange={() => setSelectedAddr(a.id)} />
                    <div className="text-sm min-w-0">
                      <p className="font-semibold text-white">{a.recipientName}</p>
                      <p className="text-gray-400 mt-0.5">{a.line1}{a.line2 ? `, ${a.line2}` : ''}</p>
                      <p className="text-gray-400">{a.city}, {a.state} — {a.pincode}</p>
                      <p className="text-gray-400">{a.phoneNumber}</p>
                      {a.isDefault && (
                        <span className="badge bg-brand-900 text-brand-300 mt-1.5 inline-block">Default</span>
                      )}
                    </div>
                  </label>
                ))}

                {!showNew ? (
                  <button onClick={() => setShowNew(true)}
                    className="btn-outline w-full text-sm flex items-center justify-center gap-2">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                    </svg>
                    Add New Address
                  </button>
                ) : (
                  <form onSubmit={saveAddress}
                    className="bg-gray-900 border border-gray-800 rounded-2xl p-5 space-y-3 animate-fade-in">
                    <h3 className="font-semibold text-white text-sm">New Address</h3>
                    <div className="grid sm:grid-cols-2 gap-3">
                      {Object.keys(BLANK).map(f => (
                        <div key={f} className={f === 'line1' || f === 'line2' ? 'sm:col-span-2' : ''}>
                          <label className="block text-xs text-gray-500 mb-1">{FIELD_LABELS[f]}</label>
                          <input
                            required={f !== 'line2'}
                            placeholder={FIELD_LABELS[f]}
                            value={newAddr[f]}
                            onChange={e => setNewAddr(n => ({ ...n, [f]: e.target.value }))}
                            className="input text-sm"
                          />
                        </div>
                      ))}
                    </div>
                    <div className="flex gap-3 pt-1">
                      <button type="submit" disabled={savingAddr}
                        className="btn-primary text-sm flex items-center gap-2 disabled:opacity-60">
                        {savingAddr && <Spinner size="sm" />}
                        Save Address
                      </button>
                      <button type="button" onClick={() => { setShowNew(false); setNewAddr(BLANK); }}
                        className="btn-outline text-sm">
                        Cancel
                      </button>
                    </div>
                  </form>
                )}

                <button
                  disabled={!selectedAddr}
                  onClick={() => setStep(2)}
                  className="btn-primary w-full py-3 text-base flex items-center justify-center gap-2 disabled:opacity-40 mt-2">
                  Continue to Payment
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>
            )}

            {/* Step 2 — Payment */}
            {step === 2 && (
              <form onSubmit={handlePlace} className="space-y-5 animate-fade-in">
                <div className="flex items-center gap-3">
                  <button type="button" onClick={() => setStep(1)}
                    className="text-gray-400 hover:text-white transition-colors flex items-center gap-1.5 text-sm">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                    </svg>
                    Back
                  </button>
                  <h2 className="font-semibold text-white text-lg">Payment Details</h2>
                </div>

                {/* Card info */}
                <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5 space-y-4">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider flex items-center gap-2">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
                    </svg>
                    Card Information
                  </p>
                  <div>
                    <label className="block text-xs text-gray-500 mb-1">Cardholder Name</label>
                    <input required placeholder="Name on card"
                      value={card.cardholderName}
                      onChange={e => setCard(c => ({ ...c, cardholderName: e.target.value }))}
                      className="input" />
                  </div>
                  <div>
                    <label className="block text-xs text-gray-500 mb-1">Card Number</label>
                    <input required placeholder="0000 0000 0000 0000" maxLength={19}
                      value={card.cardNumber}
                      onChange={e => setCard(c => ({ ...c, cardNumber: e.target.value.replace(/[^\d]/g, '').replace(/(.{4})/g, '$1 ').trim() }))}
                      className="input font-mono tracking-widest" />
                  </div>
                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Month</label>
                      <input required placeholder="MM" maxLength={2}
                        value={card.expiryMonth}
                        onChange={e => setCard(c => ({ ...c, expiryMonth: e.target.value.replace(/\D/g, '') }))}
                        className="input text-center" />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Year</label>
                      <input required placeholder="YYYY" maxLength={4}
                        value={card.expiryYear}
                        onChange={e => setCard(c => ({ ...c, expiryYear: e.target.value.replace(/\D/g, '') }))}
                        className="input text-center" />
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">CVV</label>
                      <input required placeholder="•••" maxLength={3} type="password"
                        value={card.cvv}
                        onChange={e => setCard(c => ({ ...c, cvv: e.target.value.replace(/\D/g, '') }))}
                        className="input text-center" />
                    </div>
                  </div>
                </div>

                {/* Gift points */}
                {giftPoints > 0 && (
                  <div className="bg-gray-900 border border-brand-800/50 rounded-2xl p-5 space-y-3">
                    <div className="flex items-center gap-2">
                      <svg className="w-5 h-5 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V5.5A2.5 2.5 0 109.5 8H12zm-7 4h14M5 12a2 2 0 110-4h14a2 2 0 110 4M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
                      </svg>
                      <p className="text-sm font-semibold text-brand-300">
                        Gift Points — {giftPoints} available (worth ₹{giftPoints})
                      </p>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Points to redeem (0 to skip)</label>
                      <input type="number" min={0} max={giftPoints}
                        value={redeemPoints}
                        onChange={e => setRedeemPoints(Math.min(Number(e.target.value), giftPoints))}
                        className="input text-sm" />
                    </div>
                    {redeemPoints > 0 && (
                      <p className="text-xs text-brand-400">
                        Saving ₹{Math.min(redeemPoints, giftPoints)} on this order
                      </p>
                    )}
                  </div>
                )}

                <button type="submit" disabled={placing}
                  className="btn-primary w-full py-3 text-base flex items-center justify-center gap-2 disabled:opacity-60">
                  {placing
                    ? <><Spinner size="sm" /> Processing…</>
                    : <>Place Order — {fmt(orderTotal)}
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                    </>
                  }
                </button>
                <p className="text-xs text-gray-600 text-center">
                  Use card number <span className="font-mono">0000000000000000</span> to simulate a declined payment.
                </p>
              </form>
            )}
          </div>

          {/* ── ORDER SUMMARY SIDEBAR ─────────────── */}
          {summary && (
            <div className="lg:sticky lg:top-20">
              <div className="bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-800">
                  <h2 className="font-semibold text-white">Order Summary</h2>
                </div>

                {/* Line items */}
                <div className="px-5 py-4 space-y-2 max-h-48 overflow-y-auto">
                  {summary.items?.map(item => (
                    <div key={item.bookId} className="flex justify-between text-sm">
                      <span className="text-gray-400 truncate max-w-[160px]">
                        {item.title}
                        <span className="text-gray-600 ml-1">×{item.quantity}</span>
                      </span>
                      <span className="text-gray-300 ml-2 shrink-0">{fmt(item.lineTotal)}</span>
                    </div>
                  ))}
                </div>

                {/* Totals */}
                <div className="px-5 py-4 border-t border-gray-800 space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-400">Subtotal</span>
                    <span className="text-gray-300">{fmt(summary.basketTotal)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-400">Delivery</span>
                    {Number(summary.deliveryCharge) === 0
                      ? <span className="text-green-400 font-medium">FREE</span>
                      : <span className="text-gray-300">{fmt(summary.deliveryCharge)}</span>
                    }
                  </div>
                  {redeemPoints > 0 && (
                    <div className="flex justify-between">
                      <span className="text-brand-300">Gift Points</span>
                      <span className="text-brand-300">−₹{Math.min(redeemPoints, giftPoints)}</span>
                    </div>
                  )}
                  <div className="border-t border-gray-800 pt-2 flex justify-between font-bold text-white">
                    <span>Total</span>
                    <span>{fmt(orderTotal)}</span>
                  </div>
                </div>

                {/* Estimated delivery */}
                {summary.estimatedDeliveryDate && (
                  <div className="px-5 pb-4 flex items-center gap-2 text-xs text-gray-500">
                    <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                    Est. delivery: {summary.estimatedDeliveryDate}
                  </div>
                )}
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
