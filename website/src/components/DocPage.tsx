import React from 'react';
import { useLocation } from 'react-router-dom';
import { SEO } from './SEO';

interface DocPageProps {
  title: string;
  description: React.ReactNode;
  seoDescription?: string;
  children: React.ReactNode;
}

export function DocPage({ title, description, seoDescription, children }: DocPageProps) {
  const location = useLocation();

  const cleanDescription = seoDescription || 
    (typeof description === 'string' ? description : 'Agentic WebView SDK documentation and integration guide.');

  return (
    <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
      <SEO 
        title={title}
        description={cleanDescription}
        path={location.pathname}
      />
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
