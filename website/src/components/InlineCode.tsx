import React from 'react';

interface InlineCodeProps extends React.HTMLAttributes<HTMLElement> {
  children: React.ReactNode;
  className?: string;
}

export function InlineCode({ children, className, ...props }: InlineCodeProps) {
  // Always use text-[12px] as requested. Combine with the default styles.
  const baseClasses = "bg-vp-input px-1.5 py-0.5 rounded border border-vp-border text-[12px]";
  
  return (
    <code className={`${baseClasses} ${className || 'text-vp-fg'}`} {...props}>
      {children}
    </code>
  );
}
