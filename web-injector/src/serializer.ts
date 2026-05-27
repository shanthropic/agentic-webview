import { AccessibilityNode } from './domParser';

const DEFAULT_INCLUDE_ATTRIBUTES = [
    'title', 'type', 'checked', 'name', 'role', 'value', 'placeholder',
    'data-date-format', 'data-state', 'alt', 'aria-checked', 'aria-label',
    'aria-expanded', 'href',
];

function capTextLength(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
}

export function serializeTreeToText(
    nodes: AccessibilityNode[],
    includeAttributes: string[] | null = null,
    previousHighlightIndices?: Set<number>,
): string {
    if (!includeAttributes) includeAttributes = DEFAULT_INCLUDE_ATTRIBUTES;
    const lines: string[] = [];

    for (const node of nodes) {
        if (node.highlightIndex === null || node.highlightIndex === undefined) continue;

        const depthStr = '\t'.repeat(0); // flat for now
        const text = node.text || '';

        let attributesHtmlStr: string | null = null;
        const attributesToInclude: Record<string, string> = {};

        for (const [key, value] of Object.entries(node.attributes)) {
            if (includeAttributes.includes(key) && String(value).trim() !== '') {
                attributesToInclude[key] = String(value).trim();
            }
        }

        // Dedup attribute values
        const orderedKeys = includeAttributes.filter(key => key in attributesToInclude);
        if (orderedKeys.length > 1) {
            const keysToRemove = new Set<string>();
            const seenValues: Record<string, string> = {};
            for (const key of orderedKeys) {
                const value = attributesToInclude[key];
                if (value.length > 5) {
                    if (value in seenValues) {
                        keysToRemove.add(key);
                    } else {
                        seenValues[value] = key;
                    }
                }
            }
            for (const key of keysToRemove) delete attributesToInclude[key];
        }

        // Remove role if it matches tag
        if (node.tag && node.tag.toLowerCase() === attributesToInclude.role) {
            delete attributesToInclude.role;
        }

        // Remove attributes that duplicate text
        const attrsToRemoveIfTextMatches = ['aria-label', 'placeholder', 'title'];
        for (const attr of attrsToRemoveIfTextMatches) {
            if (
                attributesToInclude[attr] &&
                attributesToInclude[attr].trim().toLowerCase() === text.trim().toLowerCase()
            ) {
                delete attributesToInclude[attr];
            }
        }

        if (Object.keys(attributesToInclude).length > 0) {
            attributesHtmlStr = Object.entries(attributesToInclude)
                .map(([key, value]) => `${key}=${capTextLength(value, 15)}`)
                .join(' ');
        }

        const isNew = previousHighlightIndices && !previousHighlightIndices.has(node.highlightIndex);
        const highlightIndicator = isNew ? `*[${node.highlightIndex}]` : `[${node.highlightIndex}]`;

        let line = `${depthStr}${highlightIndicator}<${node.tag.toLowerCase()}`;

        if (attributesHtmlStr) {
            line += ` ${attributesHtmlStr}`;
        }

        if (text) {
            const trimmedText = text.trim();
            if (!attributesHtmlStr) line += ' ';
            line += `>${trimmedText}`;
        } else if (!attributesHtmlStr) {
            line += ' ';
        }

        line += ' />';
        lines.push(line);
    }

    return lines.join('\n');
}
