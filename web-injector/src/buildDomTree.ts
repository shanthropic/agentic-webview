export interface DomNodeData {
    tagName: string;
    attributes: Record<string, string>;
    xpath: string;
    children: string[];
    isVisible?: boolean;
    isTopElement?: boolean;
    isInteractive?: boolean;
    isInViewport?: boolean;
    highlightIndex?: number;
    shadowRoot?: boolean;
    type?: 'TEXT_NODE';
    text?: string;
}

export interface BuildDomTreeResult {
    rootId: string;
    map: Record<string, DomNodeData>;
    highlightIndexCount: number;
}

const MAX_DEPTH = 100;

const DISTINCT_INTERACTIVE_TAGS = new Set([
    'a', 'button', 'input', 'select', 'textarea',
    'summary', 'details', 'label', 'option',
]);

const INTERACTIVE_ROLES = new Set([
    'button', 'link', 'menuitem', 'menuitemradio', 'menuitemcheckbox',
    'radio', 'checkbox', 'tab', 'switch', 'slider', 'spinbutton',
    'combobox', 'searchbox', 'textbox', 'listbox', 'option', 'scrollbar',
]);

const INTERACTIVE_CURSORS = new Set([
    'pointer', 'move', 'text', 'grab', 'grabbing', 'cell', 'copy', 'alias',
    'all-scroll', 'col-resize', 'context-menu', 'crosshair', 'e-resize',
    'ew-resize', 'help', 'n-resize', 'ne-resize', 'nesw-resize', 'ns-resize',
    'nw-resize', 'nwse-resize', 'row-resize', 's-resize', 'se-resize',
    'sw-resize', 'vertical-text', 'w-resize', 'zoom-in', 'zoom-out',
]);

const NON_INTERACTIVE_CURSORS = new Set([
    'not-allowed', 'no-drop', 'wait', 'progress', 'initial', 'inherit',
]);

const INTERACTIVE_ELEMENT_TAGS = new Set([
    'a', 'button', 'input', 'select', 'textarea', 'details', 'summary',
    'label', 'option', 'optgroup', 'fieldset', 'legend',
]);

const INTERACTIVE_ROLES_FULL = new Set([
    'button', 'menu', 'menubar', 'menuitem', 'menuitemradio',
    'menuitemcheckbox', 'radio', 'checkbox', 'tab', 'switch', 'slider',
    'spinbutton', 'combobox', 'searchbox', 'textbox', 'listbox', 'option',
    'scrollbar',
]);

const ALWAYS_ACCEPT_TAGS = new Set([
    'body', 'div', 'main', 'article', 'section', 'nav', 'header', 'footer',
]);

const LEAF_DENY_LIST = new Set([
    'svg', 'script', 'style', 'link', 'meta', 'noscript', 'template',
]);

export class BuildDomTreeEngine {
    private highlightIndex = 0;
    private nextId = 0;
    private visitedNodes: WeakSet<Node> | null = null;
    private domMap: Record<string, DomNodeData> = {};
    private xpathCache = new WeakMap<Element, string>();

    private boundingRects = new WeakMap<Element, DOMRect>();
    private clientRects = new WeakMap<Element, DOMRectList>();
    private computedStyles = new WeakMap<Element, CSSStyleDeclaration>();

    private viewportExpansion: number;
    private elementMap: Map<string, Element>;

    constructor(viewportExpansion: number = 0, elementMap: Map<string, Element>) {
        this.viewportExpansion = viewportExpansion;
        this.elementMap = elementMap;
    }

    build(): BuildDomTreeResult {
        this.highlightIndex = 0;
        this.nextId = 0;
        this.visitedNodes = null;
        this.domMap = {};
        this.boundingRects = new WeakMap();
        this.clientRects = new WeakMap();
        this.computedStyles = new WeakMap();
        this.xpathCache = new WeakMap();

        const rootId = this.buildDomTree(document.body);
        return {
            rootId: rootId || '',
            map: this.domMap,
            highlightIndexCount: this.highlightIndex,
        };
    }

