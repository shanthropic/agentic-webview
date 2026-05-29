import React from 'react';

export function Logo({ className = '' }: { className?: string }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" className={className}>
      <rect x="0" y="0" width="1024" height="1024" rx="192" fill="var(--vp-bg)" />
      <g transform="translate(192, 192) scale(1.25)">
        <rect x="48" y="48" width="416" height="416" rx="48" stroke="var(--vp-fg)" strokeWidth="32" fill="none" />
        <circle cx="112" cy="112" r="16" fill="var(--vp-fg)" />
        <circle cx="176" cy="112" r="16" fill="var(--vp-fg)" />
        <g stroke="var(--vp-fg)" strokeWidth="36" fill="none" strokeLinecap="round" strokeLinejoin="round">
          <path d="M 112 240 C 112 304, 144 368, 176 368 C 208 368, 224 160, 256 160 C 288 160, 304 368, 336 368 C 368 368, 400 304, 400 240" />
          <line x1="220" y1="264" x2="292" y2="264" />
        </g>
      </g>
    </svg>
  );
}
