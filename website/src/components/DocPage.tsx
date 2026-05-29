import React from 'react';

interface DocPageProps {
  title: string;
  description: React.ReactNode;
  children: React.ReactNode;
}

export function DocPage({ title, description, children }: DocPageProps) {
  return (
    <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
      <h1 className="text-3xl font-medium tracking-tight mb-4 text-vp-fg">{title}</h1>
      <p className="text-vp-text mb-12 text-[16px] leading-relaxed">
        {description}
      </p>

      <div className="space-y-16">
        {children}
      </div>
    </div>
  );
}