    private getCachedBoundingRect(element: Element): DOMRect | null {
        if (!element) return null;
        if (this.boundingRects.has(element)) return this.boundingRects.get(element)!;
        const rect = element.getBoundingClientRect();
        if (rect) this.boundingRects.set(element, rect);
        return rect;
    }

    private getCachedComputedStyle(element: Element): CSSStyleDeclaration | null {
        if (!element) return null;
        if (this.computedStyles.has(element)) return this.computedStyles.get(element)!;
        const style = window.getComputedStyle(element);
        if (style) this.computedStyles.set(element, style);
        return style;
    }

    private getCachedClientRects(element: Element): DOMRectList | null {
        if (!element) return null;
        if (this.clientRects.has(element)) return this.clientRects.get(element)!;
        const rects = element.getClientRects();
        if (rects) this.clientRects.set(element, rects);
        return rects;
    }

    private getElementPosition(currentElement: Element): number {
        if (!currentElement.parentElement) return 0;
        const tagName = currentElement.nodeName.toLowerCase();
        const siblings = Array.from(currentElement.parentElement.children)
            .filter(sib => sib.nodeName.toLowerCase() === tagName);
        if (siblings.length === 1) return 0;
        return siblings.indexOf(currentElement) + 1;
    }

    private getXPathTree(element: Element, stopAtBoundary = true): string {
        if (this.xpathCache.has(element)) return this.xpathCache.get(element)!;

        const segments: string[] = [];
        let current: Element | null = element;

        while (current && current.nodeType === Node.ELEMENT_NODE) {
            if (
                stopAtBoundary &&
                (current.parentNode instanceof ShadowRoot ||
                    current.parentNode instanceof HTMLIFrameElement)
            ) {
                break;
            }
            const position = this.getElementPosition(current);
            const tagName = current.nodeName.toLowerCase();
            const xpathIndex = position > 0 ? `[${position}]` : '';
            segments.unshift(`${tagName}${xpathIndex}`);
            current = current.parentNode as Element;
        }

        const result = segments.join('/');
        this.xpathCache.set(element, result);
        return result;
    }

    private isTextNodeVisible(textNode: Text): boolean {
        try {
            if (this.viewportExpansion === -1) {
                const parent = textNode.parentElement;
                if (!parent) return false;
                try {
                    return parent.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
                } catch {
                    const style = window.getComputedStyle(parent);
                    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0';
                }
            }

            const range = document.createRange();
            range.selectNodeContents(textNode);
            const rects = range.getClientRects();
            if (!rects || rects.length === 0) return false;

            let isAnyRectVisible = false;
            let isAnyRectInViewport = false;

            for (const rect of rects) {
                if (rect.width > 0 && rect.height > 0) {
                    isAnyRectVisible = true;
                    if (
                        !(rect.bottom < -this.viewportExpansion ||
                            rect.top > window.innerHeight + this.viewportExpansion ||
                            rect.right < -this.viewportExpansion ||
                            rect.left > window.innerWidth + this.viewportExpansion)
                    ) {
                        isAnyRectInViewport = true;
                        break;
                    }
                }
            }

            if (!isAnyRectVisible || !isAnyRectInViewport) return false;

            const parent = textNode.parentElement;
            if (!parent) return false;
            try {
                return parent.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
            } catch {
                const style = window.getComputedStyle(parent);
                return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0';
            }
        } catch {
            return false;
        }
    }

    private isElementAccepted(element: Element): boolean {
        if (!element || !element.tagName) return false;
        const tagName = element.tagName.toLowerCase();
        if (ALWAYS_ACCEPT_TAGS.has(tagName)) return true;
        return !LEAF_DENY_LIST.has(tagName);
    }

