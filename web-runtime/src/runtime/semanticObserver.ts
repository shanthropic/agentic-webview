import { ElementRegistry } from './elementRegistry';
import { FrameRegistry, RuntimeFrame } from './frameRegistry';
import { DocumentRevisionTracker } from './revisionTracker';

export interface SemanticObservationOptions {
    maximumVisitedNodes: number;
    maximumEmittedNodes: number;
    maximumTotalTextCharacters: number;
    maximumTextCharactersPerNode: number;
    maximumTraversalMs: number;
    viewportExpansionPx: number;
    maximumFrameDepth: number;
    maximumShadowDepth: number;
    includeCompactText: boolean;
}

export interface SemanticObservation {
    id: string;
    capturedAtEpochMs: number;
    contentTrust: 'UNTRUSTED_WEBPAGE';
    document: { id: string; url: string; title: string; phase: 'READY' };
    revision: number;
    viewport: Record<string, number>;
    frames: SemanticFrame[];
    nodes: SemanticPageNode[];
    compactText: string;
    screenshot: null;
    truncation: { reason: string; limit: number; observed: number } | null;
    warnings: Array<{ code: string; message: string }>;
    metrics: {
        durationMs: number;
        visitedNodeCount: number;
        emittedNodeCount: number;
        textCharacterCount: number;
        encodedByteCount: number;
    };
}

interface SemanticFrame {
    id: string;
    parentId: string | null;
    documentId: string;
    url: string | null;
    origin: string | null;
    depth: number;
    capability: string;
}

interface SemanticPageNode {
    nodeId: string;
    parentNodeId: string | null;
    frameId: string;
    depth: number;
    kind: string;
    tagName: string | null;
    role: string | null;
    text: string | null;
    accessibleName: string | null;
    accessibleDescription: string | null;
    attributes: Record<string, string>;
    states: string[];
    bounds: { leftCssPx: number; topCssPx: number; widthCssPx: number; heightCssPx: number } | null;
    visibility: string;
    elementRef: {
        documentId: string;
        frameId: string;
        elementId: string;
        observedAtRevision: number;
    } | null;
}

interface TraversalEntry {
    element: Element;
    frame: RuntimeFrame;
    parentNodeId: string | null;
    depth: number;
    shadowDepth: number;
}

const DEFAULT_OPTIONS: SemanticObservationOptions = {
    maximumVisitedNodes: 10_000,
    maximumEmittedNodes: 750,
    maximumTotalTextCharacters: 100_000,
    maximumTextCharactersPerNode: 2_000,
    maximumTraversalMs: 1_500,
    viewportExpansionPx: 0,
    maximumFrameDepth: 8,
    maximumShadowDepth: 16,
    includeCompactText: true,
};

const SKIPPED_TAGS = new Set(['script', 'style', 'noscript', 'template', 'meta', 'link']);
const CONTROL_TAGS = new Set(['button', 'input', 'select', 'textarea', 'option', 'summary', 'details']);
const ACTIONABLE_ROLES = new Set([
    'button', 'link', 'checkbox', 'radio', 'switch', 'tab', 'textbox', 'searchbox',
    'combobox', 'listbox', 'option', 'slider', 'spinbutton', 'menuitem', 'scrollbar',
]);
const PRESERVED_ATTRIBUTES = [
    'alt', 'autocomplete', 'checked', 'href', 'name', 'placeholder', 'required',
    'role', 'title', 'type', 'aria-checked', 'aria-expanded', 'aria-label',
    'aria-selected', 'aria-describedby', 'aria-labelledby',
];

export class SemanticObserver {
    private captureSequence = 0;

    constructor(
        private readonly registry: ElementRegistry,
        private readonly revisions: DocumentRevisionTracker,
        private readonly frameRegistry: FrameRegistry = new FrameRegistry(),
    ) {}

