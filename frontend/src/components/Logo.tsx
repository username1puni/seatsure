export default function Logo({ size = 28 }: { size?: number }) {
  return (
    <span className="logo">
      <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
        <rect width="64" height="64" rx="14" fill="#e11d48" />
        <path d="M18 30a6 6 0 0 1 6-6h16a6 6 0 0 1 6 6v8H18z" fill="#fff" />
        <rect x="14" y="34" width="8" height="14" rx="3" fill="#fff" />
        <rect x="42" y="34" width="8" height="14" rx="3" fill="#fff" />
        <rect x="20" y="38" width="24" height="6" rx="2" fill="#fff" />
      </svg>
      <span className="logo-text">Seat<b>Sure</b></span>
    </span>
  )
}
