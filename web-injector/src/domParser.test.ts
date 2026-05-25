import { DomParser } from './domParser';

describe('domParser', () => {
    let parser: DomParser;

    beforeEach(() => {
        document.body.innerHTML = '';
        parser = new DomParser();

        // Mock offsetWidth/Height as JSDOM returns 0
        Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, value: 100 });
        Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, value: 30 });

        // Mock innerText as JSDOM returns undefined/empty
        Object.defineProperty(HTMLElement.prototype, 'innerText', {
            configurable: true,
            get: function() { return this.textContent; }
        });
    });

    it('should generate accessibility tree for simple elements', () => {
        document.body.innerHTML = `
            <button id="btn" aria-label="Submit Button">Click Me</button>
            <input id="inp" type="text" value="Hello">
        `;

        const { tree } = parser.getAccessibilityTree();
        const treeStr = JSON.stringify(tree);
        expect(treeStr).toContain('Submit Button');
        expect(treeStr).toContain('INPUT');
        expect(treeStr).toContain('Hello');
    });

    it('should handle nested elements', () => {
        document.body.innerHTML = `
            <div>
                <a href="/test" id="link">Link Text</a>
            </div>
        `;

        const { tree } = parser.getAccessibilityTree();
        const treeStr = JSON.stringify(tree);
        expect(treeStr).toContain('Link Text');
        expect(treeStr).toContain('A');
    });
});
