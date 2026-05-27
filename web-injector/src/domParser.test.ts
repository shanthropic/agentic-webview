import { DomParser } from './domParser';

describe('domParser', () => {
    let parser: DomParser;

    beforeEach(() => {
        document.body.innerHTML = '';
        parser = new DomParser();

        Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, value: 100 });
        Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, value: 30 });

        Object.defineProperty(HTMLElement.prototype, 'innerText', {
            configurable: true,
            get: function() { return this.textContent; }
        });

        // Mock getClientRects for JSDOM
        HTMLElement.prototype.getClientRects = function() {
            const rect = this.getBoundingClientRect();
            return [rect] as any;
        };
        HTMLElement.prototype.getBoundingClientRect = function() {
            return { x: 0, y: 0, width: 100, height: 30, top: 0, left: 0, right: 100, bottom: 30, toJSON: () => {} };
        };

        // Mock Range.getClientRects for text node visibility
        const origCreateRange = document.createRange.bind(document);
        document.createRange = function() {
            const range = origCreateRange();
            range.getClientRects = function() {
                return [{ x: 0, y: 0, width: 50, height: 14, top: 0, left: 0, right: 50, bottom: 14 }] as any;
            };
            return range;
        };

        // Mock checkVisibility for JSDOM
        (Element.prototype as any).checkVisibility = function() { return true; };

        // Mock elementFromPoint - return the element being tested (matching topmost)
        document.elementFromPoint = function(_x: number, _y: number) {
            // Return deepest leaf in body to simulate realistic hit testing
            const all = document.querySelectorAll('button, a, input, select, textarea');
            if (all.length > 0) {
                // Find the element nearest to the center of viewport
                return all[all.length - 1]; // last interactive element (deepest in DOM)
            }
            return document.body;
        };
    });

    it('should generate accessibility tree for simple elements', () => {
        document.body.innerHTML = `
            <button id="btn" aria-label="Submit Button">Click Me</button>
        `;

        const { tree } = parser.getAccessibilityTree();
        const treeStr = JSON.stringify(tree);
        expect(treeStr).toContain('Submit Button');
        expect(treeStr).toContain('BUTTON');
    });

    it('should detect input elements', () => {
        document.body.innerHTML = `
            <input id="inp" type="text" value="Hello" placeholder="Enter name">
        `;

        const { tree } = parser.getAccessibilityTree();
        const treeStr = JSON.stringify(tree);
        expect(treeStr).toContain('INPUT');
    });

    it('should handle nested elements', () => {
        document.body.innerHTML = `
            <div>
                <a href="/test" id="link">Link Text</a>
            </div>
        `;

        const { tree } = parser.getAccessibilityTree();
        const treeStr = JSON.stringify(tree);
        expect(treeStr).toContain('A');
    });

    it('should return selectorMap with highlight indices', () => {
        document.body.innerHTML = `
            <button id="btn">Click</button>
        `;

        const { selectorMap } = parser.getAccessibilityTree();
        expect(selectorMap).toBeDefined();
        expect(Object.keys(selectorMap).length).toBeGreaterThan(0);
    });

    it('should include xpath in nodes', () => {
        document.body.innerHTML = `
            <button id="btn">Click</button>
        `;

        const { tree } = parser.getAccessibilityTree();
        expect(tree.length).toBeGreaterThan(0);
        expect(tree[0].xpath).toBeDefined();
        expect(typeof tree[0].xpath).toBe('string');
        expect(tree[0].xpath.length).toBeGreaterThan(0);
    });

    it('should include isTopElement and isInteractive flags', () => {
        document.body.innerHTML = `
            <button id="btn">Click</button>
        `;

        const { tree } = parser.getAccessibilityTree();
        expect(tree.length).toBeGreaterThan(0);
        expect(typeof tree[0].isTopElement).toBe('boolean');
        expect(typeof tree[0].isInteractive).toBe('boolean');
        expect(tree[0].isTopElement).toBe(true);
        expect(tree[0].isInteractive).toBe(true);
    });

    it('should assign highlightIndex to interactive elements', () => {
        document.body.innerHTML = `
            <button id="btn">Click</button>
        `;

        const { tree } = parser.getAccessibilityTree();
        expect(tree.length).toBeGreaterThan(0);
        expect(tree[0].highlightIndex).toBe(0);
    });

    it('should include occluded and inIframe fields', () => {
        document.body.innerHTML = `
            <button id="btn">Click</button>
        `;

        const { tree } = parser.getAccessibilityTree();
        expect(tree.length).toBeGreaterThan(0);
        expect(typeof tree[0].occluded).toBe('boolean');
        expect(typeof tree[0].inIframe).toBe('boolean');
    });

    it('should handle empty body', () => {
        document.body.innerHTML = '';
        const { tree, truncated } = parser.getAccessibilityTree();
        expect(tree).toEqual([]);
        expect(truncated).toBe(false);
    });
});