    public capture(
        documentId: string,
        requestedOptions: Partial<SemanticObservationOptions> = {},
    ): SemanticObservation {
        const options = validateOptions({ ...DEFAULT_OPTIONS, ...requestedOptions });
        const startedAt = performance.now();
        const frames = this.frameRegistry.snapshot(
            documentId,
            options.maximumFrameDepth,
            options.maximumVisitedNodes,
        );
        const revisionRoots: Node[] = frames.flatMap(frame => frame.document ? [frame.document] : []);
        const revision = this.revisions.current;
        const nodes: SemanticPageNode[] = [];
        const warnings = frameWarnings(frames);
        if (this.frameRegistry.didReachDepthLimit()) {
            warnings.push({
                code: 'FRAME_DEPTH_LIMIT',
                message: `Frame traversal stopped at depth ${options.maximumFrameDepth}`,
            });
        }
        if (this.frameRegistry.didReachScanLimit()) {
            warnings.push({
                code: 'FRAME_SCAN_LIMIT',
                message: `Frame discovery stopped after ${options.maximumVisitedNodes} elements`,
            });
        }
        let shadowDepthWarningEmitted = false;
        let visitedNodeCount = 0;
        let textCharacterCount = 0;
        let truncation: SemanticObservation['truncation'] = null;

        const setTruncation = (reason: string, limit: number, observed: number): void => {
            if (!truncation) truncation = { reason, limit, observed };
        };
        const pending: TraversalEntry[] = [];

        const visit = (
            element: Element,
            frame: RuntimeFrame,
            parentNodeId: string | null,
            depth: number,
            shadowDepth: number,
        ): void => {
            if (truncation) return;
            visitedNodeCount++;
            if (visitedNodeCount > options.maximumVisitedNodes) {
                setTruncation('VISITED_NODES', options.maximumVisitedNodes, visitedNodeCount);
                return;
            }
            const elapsed = performance.now() - startedAt;
            if (elapsed > options.maximumTraversalMs) {
                setTruncation('TRAVERSAL_TIME', options.maximumTraversalMs, Math.ceil(elapsed));
                return;
            }

            const tagName = element.tagName.toLowerCase();
            if (SKIPPED_TAGS.has(tagName) || isHidden(element)) return;
            const role = normalizedAttribute(element, 'role');
            const sensitive = isSensitiveElement(element);
            const rawText = sensitive ? '[REDACTED]' : meaningfulText(element);
            const textBudget = Math.max(0, options.maximumTotalTextCharacters - textCharacterCount);
            const text = capText(rawText, Math.min(options.maximumTextCharactersPerNode, textBudget));
            const kind = nodeKind(tagName, role, text);
            const isFrameDocumentRoot = element === frame.document?.body;
            const shouldEmit = kind !== 'OTHER' || text.length > 0 || isFrameDocumentRoot;
            let nextParentId = parentNodeId;
            let nextDepth = depth;

            if (shouldEmit) {
                if (nodes.length >= options.maximumEmittedNodes) {
                    setTruncation('EMITTED_NODES', options.maximumEmittedNodes, nodes.length + 1);
                    return;
                }
                if (rawText.length > textBudget) {
                    setTruncation('TOTAL_TEXT', options.maximumTotalTextCharacters, textCharacterCount + rawText.length);
                }
                const elementId = this.registry.getOrCreate(element, frame.id);
                const bounds = elementBounds(element, frame);
                const node: SemanticPageNode = {
                    nodeId: elementId,
                    parentNodeId,
                    frameId: frame.id,
                    depth,
                    kind: isFrameDocumentRoot ? 'DOCUMENT' : kind,
                    tagName: tagName || null,
                    role,
                    text: text || null,
                    accessibleName: sensitive ? '[REDACTED]' : accessibleName(element, text) || null,
                    accessibleDescription: sensitive
                        ? null
                        : referencedText(element, element.getAttribute('aria-describedby')) || null,
                    attributes: safeAttributes(element, sensitive),
                    states: elementStates(element),
                    bounds,
                    visibility: elementVisibility(element, bounds, frame, options.viewportExpansionPx),
                    elementRef: isActionable(element, tagName, role) ? {
                        documentId: frame.documentId,
                        frameId: frame.id,
                        elementId,
                        observedAtRevision: revision,
                    } : null,
                };
                nodes.push(node);
                textCharacterCount += text.length;
                nextParentId = node.nodeId;
                nextDepth = depth + 1;
            }

            if (element.shadowRoot && !truncation) {
                if (shadowDepth >= options.maximumShadowDepth) {
                    if (!shadowDepthWarningEmitted) {
                        warnings.push({
                            code: 'SHADOW_DEPTH_LIMIT',
                            message: `Open Shadow DOM traversal stopped at depth ${options.maximumShadowDepth}`,
                        });
                        shadowDepthWarningEmitted = true;
                    }
                } else {
                    revisionRoots.push(element.shadowRoot);
                    pushTraversalEntries(
                        pending,
                        Array.from(element.shadowRoot.children),
                        frame,
                        nextParentId,
                        nextDepth,
                        shadowDepth + 1,
                    );
                }
            }
            pushTraversalEntries(
                pending,
                Array.from(element.children),
                frame,
                nextParentId,
                nextDepth,
                shadowDepth,
            );
        };

        for (const frame of frames) {
            if (truncation) break;
            if (!frame.document?.body) continue;
            const parentNodeId = frame.hostElement && frame.parentId
                ? this.registry.getOrCreate(frame.hostElement, frame.parentId)
                : null;
            pending.push({
                element: frame.document.body,
                frame,
                parentNodeId,
                depth: frame.depth,
                shadowDepth: 0,
            });
            while (pending.length > 0 && !truncation) {
                const entry = pending.pop()!;
                visit(entry.element, entry.frame, entry.parentNodeId, entry.depth, entry.shadowDepth);
            }
        }

        this.revisions.setRoots(revisionRoots);
        this.registry.prune();
        const compactText = options.includeCompactText ? serializeCompact(nodes, revision) : '';
        const observation: SemanticObservation = {
            id: `observation-${documentId}-${revision}-${++this.captureSequence}`,
            capturedAtEpochMs: Date.now(),
            contentTrust: 'UNTRUSTED_WEBPAGE',
            document: {
                id: documentId,
                url: safeWindowUrl(window) ?? '',
                title: document.title || '',
                phase: 'READY',
            },
            revision,
            viewport: viewport(window, document),
            frames: frames.map(toSemanticFrame),
            nodes,
            compactText,
            screenshot: null,
            truncation,
            warnings,
            metrics: {
                durationMs: Math.max(0, Math.ceil(performance.now() - startedAt)),
                visitedNodeCount,
                emittedNodeCount: nodes.length,
                textCharacterCount,
                encodedByteCount: 0,
            },
        };
        observation.metrics.encodedByteCount = utf8ByteLength(JSON.stringify(observation));
        return observation;
    }
}

