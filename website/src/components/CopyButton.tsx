import React, { useState } from 'react';
import { Copy, Check } from 'lucide-react';

export function CopyButton({ text, className = "" }: { text: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <button 
      onClick={handleCopy} 
      className={`p-1.5 text-vp-subtle hover:text-vp-fg transition-colors rounded-md hover:bg-vp-selection/50 ${className}`}
      title="Copy code"
      aria-label="Copy to clipboard"
    >
      {copied ? <Check size={16} strokeWidth={1.5} className="text-vp-highlight" /> : <Copy size={16} strokeWidth={1.5} />}
    </button>
  );
}
