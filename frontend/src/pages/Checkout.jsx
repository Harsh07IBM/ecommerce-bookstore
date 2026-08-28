import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAddresses, addAddress, deleteAddress, getCheckout, placeOrder, getGiftPoints } from '../api';
import { useBasket } from '../context/BasketContext';
import Spinner from '../components/Spinner';
import toast from 'react-hot-toast';

const BLANK = { recipientName: '', phoneNumber: '', line1: '', line2: '', city: '', state: '', pincode: '' };

export default function Checkout() {
  const navigate = useNavigate();
  const { refresh } = useBasket();
  const [step, setStep] = useState(1); // 1=address 2=payment
  const [addresses, setAddresses] = useState([]);
  const [selectedAddr, setSelectedAddr] = useState(null);
  const [summary, setSummary] = useState(null);
  const [giftPoints, setGiftPoints] = useState(0);
  const [redeemPoints, setRedeemPoints] = useState(0);
  const [card, setCard] = useState({ cardNumber: '', expiryMonth: '', expiryYear: '', cvv: '', cardholderName: '' });
  const [showNew, setShowNew] = useState(false);
  const [newAddr, setNewAddr] = useState(BLANK);
  const [placing, setPlacing] = useState(false);

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
    try {
      const saved = await addAddress(newAddr);
      setAddresses(a => [...a, saved]);
      setSelectedAddr(saved.id);
      setShowNew(false);
      setNewAddr(BLANK);
      toast.success('Address saved');
    } catch (e) { toast.error(e.message); }
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
    } finally { setPlacing(false); }
  };

  const fmt = (v) => `₹${Number(v).toFixed(2)}`;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 animate-fade-in">
      <h1 className="font-serif text-3xl font-bold text-white mb-8">Checkout</h1>

      {/* Steps */}
      <div className="flex items-center gap-4 mb-8">
        {['Delivery Address', 'Payment'].map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold ${step > i + 1 ? 'bg-green-600 text-white' : step === i + 1 ? 'bg-brand-600 text-white' : 'bg-gray-800 text-gray-500'}`}>
              {step > i + 1 ? '✓' : i + 1}
            </div>
            <span className={`text-sm font-medium ${step === i + 1 ? 'text-white' : 'text-gray-500'}`}>{s}</span>
            {i === 0 && <div className="w-12 h-0.5 bg-gray-700 mx-1" />}
          </div>
        ))}
      </div>

      <div className="grid lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2">
          {/* Step 1: Address */}
          {step === 1 && (
            <div className="space-y-4 animate-slide-up">
              <h2 className="font-semibold text-white text-lg">Select Delivery Address</h2>
              {addresses.map(a => (
                <label key={a.id} className={`card flex gap-4 p-4 cursor-pointer ${selectedAddr === a.id ? 'border-brand-500' : ''}`}>
                  <input type="radio" className="mt-1 accent-brand-500" checked={selectedAddr === a.id}
                    onChange={() => setSelectedAddr(a.id)} />
                  <div className="text-sm">
                    <p className="font-semibold text-white">{a.recipientName}</p>
                    <p className="text-gray-400">{a.line1}{a.line2 ? `, ${a.line2}` : ''}</p>
                    <p className="text-gray-400">{a.city}, {a.state} — {a.pincode}</p>
                    <p className="text-gray-400">{a.phoneNumber}</p>
                    {a.isDefault && <span className="badge bg-brand-900 text-brand-300 mt-1 inline-block">Default</span>}
                  </div>
                </label>
              ))}

              {!showNew ? (
                <button onClick={() => setShowNew(true)} className="btn-outline w-full text-sm">+ Add New Address</button>
              ) : (
                <form onSubmit={saveAddress} className="card p-5 space-y-3 animate-slide-up">
                  <h3 className="font-medium text-white">New Address</h3>
                  {['recipientName', 'phoneNumber', 'line1', 'line2', 'city', 'state', 'pincode'].map(f => (
                    <input key={f} required={f !== 'line2'} placeholder={f.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
                      value={newAddr[f]} onChange={e => setNewAddr(n => ({ ...n, [f]: e.target.value }))}
                      className="input text-sm" />
                  ))}
                  <div className="flex gap-3">
                    <button type="submit" className="btn-primary text-sm">Save Address</button>
                    <button type="button" onClick={() => setShowNew(false)} className="btn-outline text-sm">Cancel</button>
                  </div>
                </form>
              )}

              <button disabled={!selectedAddr} onClick={() => setStep(2)} className="btn-primary w-full disabled:opacity-40">
                Continue to Payment →
              </button>
            </div>
          )}

          {/* Step 2: Payment */}
          {step === 2 && (
            <form onSubmit={handlePlace} className="space-y-4 animate-slide-up">
              <div className="flex items-center gap-3 mb-2">
                <button type="button" onClick={() => setStep(1)} className="text-gray-400 hover:text-white transition-colors text-sm">← Back</button>
                <h2 className="font-semibold text-white text-lg">Payment Details</h2>
              </div>

              <div className="card p-5 space-y-4">
                <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider">Card Information</h3>
                <input required placeholder="Cardholder Name" value={card.cardholderName}
                  onChange={e => setCard(c => ({ ...c, cardholderName: e.target.value }))} className="input" />
                <input required placeholder="Card Number (16 digits)" maxLength={19} value={card.cardNumber}
                  onChange={e => setCard(c => ({ ...c, cardNumber: e.target.value.replace(/[^\d]/g, '').replace(/(.{4})/g, '$1 ').trim() }))}
                  className="input font-mono" />
                <div className="grid grid-cols-3 gap-3">
                  <input required placeholder="MM" maxLength={2} value={card.expiryMonth}
                    onChange={e => setCard(c => ({ ...c, expiryMonth: e.target.value }))} className="input" />
                  <input required placeholder="YYYY" maxLength={4} value={card.expiryYear}
                    onChange={e => setCard(c => ({ ...c, expiryYear: e.target.value }))} className="input" />
                  <input required placeholder="CVV" maxLength={3} type="password" value={card.cvv}
                    onChange={e => setCard(c => ({ ...c, cvv: e.target.value }))} className="input" />
                </div>
              </div>

              {giftPoints > 0 && (
                <div className="card p-5 space-y-2">
                  <p className="text-sm font-semibold text-gray-300">🎁 Gift Points ({giftPoints} available = ₹{giftPoints})</p>
                  <input type="number" min={0} max={giftPoints} value={redeemPoints}
                    onChange={e => setRedeemPoints(Number(e.target.value))}
                    className="input text-sm" placeholder="Points to redeem (0 to skip)" />
                </div>
              )}

              <button type="submit" disabled={placing} className="btn-primary w-full disabled:opacity-60">
                {placing ? 'Processing…' : `Place Order — ${fmt(summary ? (Number(summary.basketTotal) + Number(summary.deliveryCharge) - redeemPoints) : 0)}`}
              </button>
              <p className="text-xs text-gray-600 text-center">Use card number 0000000000000000 to simulate a declined payment.</p>
            </form>
          )}
        </div>

        {/* Summary sidebar */}
        {summary && (
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6 h-fit space-y-3 text-sm">
            <h2 className="font-semibold text-white">Order Summary</h2>
            {summary.items?.map(item => (
              <div key={item.bookId} className="flex justify-between text-gray-400">
                <span className="truncate max-w-[160px]">{item.title} ×{item.quantity}</span>
                <span>{fmt(item.lineTotal)}</span>
              </div>
            ))}
            <div className="border-t border-gray-700 pt-2 space-y-1">
              <div className="flex justify-between text-gray-400"><span>Subtotal</span><span>{fmt(summary.basketTotal)}</span></div>
              <div className="flex justify-between text-gray-400"><span>Delivery</span>
                <span className={Number(summary.deliveryCharge) === 0 ? 'text-green-400' : ''}>{Number(summary.deliveryCharge) === 0 ? 'FREE' : fmt(summary.deliveryCharge)}</span>
              </div>
              {redeemPoints > 0 && <div className="flex justify-between text-brand-300"><span>Gift Points</span><span>-₹{redeemPoints}</span></div>}
              <div className="flex justify-between font-bold text-white pt-1 border-t border-gray-700">
                <span>Total</span>
                <span>{fmt(Number(summary.basketTotal) + Number(summary.deliveryCharge) - redeemPoints)}</span>
              </div>
            </div>
            <p className="text-xs text-gray-500 pt-2">📅 Estimated delivery: {summary.estimatedDeliveryDate}</p>
          </div>
        )}
      </div>
    </div>
  );
}
