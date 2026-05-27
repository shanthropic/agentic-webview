const SAFE_ATTRIBUTES = new Set([
    'id', 'name', 'type', 'placeholder', 'aria-label', 'aria-labelledby',
    'aria-describedby', 'role', 'for', 'autocomplete', 'required', 'readonly',
    'alt', 'title', 'src', 'href', 'target',
    'data-id', 'data-qa', 'data-cy', 'data-testid',
]);

export function convertSimpleXPathToCssSelector(xpath: string): string {
    if (!xpath) return '';
    const cleanXpath = xpath.replace(/^\//, '');
    const parts = cleanXpath.split('/');
    const cssParts: string[] = [];

    for (const part of parts) {
        if (!part) continue;

        if (part.includes(':') && !part.includes('[')) {
            cssParts.push(part.replace(/:/g, '\\:'));
            continue;
        }

        if (part.includes('[')) {
            const bracketIndex = part.indexOf('[');
            let basePart = part.substring(0, bracketIndex);
            if (basePart.includes(':')) basePart = basePart.replace(/:/g, '\\:');
            const indexPart = part.substring(bracketIndex);
            const indices = indexPart.split(']').slice(0, -1).map(i => i.replace('[', ''));

            for (const idx of indices) {
                if (/^\d+$/.test(idx)) {
                    const index = parseInt(idx, 10);
                    basePart += `:nth-of-type(${index})`;
                } else if (idx === 'last()') {
                    basePart += ':last-of-type';
                } else if (idx.includes('position()')) {
                    if (idx.includes('>1')) basePart += ':nth-of-type(n+2)';
                }
            }
            cssParts.push(basePart);
        } else {
            cssParts.push(part);
        }
    }

    return cssParts.join(' > ');
}

export function enhancedCssSelectorForElement(
    tagName: string,
    xpath: string | null,
    attributes: Record<string, string>,
    highlightIndex: number | null,
): string {
    try {
        if (!xpath) return '';

        let cssSelector = convertSimpleXPathToCssSelector(xpath);

        // Class names
        const classValue = attributes.class;
        if (classValue) {
            const validClassNamePattern = /^[a-zA-Z_][a-zA-Z0-9_-]*$/;
            const classes = classValue.trim().split(/\s+/);
            for (const className of classes) {
                if (className.trim() && validClassNamePattern.test(className)) {
                    cssSelector += `.${className}`;
                }
            }
        }

        // Safe attributes
        for (const [attribute, value] of Object.entries(attributes)) {
            if (attribute === 'class') continue;
            if (!attribute.trim()) continue;
            if (!SAFE_ATTRIBUTES.has(attribute)) continue;

            const safeAttribute = attribute.replace(':', '\\:');
            if (value === '') {
                cssSelector += `[${safeAttribute}]`;
            } else if (/["'<>`\n\r\t]/.test(value)) {
                const collapsedValue = value.replace(/\s+/g, ' ').trim();
                const safeValue = collapsedValue.replace(/"/g, '\\"');
                cssSelector += `[${safeAttribute}*="${safeValue}"]`;
            } else {
                cssSelector += `[${safeAttribute}="${value}"]`;
            }
        }

        return cssSelector;
    } catch {
        return `${tagName || '*'}[data-agent-id='${highlightIndex}']`;
    }
}