function validateOptions(options: SemanticObservationOptions): SemanticObservationOptions {
    const positiveFields: Array<keyof SemanticObservationOptions> = [
        'maximumVisitedNodes', 'maximumEmittedNodes', 'maximumTotalTextCharacters',
        'maximumTextCharactersPerNode', 'maximumTraversalMs',
    ];
    for (const field of positiveFields) {
        const value = options[field];
        if (typeof value !== 'number' || !Number.isFinite(value) || value < 1) {
            throw new Error(`${field} must be a positive number`);
        }
    }
    if (!Number.isInteger(options.maximumFrameDepth) || options.maximumFrameDepth < 0 || options.maximumFrameDepth > 64) {
        throw new Error('maximumFrameDepth must be an integer within 0..64');
    }
    if (!Number.isInteger(options.maximumShadowDepth) || options.maximumShadowDepth < 0 || options.maximumShadowDepth > 64) {
        throw new Error('maximumShadowDepth must be an integer within 0..64');
    }
    if (options.maximumEmittedNodes > options.maximumVisitedNodes) {
        throw new Error('maximumEmittedNodes cannot exceed maximumVisitedNodes');
    }
    if (!Number.isFinite(options.viewportExpansionPx) || options.viewportExpansionPx < -1) {
        throw new Error('viewportExpansionPx must be -1 or non-negative');
    }
    return options;
}

