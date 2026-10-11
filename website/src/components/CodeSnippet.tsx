import React from 'react';
import { CopyButton } from './CopyButton';

const escapeHtml = (unsafe: string) => {
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;");
}

const highlightCode = (code: string) => {
  const tokenRegex = new RegExp([
    '(?<comment>//.*|#.*|/\\*[\\s\\S]*?\\*/)',
    '(?<multilineString>"""[\\s\\S]*?""")',
    '(?<string>"(?:[^"\\\\]|\\\\.)*"|\'(?:[^\'\\\\]|\\\\.)*\')',
    '(?<bracketNum>\\[\\d+\\])',
    '(?<annotation>@[a-zA-Z0-9_]+)',
    '(?<number>\\b\\d+(?:\\.\\d+)?f?\\b)',
    '(?<keyword>\\b(?:val|var|fun|class|interface|sealed|data|object|package|import|override|suspend|if|else|when|in|is|return|true|false|null|remember|implementation|dependencies|setOf|emptySet|private|lateinit|super|by|as|for|while|try|catch|finally|throw|open|public|protected|internal|const)\\b)',
    '(?<type>\\b[A-Z][a-zA-Z0-9_]*\\b)',
    '(?<function>\\b[a-z][a-zA-Z0-9_]*(?=\\s*\\())',
    '(?<namedArg>\\b[a-zA-Z0-9_-]+(?=\\s*=(?!=)))',
    '(?<variable>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)',
    '(?<operator>->|=>|==|!=|<=|>=|[=+\\-*\\/%&|!<>])',
    '(?<punctuation>[{}()\\[\\].:;?,])',
    '(?<other>\\s+|[^\\s])'
  ].join('|'), 'gy');

  let result = '';
  let match: RegExpExecArray | null;
  while ((match = tokenRegex.exec(code)) !== null) {
    const g = match.groups as Record<string, string | undefined>;
    if (g.comment) {
      result += `<span class="text-tk-comment">${escapeHtml(g.comment)}</span>`;
    } else if (g.multilineString || g.string) {
      result += `<span class="text-tk-string">${escapeHtml(g.multilineString || g.string!)}</span>`;
    } else if (g.bracketNum) {
      const num = g.bracketNum.slice(1, -1);
      result += `<span class="text-tk-bracket">[</span><span class="text-tk-number">${escapeHtml(num)}</span><span class="text-tk-bracket">]</span>`;
    } else if (g.annotation) {
      result += `<span class="text-tk-attribute">${escapeHtml(g.annotation)}</span>`;
    } else if (g.number) {
      result += `<span class="text-tk-number">${escapeHtml(g.number)}</span>`;
    } else if (g.keyword) {
      result += `<span class="text-tk-keyword">${escapeHtml(g.keyword)}</span>`;
    } else if (g.type) {
      result += `<span class="text-tk-class">${escapeHtml(g.type)}</span>`;
    } else if (g.function) {
      result += `<span class="text-tk-function">${escapeHtml(g.function)}</span>`;
    } else if (g.namedArg) {
      result += `<span class="text-tk-attribute">${escapeHtml(g.namedArg)}</span>`;
    } else if (g.variable) {
      result += `<span class="text-tk-variable">${escapeHtml(g.variable)}</span>`;
    } else if (g.operator) {
      result += `<span class="text-tk-operator">${escapeHtml(g.operator)}</span>`;
    } else if (g.punctuation) {
      result += `<span class="text-tk-punctuation">${escapeHtml(g.punctuation)}</span>`;
    } else {
      result += escapeHtml(g.other || match[0]);
    }
  }
  return result;
};

export interface CodeTab {
  label: string;
  code: string;
}

export interface CodeSnippetProps {
  code?: string;
  label?: string;
  tabs?: CodeTab[];
  className?: string;
}

export function CodeSnippet({ code = '', label, tabs, className }: CodeSnippetProps) {
  const [activeTabIdx, setActiveTabIdx] = React.useState(0);

  const activeCode = tabs && tabs.length > 0 ? tabs[activeTabIdx]?.code ?? '' : code;
  const activeLabel = tabs && tabs.length > 0 ? undefined : label;
  const highlighted = highlightCode(activeCode);

  return (
    <div className={`relative group bg-vp-bg border border-vp-border rounded-sm flex flex-col ${className || 'mb-6 mt-4'}`}>
      {tabs && tabs.length > 0 ? (
        <div className="flex items-center justify-between border-b border-vp-border px-3 py-1.5 bg-vp-input/40">
          <div className="flex items-center gap-1.5">
            {tabs.map((tab, idx) => (
              <button
                key={tab.label}
                type="button"
                onClick={() => setActiveTabIdx(idx)}
                className={`px-2.5 py-1 text-[12px] font-mono rounded-sm transition-colors cursor-pointer border ${
                  activeTabIdx === idx
                    ? 'text-vp-fg bg-vp-bg border-vp-border font-medium'
                    : 'text-vp-subtle hover:text-vp-fg border-transparent'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <CopyButton text={activeCode} className="text-vp-subtle hover:text-vp-fg" />
        </div>
      ) : (
        <CopyButton text={activeCode} className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity z-10" />
      )}
      <div className="p-4 sm:p-5 overflow-x-auto code-scroll">
        {activeLabel && <span className="text-[11px] font-medium uppercase tracking-wider text-vp-subtle mb-3 block">{activeLabel}</span>}
        <pre 
          className="text-[12px] sm:text-[13px] font-mono text-vp-text flex-1 leading-[1.7]"
          dangerouslySetInnerHTML={{ __html: highlighted }}
        />
      </div>
    </div>
  );
}
