import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight } from 'lucide-react';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';
import { SEO } from '../components/SEO';
import { ThemeToggle } from '../components/ThemeToggle';

const repository = 'https://github.com/shantoislamdev/agentic-webview/blob/main/docs';
const documents = [
  ['Getting started', 'getting-started.md'],
  ['Architecture', 'architecture.md'],
  ['Android Views', 'views.md'],
  ['Compose', 'compose.md'],
  ['Agent tools', 'agent-tools.md'],
  ['Koog adapter', 'koog.md'],
  ['Observations', 'observations.md'],
  ['Commands', 'commands.md'],
  ['Lifecycle', 'lifecycle.md'],
  ['Security', 'security.md'],
  ['Frames and Shadow DOM', 'frames-shadow-dom.md'],
  ['Privacy and prompt injection', 'privacy-prompt-injection.md'],
  ['Diagnostics', 'diagnostics.md'],
  ['Testing', 'testing.md'],
  ['Release process', 'release.md'],
];

export default function CanonicalDocs() {
  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO title="Documentation — Agentic WebView" description="Canonical Agentic WebView SDK documentation." path="/documentation" />
      <header className="w-full max-w-[760px] px-4 sm:px-6 h-20 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-3">
          <Logo className="w-8 h-8 rounded-lg" />
          <span className="font-medium">Agentic WebView</span>
        </Link>
        <ThemeToggle />
      </header>
      <main className="max-w-[760px] w-full px-4 sm:px-6 py-10 text-[15px] leading-[1.7]">
        <Link to="/" className="inline-flex items-center text-vp-subtle text-sm mb-10">
          <ArrowLeft size={15} className="mr-2" /> Home
        </Link>
        <h1 className="text-2xl font-medium mb-3">Documentation</h1>
        <p className="text-vp-text mb-10">
          The repository Markdown files are canonical so code examples cannot silently diverge between the website and the SDK.
        </p>
        <div className="grid sm:grid-cols-2 gap-3">
          {documents.map(([label, file]) => (
            <a
              key={file}
              href={`${repository}/${file}`}
              className="border border-vp-border rounded-md px-4 py-3 text-vp-text hover:text-vp-fg hover:border-vp-subtle transition-colors flex items-center justify-between"
            >
              {label}<ArrowUpRight size={14} />
            </a>
          ))}
        </div>
        <Footer className="mt-16 mb-8" />
      </main>
    </div>
  );
}