    private isElementVisible(element: HTMLElement): boolean {
        const style = this.getCachedComputedStyle(element);
        return (
            element.offsetWidth > 0 &&
            element.offsetHeight > 0 &&
            style?.visibility !== 'hidden' &&
            style?.display !== 'none'
        );
    }

    private isInteractiveElement(element: HTMLElement): boolean {
        if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;

        const tagName = element.tagName.toLowerCase();
        const style = this.getCachedComputedStyle(element);

        if (element.tagName.toLowerCase() !== 'html' && style?.cursor && INTERACTIVE_CURSORS.has(style.cursor)) {
            return true;
        }

        if (INTERACTIVE_ELEMENT_TAGS.has(tagName)) {
            if (style?.cursor && NON_INTERACTIVE_CURSORS.has(style.cursor)) return false;
            if (element.hasAttribute('disabled') || element.getAttribute('disabled') === 'true' || element.getAttribute('disabled') === '') return false;
            if (element.hasAttribute('readonly') || element.getAttribute('readonly') === 'true' || element.getAttribute('readonly') === '') return false;
            if ((element as any).disabled) return false;
            if ((element as any).readOnly) return false;
            if ((element as any).inert) return false;
            return true;
        }

        const role = element.getAttribute('role');
        const ariaRole = element.getAttribute('aria-role');

        if (element.getAttribute('contenteditable') === 'true' || element.isContentEditable) return true;

        if (
            element.classList &&
            (element.classList.contains('button') ||
                element.classList.contains('dropdown-toggle') ||
                element.getAttribute('data-index') ||
                element.getAttribute('data-toggle') === 'dropdown' ||
                element.getAttribute('aria-haspopup') === 'true')
        ) {
            return true;
        }

        if (
            INTERACTIVE_ELEMENT_TAGS.has(tagName) ||
            (role && INTERACTIVE_ROLES_FULL.has(role)) ||
            (ariaRole && INTERACTIVE_ROLES_FULL.has(ariaRole))
        ) {
            return true;
        }

        try {
            const commonMouseAttrs = ['onclick', 'onmousedown', 'onmouseup', 'ondblclick'];
            for (const attr of commonMouseAttrs) {
                if (element.hasAttribute(attr) || typeof (element as any)[attr] === 'function') return true;
            }
        } catch {
            // ignore
        }

        return false;
    }

    private isHeuristicallyInteractive(element: HTMLElement): boolean {
        if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;
        if (!this.isElementVisible(element)) return false;

        const hasInteractiveAttributes =
            element.hasAttribute('role') ||
            element.hasAttribute('tabindex') ||
            element.hasAttribute('onclick') ||
            typeof (element as any).onclick === 'function';

        const hasInteractiveClass = /\b(btn|clickable|menu|item|entry|link)\b/i.test(element.className || '');
        const isInKnownContainer = Boolean(element.closest('button,a,[role="button"],.menu,.dropdown,.list,.toolbar'));
        const hasVisibleChildren = [...element.children].some(c => this.isElementVisible(c as HTMLElement));
        const isParentBody = element.parentElement && element.parentElement.isSameNode(document.body);

        return (
            (this.isInteractiveElement(element) || hasInteractiveAttributes || hasInteractiveClass) &&
            hasVisibleChildren &&
            isInKnownContainer &&
            !isParentBody
        );
    }

