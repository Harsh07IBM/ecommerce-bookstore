export default function Spinner({ size = 'md' }) {
  const s = { sm: 'w-5 h-5', md: 'w-8 h-8', lg: 'w-12 h-12' }[size];
  return (
    <div className={`${s} border-2 border-gray-700 border-t-brand-500 rounded-full animate-spin`} />
  );
}