function pushTraversalEntries(
    pending: TraversalEntry[],
    children: Element[],
    frame: RuntimeFrame,
    parentNodeId: string | null,
    depth: number,
    shadowDepth: number,
): void {
    for (let index = children.length - 1; index >= 0; index--) {
        pending.push({
            element: children[index],
            frame,
            parentNodeId,
            depth,
            shadowDepth,
        });
    }
}

function toSemanticFrame(frame: RuntimeFrame): SemanticFrame {
    return {
        id: frame.id,
        parentId: frame.parentId,
        documentId: frame.documentId,
        url: frame.url,
        origin: frame.origin,
        depth: frame.depth,
        capability: frame.capability,
    };
}

function frameWarnings(frames: RuntimeFrame[]): Array<{ code: string; message: string }> {
    return frames
        .filter(frame => frame.capability !== 'OBSERVABLE_AND_ACTIONABLE')
        .map(frame => ({
            code: frame.capability,
            message: `Frame ${frame.id} cannot be observed or acted upon: ${frame.capability}`,
        }));
}

function isHidden(element: Element): boolean {
    if (element.hasAttribute('hidden') || element.getAttribute('aria-hidden') === 'true') return true;
    try {
        const style = element.ownerDocument.defaultView?.getComputedStyle(element);
        return style?.display === 'none' || style?.visibility === 'hidden' ||
            style?.visibility === 'collapse' || style?.opacity === '0';
    } catch {
        return false;
    }
}

function meaningfulText(element: Element): string {
    const directText = Array.from(element.childNodes)
        .filter(node => node.nodeType === 3)
        .map(node => node.textContent || '')
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim();
    if (directText) return directText;
    return element.children.length === 0
        ? (element.textContent || '').replace(/\s+/g, ' ').trim()
        : '';
}

function nodeKind(tagName: string, role: string | null, text: string): string {
    if (/^h[1-6]$/.test(tagName) || role === 'heading') return 'HEADING';
    if (tagName === 'a' || role === 'link') return 'LINK';
    if (CONTROL_TAGS.has(tagName) || (role && ACTIONABLE_ROLES.has(role))) return 'CONTROL';
    if (['main', 'nav', 'header', 'footer', 'aside', 'section', 'article', 'form'].includes(tagName)) return 'LANDMARK';
    if (tagName === 'ul' || tagName === 'ol' || role === 'list') return 'LIST';
    if (tagName === 'li' || role === 'listitem') return 'LIST_ITEM';
    if (tagName === 'table' || role === 'table') return 'TABLE';
    if (tagName === 'tr' || role === 'row') return 'ROW';
    if (tagName === 'td' || tagName === 'th' || role === 'cell') return 'CELL';
    if (tagName === 'img' || role === 'img') return 'IMAGE';
    if (tagName === 'iframe') return 'FRAME';
    return text ? 'TEXT' : 'OTHER';
}

function isActionable(element: Element, tagName: string, role: string | null): boolean {
    return (tagName === 'a' && element.hasAttribute('href')) ||
        CONTROL_TAGS.has(tagName) ||
        Boolean(role && ACTIONABLE_ROLES.has(role)) ||
        element.hasAttribute('onclick') ||
        element.hasAttribute('tabindex') ||
        (element as HTMLElement).isContentEditable === true;
}