    private isTopElement(element: HTMLElement): boolean {
        if (this.viewportExpansion === -1) return true;

        const rects = this.getCachedClientRects(element);
        if (!rects || rects.length === 0) return false;

        let isAnyRectInViewport = false;
        for (const rect of rects) {
            if (
                rect.width > 0 && rect.height > 0 &&
                !(rect.bottom < -this.viewportExpansion ||
                    rect.top > window.innerHeight + this.viewportExpansion ||
                    rect.right < -this.viewportExpansion ||
                    rect.left > window.innerWidth + this.viewportExpansion)
            ) {
                isAnyRectInViewport = true;
                break;
            }
        }
        if (!isAnyRectInViewport) return false;

        const doc = element.ownerDocument;
        if (doc !== window.document) return true;

            const rootNode = element.getRootNode();
            if (rootNode instanceof ShadowRoot) {
                const midRect = rects[Math.floor(rects.length / 2)];
                const centerX = midRect.left + midRect.width / 2;
                const centerY = midRect.top + midRect.height / 2;
                try {
                    const topEl = rootNode.elementFromPoint(centerX, centerY);
                    if (!topEl) return false;
                    let current: Node | null = topEl;
                    while (current && current !== rootNode) {
                        if (current === element) return true;
                        current = (current as Element).parentElement || null;
                    }
                    return false;
                } catch {
                    return true;
                }
            }

        const margin = 5;
        const rect = rects[Math.floor(rects.length / 2)];
        const checkPoints = [
            { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 },
            { x: rect.left + margin, y: rect.top + margin },
            { x: rect.right - margin, y: rect.bottom - margin },
        ];

        return checkPoints.some(({ x, y }) => {
            try {
                const topEl = document.elementFromPoint(x, y);
                // If elementFromPoint returns null (e.g., JSDOM), assume element is top
                if (!topEl) return true;
                let current: Element | null = topEl;
                while (current && current !== document.documentElement) {
                    if (current === element) return true;
                    current = current.parentElement;
                }
                return false;
            } catch {
                return true;
            }
        });
    }

    private isInExpandedViewport(element: HTMLElement): boolean {
        if (this.viewportExpansion === -1) return true;

        const rects = element.getClientRects();
        if (!rects || rects.length === 0) {
            const boundingRect = this.getCachedBoundingRect(element);
            if (!boundingRect || boundingRect.width === 0 || boundingRect.height === 0) return false;
            return !(
                boundingRect.bottom < -this.viewportExpansion ||
                boundingRect.top > window.innerHeight + this.viewportExpansion ||
                boundingRect.right < -this.viewportExpansion ||
                boundingRect.left > window.innerWidth + this.viewportExpansion
            );
        }

        for (const rect of rects) {
            if (rect.width === 0 || rect.height === 0) continue;
            if (
                !(rect.bottom < -this.viewportExpansion ||
                    rect.top > window.innerHeight + this.viewportExpansion ||
                    rect.right < -this.viewportExpansion ||
                    rect.left > window.innerWidth + this.viewportExpansion)
            ) {
                return true;
            }
        }
        return false;
    }

    private isInteractiveCandidate(element: Element): boolean {
        if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;
        const tagName = element.tagName.toLowerCase();
        if (INTERACTIVE_ELEMENT_TAGS.has(tagName)) return true;
        return (
            element.hasAttribute('onclick') ||
            element.hasAttribute('role') ||
            element.hasAttribute('tabindex') ||
            element.hasAttribute('data-action') ||
            element.getAttribute('contenteditable') === 'true'
        );
    }

    private isElementDistinctInteraction(element: HTMLElement): boolean {
        if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;

        const tagName = element.tagName.toLowerCase();
        const role = element.getAttribute('role');

        if (tagName === 'iframe') return true;
        if (DISTINCT_INTERACTIVE_TAGS.has(tagName)) return true;
        if (role && INTERACTIVE_ROLES.has(role)) return true;
        if (element.isContentEditable || element.getAttribute('contenteditable') === 'true') return true;
        if (element.hasAttribute('data-testid') || element.hasAttribute('data-cy') || element.hasAttribute('data-test')) return true;
        if (element.hasAttribute('onclick') || typeof (element as any).onclick === 'function') return true;

        try {
            const commonEventAttrs = [
                'onmousedown', 'onmouseup', 'onkeydown', 'onkeyup',
                'onsubmit', 'onchange', 'oninput', 'onfocus', 'onblur',
            ];
            if (commonEventAttrs.some(attr => element.hasAttribute(attr))) return true;
        } catch {
            // ignore
        }

        if (this.isHeuristicallyInteractive(element)) return true;
        return false;
    }

