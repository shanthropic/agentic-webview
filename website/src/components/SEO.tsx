import React, { useEffect } from 'react';

interface SEOProps {
  title: string;
  description: string;
  path: string;
}

export function SEO({ title, description, path }: SEOProps) {
  useEffect(() => {
    // 1. Update title
    const fullTitle = title === 'Agentic WebView SDK' ? title : `${title} | Agentic WebView SDK`;
    document.title = fullTitle;

    // Helper to set/update meta tag
    const updateMetaTag = (attributeName: string, attributeValue: string, content: string) => {
      let element = document.querySelector(`meta[${attributeName}="${attributeValue}"]`);
      if (element) {
        element.setAttribute('content', content);
      } else {
        element = document.createElement('meta');
        element.setAttribute(attributeName, attributeValue);
        element.setAttribute('content', content);
        document.head.appendChild(element);
      }
    };

    // Helper to set/update link tag
    const updateLinkTag = (rel: string, href: string) => {
      let element = document.querySelector(`link[rel="${rel}"]`);
      if (element) {
        element.setAttribute('href', href);
      } else {
        element = document.createElement('link');
        element.setAttribute('rel', rel);
        element.setAttribute('href', href);
        document.head.appendChild(element);
      }
    };

    // Normalize path to ensure leading slash
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    const fullUrl = `https://awv.shantoislam.dev${normalizedPath}`;

    // 2. Update description meta tag
    updateMetaTag('name', 'description', description);

    // 3. Update canonical link
    updateLinkTag('canonical', fullUrl);

    // 4. Update Open Graph tags
    updateMetaTag('property', 'og:title', fullTitle);
    updateMetaTag('property', 'og:description', description);
    updateMetaTag('property', 'og:url', fullUrl);
    updateMetaTag('property', 'og:type', 'website');
    updateMetaTag('property', 'og:image', 'https://awv.shantoislam.dev/og-image.png');

    // 5. Update Twitter Card tags
    updateMetaTag('name', 'twitter:title', fullTitle);
    updateMetaTag('name', 'twitter:description', description);
    updateMetaTag('name', 'twitter:card', 'summary_large_image');
    updateMetaTag('name', 'twitter:image', 'https://awv.shantoislam.dev/og-image.png');

  }, [title, description, path]);

  return null; // This component does not render visual UI
}