function accessibleName(element: Element, text: string): string {
    return normalizedAttribute(element, 'aria-label') ||
        referencedText(element, element.getAttribute('aria-labelledby')) ||
        normalizedAttribute(element, 'alt') ||
        normalizedAttribute(element, 'title') ||
        normalizedAttribute(element, 'placeholder') ||
        text;
}

function referencedText(element: Element, ids: string | null): string {
    if (!ids) return '';
    return ids.split(/\s+/)
        .map(id => element.ownerDocument.getElementById(id)?.textContent?.replace(/\s+/g, ' ').trim() || '')
        .filter(Boolean)
        .join(' ');
}

function safeAttributes(element: Element, sensitive: boolean): Record<string, string> {
    const result: Record<string, string> = {};
    const inputType = normalizedAttribute(element, 'type').toLowerCase();
    for (const name of PRESERVED_ATTRIBUTES) {
        const value = normalizedAttribute(element, name);
        if (!value) continue;
        result[name] = sensitive || (inputType === 'password' && name === 'placeholder')
            ? '[REDACTED]'
            : name === 'href'
                ? redactUrlSecrets(value, element.baseURI)
                : capText(value, 500);
    }
    return result;
}

function isSensitiveElement(element: Element): boolean {
    const type = normalizedAttribute(element, 'type').toLowerCase();
    const autocomplete = normalizedAttribute(element, 'autocomplete').toLowerCase();
    return type === 'password' ||
        hasSensitiveAncestor(element) ||
        ['current-password', 'new-password', 'cc-number', 'cc-csc', 'one-time-code'].includes(autocomplete);
}

function hasSensitiveAncestor(element: Element): boolean {
    let current: Element | null = element;
    while (current) {
        if (current.hasAttribute('data-agentic-sensitive')) return true;
        const parentElement: Element | null = current.parentElement;
        if (parentElement) {
            current = parentElement;
            continue;
        }
        const root = current.getRootNode();
        current = root instanceof ShadowRoot ? root.host : null;
    }
    return false;
}

function redactUrlSecrets(value: string, baseUrl: string): string {
    try {
        const url = new URL(value, baseUrl);
        url.username = '';
        url.password = '';
        if (url.search) url.search = '?[REDACTED]';
        if (url.hash) url.hash = '#[REDACTED]';
        return capText(url.toString(), 500);
    } catch {
        return '[REDACTED_URL]';
    }
}

function elementStates(element: Element): string[] {
    const states: string[] = [];
    const html = element as HTMLElement & {
        disabled?: boolean; readOnly?: boolean; checked?: boolean; selected?: boolean; required?: boolean;
    };
    if (html.disabled || element.getAttribute('aria-disabled') === 'true') states.push('DISABLED');
    if (html.readOnly || element.hasAttribute('readonly')) states.push('READ_ONLY');
    if (html.checked || element.getAttribute('aria-checked') === 'true') states.push('CHECKED');
    if (html.selected || element.getAttribute('aria-selected') === 'true') states.push('SELECTED');
    if (html.required || element.hasAttribute('required')) states.push('REQUIRED');
    const expanded = element.getAttribute('aria-expanded');
    if (expanded === 'true') states.push('EXPANDED');
    if (expanded === 'false') states.push('COLLAPSED');
    if (element === element.ownerDocument.activeElement) states.push('FOCUSED');
    if (['input', 'textarea'].includes(element.tagName.toLowerCase()) || html.isContentEditable) states.push('EDITABLE');
    return states;
}

function elementBounds(element: Element, frame: RuntimeFrame): SemanticPageNode['bounds'] {
    try {
        const rect = element.getBoundingClientRect();
        return {
            leftCssPx: frame.offsetLeftCssPx + finiteOrZero(rect.left),
            topCssPx: frame.offsetTopCssPx + finiteOrZero(rect.top),
            widthCssPx: Math.max(0, finiteOrZero(rect.width)),
            heightCssPx: Math.max(0, finiteOrZero(rect.height)),
        };
    } catch {
        return null;
    }
}

