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
    '(?<comment>//.*|#.*)',
    '(?<multilineString>"""[\\s\\S]*?""")',
    '(?<string>"(?:[^"\\\\]|\\\\.)*")',
    '(?<bracketNum>\\[\\d+\\])',
    '(?<annotation>@[a-zA-Z0-9_]+)',
    '(?<number>\\b\\d+(?:\\.\\d+)?f?\\b)',
    '(?<keyword>\\b(?:val|var|fun|class|interface|sealed|data|object|package|import|override|suspend|if|else|when|in|is|return|true|false|null|remember|implementation|dependencies|setOf|emptySet)\\b)',
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

export function CodeSnippet({ code, label, className }: { code: string, label?: string, className?: string }) {
  const highlighted = highlightCode(code);

  return (
    <div className={`relative group bg-vp-bg border border-vp-border rounded-sm flex flex-col ${className || 'mb-6 mt-4'}`}>
      <CopyButton text={code} className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity z-10" />
      <div className="p-4 sm:p-5 overflow-x-auto code-scroll">
        {label && <span className="text-[11px] font-medium uppercase tracking-wider text-vp-subtle mb-3 block">{label}</span>}
        <pre 
          className="text-[12px] sm:text-[13px] font-mono text-vp-text flex-1 leading-[1.7]"
          dangerouslySetInnerHTML={{ __html: highlighted }}
        />
      </div>
    </div>
  );
}
