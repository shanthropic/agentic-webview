import React from 'react';
import { Copyright } from 'lucide-react';

interface FooterProps {
  className?: string;
}

export function Footer({ className = '' }: FooterProps) {
  return (
    <footer className={`pt-8 text-vp-subtle text-[12px] sm:text-[13px] flex items-center justify-between gap-2 border-t border-vp-border ${className}`}>
      <span className="flex items-center gap-1.5"><Copyright size={14} /> {new Date().getFullYear()}</span>
      <span>Built with 🖤 by <a href="https://shantoislam.dev" target="_blank" rel="noopener noreferrer" className="font-medium text-vp-fg transition-colors border-b border-transparent hover:border-vp-fg active:border-vp-fg">Shanto Islam</a></span>
    </footer>
  );
}