    private handleHighlighting(
        nodeData: DomNodeData,
        node: HTMLElement,
        isParentHighlighted: boolean,
    ): boolean {
        if (!nodeData.isInteractive) return false;

        let shouldHighlight = false;
        if (!isParentHighlighted) {
            shouldHighlight = true;
        } else if (this.isElementDistinctInteraction(node)) {
            shouldHighlight = true;
        }

        if (shouldHighlight) {
            nodeData.isInViewport = this.isInExpandedViewport(node);
            if (nodeData.isInViewport || this.viewportExpansion === -1) {
                nodeData.highlightIndex = this.highlightIndex++;
                return true;
            }
        }
        return false;
    }

    private buildDomTree(
        node: Node,
        isParentHighlighted = false,
        depth = 0,
    ): string | null {
        if (!this.visitedNodes) this.visitedNodes = new WeakSet();
        if (depth > MAX_DEPTH) return null;

        if (
            !node ||
            (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.TEXT_NODE)
        ) {
            return null;
        }

        if (node.nodeType === Node.ELEMENT_NODE && this.visitedNodes.has(node)) return null;
        if (node.nodeType === Node.ELEMENT_NODE) this.visitedNodes.add(node);

        // Handle body root
        if (node === document.body) {
            const nodeData: DomNodeData = {
                tagName: 'body',
                attributes: {},
                xpath: '/body',
                children: [],
            };
            for (const child of Array.from(node.childNodes)) {
                const childId = this.buildDomTree(child, false, depth + 1);
                if (childId) nodeData.children.push(childId);
            }
            const id = `${this.nextId++}`;
            this.domMap[id] = nodeData;
            return id;
        }

        // Text nodes
        if (node.nodeType === Node.TEXT_NODE) {
            const textContent = node.textContent?.trim();
            if (!textContent) return null;
            const parentEl = (node as Text).parentElement;
            if (!parentEl || parentEl.tagName.toLowerCase() === 'script') return null;

            const id = `${this.nextId++}`;
            this.domMap[id] = {
                tagName: '',
                attributes: {},
                xpath: '',
                children: [],
                type: 'TEXT_NODE',
                text: textContent,
                isVisible: this.isTextNodeVisible(node as Text),
            };
            return id;
        }

        // Element nodes
        const element = node as HTMLElement;
        if (!this.isElementAccepted(element)) return null;

        // Early viewport check
        if (this.viewportExpansion !== -1 && !element.shadowRoot) {
            const rect = this.getCachedBoundingRect(element);
            const style = this.getCachedComputedStyle(element);
            const isFixedOrSticky = style && (style.position === 'fixed' || style.position === 'sticky');
            const hasSize = element.offsetWidth > 0 || element.offsetHeight > 0;

            if (
                !rect ||
                (!isFixedOrSticky && !hasSize &&
                    (rect.bottom < -this.viewportExpansion ||
                        rect.top > window.innerHeight + this.viewportExpansion ||
                        rect.right < -this.viewportExpansion ||
                        rect.left > window.innerWidth + this.viewportExpansion))
            ) {
                return null;
            }
        }

        const nodeData: DomNodeData = {
            tagName: element.tagName.toLowerCase(),
            attributes: {},
            xpath: this.getXPathTree(element, true),
            children: [],
        };

        // Get attributes for interactive candidates
        if (
            this.isInteractiveCandidate(element) ||
            element.tagName.toLowerCase() === 'iframe' ||
            element.tagName.toLowerCase() === 'body'
        ) {
            const attributeNames = element.getAttributeNames?.() || [];
            for (const name of attributeNames) {
                const value = element.getAttribute(name);
                if (value !== null) nodeData.attributes[name] = value;
            }
        }

        // Visibility, interactivity, highlighting
        let nodeWasHighlighted = false;
        nodeData.isVisible = this.isElementVisible(element);
        if (nodeData.isVisible) {
            nodeData.isTopElement = this.isTopElement(element);
            const role = element.getAttribute('role');
            const isMenuContainer = role === 'menu' || role === 'menubar' || role === 'listbox';

            if (nodeData.isTopElement || isMenuContainer) {
                nodeData.isInteractive = this.isInteractiveElement(element);
                nodeWasHighlighted = this.handleHighlighting(nodeData, element, isParentHighlighted);
            }
        }

        // Even if not interactive, still process children
        const tagName = element.tagName.toLowerCase();

        if (tagName === 'iframe') {
            const rect = this.getCachedBoundingRect(element);
            if (rect) {
                nodeData.attributes['computedHeight'] = String(Math.ceil(rect.height));
                nodeData.attributes['computedWidth'] = String(Math.ceil(rect.width));

                const shouldSkip =
                    (rect.width <= 1 && rect.height <= 1) ||
                    rect.left < -1000 || rect.top < -1000;

                const sandbox = element.getAttribute('sandbox');
                const isRestrictiveSandbox = sandbox !== null && !sandbox.includes('allow-same-origin');

                if (shouldSkip) {
                    nodeData.attributes['skipped'] = 'invisible-tracking-iframe';
                } else if (isRestrictiveSandbox) {
                    nodeData.attributes['error'] = 'Cross-origin iframe access blocked by sandbox';
                } else {
                    try {
                        const iframeDoc = (element as HTMLIFrameElement).contentDocument ||
                            (element as HTMLIFrameElement).contentWindow?.document;
                        if (iframeDoc && iframeDoc.childNodes) {
                            for (const child of Array.from(iframeDoc.childNodes)) {
                                const childId = this.buildDomTree(child, false, depth + 1);
                                if (childId) nodeData.children.push(childId);
                            }
                        }
                    } catch (e: any) {
                        nodeData.attributes['error'] = e.message;
                    }
                }
            }
        } else if (
            element.isContentEditable ||
            element.getAttribute('contenteditable') === 'true' ||
            element.id === 'tinymce' ||
            element.classList.contains('mce-content-body') ||
            (tagName === 'body' && element.getAttribute('data-id')?.startsWith('mce_'))
        ) {
            for (const child of Array.from(element.childNodes)) {
                const childId = this.buildDomTree(child, nodeWasHighlighted, depth + 1);
                if (childId) nodeData.children.push(childId);
            }
        } else {
            // Shadow DOM
            if (element.shadowRoot) {
                nodeData.shadowRoot = true;
                for (const child of Array.from(element.shadowRoot.childNodes)) {
                    const childId = this.buildDomTree(child, nodeWasHighlighted, depth + 1);
                    if (childId) nodeData.children.push(childId);
                }
            }
            // Regular children
            for (const child of Array.from(element.childNodes)) {
                const passHighlight = nodeWasHighlighted || isParentHighlighted;
                const childId = this.buildDomTree(child, passHighlight, depth + 1);
                if (childId) nodeData.children.push(childId);
            }
        }

        // Skip empty anchors
        if (nodeData.tagName === 'a' && nodeData.children.length === 0 && !nodeData.attributes.href) {
            const rect = this.getCachedBoundingRect(element);
            const hasSize = (rect && rect.width > 0 && rect.height > 0) || element.offsetWidth > 0 || element.offsetHeight > 0;
            if (!hasSize) return null;
        }

        const id = `${this.nextId++}`;
        this.domMap[id] = nodeData;

        // Tag element with agent ID for Kotlin-side lookups
        if (nodeData.highlightIndex !== undefined && nodeData.highlightIndex !== null) {
            const agentId = nodeData.highlightIndex.toString();
            element.setAttribute('data-agent-id', agentId);
            this.elementMap.set(agentId, element);
        }

        return id;
    }
}
