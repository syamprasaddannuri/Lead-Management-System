import React from 'react'

export default function LmsLogo({ height = 30, light = false }: { height?: number; light?: boolean }) {
  return (
    <svg viewBox="0 0 500 120" height={height} style={{ width: 'auto', display: 'block' }} role="img" aria-label="Lead Management System">
      <defs>
        <linearGradient id="fbg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#4F46E5" /><stop offset="1" stopColor="#06B6D4" />
        </linearGradient>
        <linearGradient id="fba" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#06B6D4" /><stop offset="1" stopColor="#3B82F6" />
        </linearGradient>
      </defs>
      <g transform="translate(15,0)">
        <text x="0" y="100" fontFamily="Arial" fontWeight="900" fontStyle="italic" fontSize="110" letterSpacing="-5" fill="url(#fbg)">0</text>
        <text x="65" y="100" fontFamily="Arial" fontWeight="900" fontStyle="italic" fontSize="110" letterSpacing="-5" fill="url(#fbg)">1</text>
        <path d="M 12 92 Q 60 125 115 65 L 105 55 L 138 48 L 128 80 L 118 70 Q 65 118 8 85 Z" fill="url(#fba)" />
      </g>
      <text x="185" y="90" fontFamily="Segoe UI,sans-serif" fontWeight="800" fontSize="68" letterSpacing="-1.5">
        <tspan fill={light ? '#ffffff' : '#111827'}>L</tspan><tspan fill="url(#fbg)">MS</tspan>
      </text>
    </svg>
  )
}
