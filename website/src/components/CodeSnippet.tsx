import React from 'react';
import { CopyButton } from './CopyButton';

const escapeHtml = (unsafe: string) => {
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;");
}

const highlightCode = (code: string) => {
  let counter = 0;
  const placeholders: Record<string, string> = {};
  
  const save = (match: string, className: string, escape = true) => {
    const key = `__TOK${counter++}__`;
    placeholders[key] = `<span class="${className}">${escape ? escapeHtml(match) : match}</span>`;
    return key;
  };

  let text = code;

  // 1 & 2. Strings and Comments
  text = text.replace(/("[^"\\]*(?:\\.[^"\\]*)*"|\/\/.*)/g, (m) => {
    if (m.startsWith('"')) return save(m, "text-tk-string");
    return save(m, "text-tk-comment");
  });
  
  // 3. Agent Bracket notation: [15]
  text = text.replace(/\[\d+\]/g, (m) => {
    const num = m.substring(1, m.length - 1);
    return `${save("[", "text-tk-bracket")}${save(num, "text-tk-number")}${save("]", "text-tk-bracket")}`;
  });

  // 4. HTML/XML Tags
  text = text.replace(/<\/?([a-zA-Z0-9-]+)/g, (m) => {
    const isClosing = m.startsWith("</");
    const tag = isClosing ? m.substring(2) : m.substring(1);
    return `${save(isClosing ? "</" : "<", "text-tk-punctuation")}${save(tag, "text-tk-tag")}`;
  });
  text = text.replace(/\/?>/g, (m) => save(m, "text-tk-punctuation"));

  // 5. HTML/XML Attributes & Kotlin Named Args before '='
  text = text.replace(/\b([a-zA-Z0-9-]+)(\s*)=/g, (m, p1, p2) => {
    return `${save(p1, "text-tk-attribute")}${p2}${save("=", "text-tk-operator")}`;
  });

  // 6. Keywords
  text = text.replace(/\b(val|var|fun|class|interface|if|else|when|in|is|return|true|false|remember|implementation|dependencies|setOf)\b/g, (m) => save(m, "text-tk-keyword"));
  
  // 7. Classes / Types
  text = text.replace(/\b[A-Z][a-zA-Z0-9_]*\b/g, (m) => save(m, "text-tk-class"));
  
  // 8. Functions (camelCase followed by paren)
  text = text.replace(/\b([a-z][a-zA-Z0-9_]*)(\s*\()/g, (m, p1, p2) => {
    return `${save(p1, "text-tk-function")}${p2}`;
  });
  
  // 9. Numbers
  text = text.replace(/\b\d+(?:\.\d+)?f?\b/g, (m) => save(m, "text-tk-number"));
  
  // 10. Operators and Punctuation (ignoring the already saved placeholders)
  text = text.replace(/([{}()[\].,?+\-*\/])/g, (m) => save(m, "text-tk-punctuation"));
  text = text.replace(/->/g, (m) => save(m, "text-tk-operator"));
  text = text.replace(/=(?!>)/g, (m) => save(m, "text-tk-operator"));

  // 11. Remaining Variables
  text = text.replace(/\b[a-zA-Z_][a-zA-Z0-9_]*\b/g, (m) => save(m, "text-tk-variable"));

  // First gently escape the remaining raw <, >, & characters that didn't match tags
  text = escapeHtml(text);

  // Restore placeholders. Because we escaped the text, `__TOK` might have remained intact.
  let limit = text.length;
  while (text.includes('__TOK') && limit-- > 0) {
    text = text.replace(/__TOK\d+__/g, (m) => placeholders[m] || m);
  }

  return text;
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
