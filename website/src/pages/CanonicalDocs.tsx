import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight } from 'lucide-react';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';
import { SEO } from '../components/SEO';
import { ThemeToggle } from '../components/ThemeToggle';

const repository = 'https://github.com/shanthropic/agentic-webview/blob/main/docs';

interface DocItem {
  title: string;
  file: string;
  description: string;
}

interface DocCategory {
  category: string;
  items: DocItem[];
}

const docCategories: DocCategory[] = [
  {
    category: 'Core & Architecture',
    items: [
      {
        title: 'Getting started',
        file: 'getting-started.md',
        description: 'System requirements (API 28+, Java 17 bytecode, Kotlin 2.0+), 6 Maven artifacts, and end-to-end navigate/observe/click recipe.',
      },
      {
        title: 'Architecture overview',
        file: 'architecture.md',
        description: 'Module boundaries, single-owner session model, protocol bridge, and architectural guarantees.',
      },
      {
        title: 'Configuration cookbook',
        file: 'configuration.md',
        description: 'Comprehensive reference for all 7 sub-configurations with validation and practical recipes.',
      },
      {
        title: 'Testing strategy',
        file: 'testing.md',
        description: 'Unit test fixtures, page runtime tests, and deterministic CI verification gates.',
      },
      {
        title: 'Release process',
        file: 'release.md',
        description: 'Published Maven Central artifacts, artifact byte budgets, and release workflow checklist.',
      },
    ],
  },
  {
    category: 'UI & Host Integration',
    items: [
      {
        title: 'Android Views integration',
        file: 'views.md',
        description: 'AgenticBrowserHost, Activity/Fragment lifecycles, custom BrowserHostDelegate, and View hierarchy embedding.',
      },
      {
        title: 'Jetpack Compose integration',
        file: 'compose.md',
        description: 'rememberAgenticBrowserHost, AgenticBrowserView, reactive StateFlow collection, and Compose recipes.',
      },
      {
        title: 'Sample application guide',
        file: 'samples.md',
        description: 'Architecture and guide for samples/android featuring Offline Simulation Mode and Autonomous Agent Mode.',
      },
    ],
  },
  {
    category: 'Page Interaction & DOM',
    items: [
      {
        title: 'Command reference',
        file: 'commands.md',
        description: 'Typed browser commands, native touch simulation (PointerActionStrategy.NATIVE_PREFERRED vs DOM_ONLY), await, and navigateHistory.',
      },
      {
        title: 'Observation model',
        file: 'observations.md',
        description: 'Semantic DOM extraction, stable ElementRef identity, viewport filtering, and sensitive value redaction.',
      },
      {
        title: 'Frames and Shadow DOM',
        file: 'frames-shadow-dom.md',
        description: 'Nested same-origin iframes, cross-origin security isolation, and open Shadow DOM traversal.',
      },
    ],
  },
  {
    category: 'Agent Frameworks & Tools',
    items: [
      {
        title: 'Framework-neutral agent tools',
        file: 'agent-tools.md',
        description: 'JSON Schema definitions for all 12 browser tools across Minimal, Standard, and Advanced profiles.',
      },
      {
        title: 'Koog agent integration',
        file: 'koog.md',
        description: 'Reflective ToolSet adapter and autonomous agent recipe with JetBrains Koog.',
      },
      {
        title: 'JSON-RPC 2.0 tool server',
        file: 'jsonrpc.md',
        description: 'Transport-neutral JSON-RPC 2.0 tool discovery and execution endpoint for remote or multi-language agent runtimes.',
      },
    ],
  },
  {
    category: 'Security, Diagnostics & Lifecycle',
    items: [
      {
        title: 'Diagnostics & error catalog',
        file: 'diagnostics.md',
        description: 'Catalog of all 23 BrowserError sealed subclasses, Kotlin pattern matching vs JSON agent tool codes, and recovery recipes.',
      },
      {
        title: 'Lifecycle & event streams',
        file: 'lifecycle.md',
        description: 'Detailed reference for the 11 session phases and 11 browser event types, termination semantics, and cancellation.',
      },
      {
        title: 'Security & navigation policies',
        file: 'security.md',
        description: 'Host allowlisting/denylisting, HTTPS enforcement, and privileged request delegation.',
      },
      {
        title: 'Privacy and prompt injection',
        file: 'privacy-prompt-injection.md',
        description: 'Untrusted page isolation, threat model boundaries, and automated DOM redaction.',
      },
    ],
  },
];

export default function CanonicalDocs() {
  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Documentation — Agentic WebView"
        description="Canonical documentation index for the Agentic WebView SDK on Android."
        path="/documentation"
      />
      <header className="w-full max-w-[760px] px-4 sm:px-6 h-20 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-3">
          <Logo className="w-8 h-8 rounded-lg" />
          <span className="font-medium">Agentic WebView</span>
        </Link>
        <ThemeToggle />
      </header>
      <main className="max-w-[760px] w-full px-4 sm:px-6 py-10 text-[15px] leading-[1.7]">
        <Link to="/" className="inline-flex items-center text-vp-subtle text-sm mb-10 hover:text-vp-fg transition-colors">
          <ArrowLeft size={15} className="mr-2" /> Home
        </Link>
        <h1 className="text-2xl font-medium mb-3">Documentation</h1>
        <p className="text-vp-text mb-10">
          The repository Markdown files are canonical so code examples cannot silently diverge between the website and the SDK. Every guide is kept strictly up to date with the published API contracts.
        </p>

        <div className="space-y-10">
          {docCategories.map((category) => (
            <section key={category.category}>
              <h2 className="text-base font-medium mb-4 text-vp-fg border-b border-vp-border pb-2">
                {category.category}
              </h2>
              <div className="grid sm:grid-cols-2 gap-3">
                {category.items.map((item) => (
                  <a
                    key={item.file}
                    href={`${repository}/${item.file}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="group border border-vp-border rounded-md p-4 text-vp-text hover:text-vp-fg hover:border-vp-subtle transition-colors flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-center justify-between gap-2 font-medium text-vp-fg mb-1.5">
                        <span>{item.title}</span>
                        <ArrowUpRight size={14} className="text-vp-subtle group-hover:text-vp-fg transition-colors flex-shrink-0" />
                      </div>
                      <p className="text-[13px] leading-relaxed text-vp-text">
                        {item.description}
                      </p>
                    </div>
                    <span className="text-[11px] font-mono text-vp-subtle mt-3 block">
                      docs/{item.file}
                    </span>
                  </a>
                ))}
              </div>
            </section>
          ))}
        </div>

        <Footer className="mt-16 mb-8" />
      </main>
    </div>
  );
}