function elementVisibility(
    element: Element,
    bounds: SemanticPageNode['bounds'],
    frame: RuntimeFrame,
    expansion: number,
): string {
    if (!bounds || !frame.window || !frame.document) return 'UNKNOWN';
    if (bounds.widthCssPx === 0 && bounds.heightCssPx === 0) return 'UNKNOWN';
    const localLeft = bounds.leftCssPx - frame.offsetLeftCssPx;
    const localTop = bounds.topCssPx - frame.offsetTopCssPx;
    if (expansion !== -1 && (
        localTop + bounds.heightCssPx < -expansion ||
        localLeft + bounds.widthCssPx < -expansion ||
        localTop > frame.window.innerHeight + expansion ||
        localLeft > frame.window.innerWidth + expansion
    )) return 'OFFSCREEN';
    try {
        const top = frame.document.elementFromPoint(
            localLeft + bounds.widthCssPx / 2,
            localTop + bounds.heightCssPx / 2,
        );
        if (top && top !== element && !element.contains(top) && !top.contains(element)) return 'OCCLUDED';
    } catch {
        return 'UNKNOWN';
    }
    return 'VISIBLE';
}

function serializeCompact(nodes: SemanticPageNode[], revision: number): string {
    const lines = [`[Untrusted webpage observation revision=${revision}]`];
    for (const node of nodes) {
        if (node.kind === 'DOCUMENT') {
            if (node.frameId !== 'main') lines.push(`[Frame ${node.frameId}]`);
            continue;
        }
        const indent = '  '.repeat(Math.min(node.depth, 12));
        const reference = node.elementRef ? `[${node.frameId}/${node.elementRef.elementId}]` : '';
        const tag = node.tagName || node.kind.toLowerCase();
        const role = node.role ? ` role=${JSON.stringify(node.role)}` : '';
        const name = node.accessibleName ? ` name=${JSON.stringify(capText(node.accessibleName, 200))}` : '';
        const text = node.text && node.text !== node.accessibleName ? ` ${capText(node.text, 500)}` : '';
        lines.push(`${indent}${reference}<${tag}${role}${name}>${text}`.trimEnd());
    }
    return lines.join('\n');
}

function viewport(targetWindow: Window, targetDocument: Document): Record<string, number> {
    const root = targetDocument.documentElement;
    return {
        scrollXCssPx: finiteOrZero(targetWindow.scrollX),
        scrollYCssPx: finiteOrZero(targetWindow.scrollY),
        widthCssPx: finiteOrZero(targetWindow.innerWidth),
        heightCssPx: finiteOrZero(targetWindow.innerHeight),
        contentWidthCssPx: finiteOrZero(root?.scrollWidth || 0),
        contentHeightCssPx: finiteOrZero(root?.scrollHeight || 0),
        devicePixelRatio: finiteOrZero(targetWindow.devicePixelRatio || 1),
        visualViewportScale: finiteOrZero(targetWindow.visualViewport?.scale || 1),
    };
}

function safeWindowUrl(target: Window): string | null {
    try { return target.location.href || null; } catch { return null; }
}

function normalizedAttribute(element: Element, name: string): string {
    return (element.getAttribute(name) || '').replace(/\s+/g, ' ').trim();
}

function capText(value: string, maximum: number): string {
    if (maximum <= 0) return '';
    return value.length <= maximum ? value : `${value.slice(0, Math.max(0, maximum - 1))}…`;
}

function finiteOrZero(value: number): number {
    return Number.isFinite(value) ? value : 0;
}

function utf8ByteLength(value: string): number {
    let bytes = 0;
    for (const character of value) {
        const codePoint = character.codePointAt(0)!;
        if (codePoint <= 0x7f) bytes++;
        else if (codePoint <= 0x7ff) bytes += 2;
        else if (codePoint <= 0xffff) bytes += 3;
        else bytes += 4;
    }
    return bytes;
}
