import { ElementRegistry } from './elementRegistry';
import { DocumentRevisionTracker } from './revisionTracker';
import { SemanticObserver } from './semanticObserver';

describe('SemanticObserver', () => {
    let registry: ElementRegistry;
    let revisions: DocumentRevisionTracker;
    let observer: SemanticObserver;

    beforeEach(() => {
        document.body.innerHTML = '';
        registry = new ElementRegistry();
        revisions = new DocumentRevisionTracker();
        observer = new SemanticObserver(registry, revisions);
    });

    it('includes readable content as well as actionable controls', () => {
        document.body.innerHTML = `
            <main>
                <h1>Account settings</h1>
                <p>Update your public profile.</p>
                <button aria-label="Save profile">Save</button>
            </main>
        `;

        const observation = observer.capture('document-1');

        expect(observation.contentTrust).toBe('UNTRUSTED_WEBPAGE');
        expect(observation.nodes.some(node => node.kind === 'HEADING' && node.text === 'Account settings')).toBe(true);
        expect(observation.nodes.some(node => node.kind === 'TEXT' && node.text === 'Update your public profile.')).toBe(true);
        const button = observation.nodes.find(node => node.tagName === 'button');
        expect(button?.accessibleName).toBe('Save profile');
        expect(button?.elementRef?.documentId).toBe('document-1');
    });

    it('preserves an actionable element reference across unrelated mutations', () => {
        document.body.innerHTML = '<main><button>Continue</button></main>';
        const first = observer.capture('document-1');
        const firstRef = first.nodes.find(node => node.tagName === 'button')?.elementRef;

        document.querySelector('main')?.prepend(document.createElement('p'));
        revisions.bump();
        const second = observer.capture('document-1');
        const secondRef = second.nodes.find(node => node.tagName === 'button')?.elementRef;

        expect(secondRef?.elementId).toBe(firstRef?.elementId);
        expect(secondRef?.observedAtRevision).toBe(1);
    });

    it('reports structured truncation when emitted-node budget is reached', () => {
        document.body.innerHTML = '<main><p>One</p><p>Two</p><p>Three</p></main>';

        const observation = observer.capture('document-1', {
            maximumVisitedNodes: 10,
            maximumEmittedNodes: 2,
        });

        expect(observation.nodes).toHaveLength(2);
        expect(observation.truncation?.reason).toBe('EMITTED_NODES');
    });

    it('handles deeply nested hostile markup without recursive call-stack growth', () => {
        let parent: Element = document.body;
        for (let depth = 0; depth < 2_000; depth++) {
            const child = document.createElement('div');
            parent.appendChild(child);
            parent = child;
        }

        const style = jest.spyOn(window, 'getComputedStyle').mockReturnValue({
            display: 'block',
            visibility: 'visible',
            opacity: '1',
        } as CSSStyleDeclaration);
        const observation = (() => {
            try {
                return observer.capture('document-1', {
                    maximumVisitedNodes: 1_000,
                    maximumEmittedNodes: 10,
                    maximumTraversalMs: 30_000,
                });
            } finally {
                style.mockRestore();
            }
        })();

        expect(observation.truncation?.reason).toBe('VISITED_NODES');
    });

    it('does not expose password values', () => {
        document.body.innerHTML = '<input type="password" value="secret" placeholder="Password">';

        const observation = observer.capture('document-1');
        const password = observation.nodes.find(node => node.tagName === 'input');

        expect(JSON.stringify(password)).not.toContain('secret');
    });

    it('redacts explicitly sensitive page content and accessible metadata', () => {
        document.body.innerHTML = '<div data-agentic-sensitive aria-label="Private balance">$42,000</div>';

        const observation = observer.capture('document-1');
        const sensitive = observation.nodes.find(node => node.tagName === 'div');

        expect(sensitive?.text).toBe('[REDACTED]');
        expect(sensitive?.accessibleName).toBe('[REDACTED]');
        expect(JSON.stringify(sensitive)).not.toContain('42,000');
        expect(JSON.stringify(sensitive)).not.toContain('Private balance');
    });

    it('redacts every descendant of an explicitly sensitive container', () => {
        document.body.innerHTML = `
            <section data-agentic-sensitive>
                <span aria-label="Recovery code">alpha-bravo-secret</span>
            </section>
        `;

        const observation = observer.capture('document-1');
        const serialized = JSON.stringify(observation.nodes);

        expect(serialized).not.toContain('alpha-bravo-secret');
        expect(serialized).not.toContain('Recovery code');
    });

    it('removes credentials, query values, and fragments from observed links', () => {
        document.body.innerHTML = '<a href="https://user:pass@example.com/account?token=secret#private">Account</a>';

        const observation = observer.capture('document-1');
        const link = observation.nodes.find(node => node.tagName === 'a');

        expect(link?.attributes.href).toContain('example.com/account');
        expect(link?.attributes.href).not.toContain('user');
        expect(link?.attributes.href).not.toContain('pass');
        expect(link?.attributes.href).not.toContain('secret');
        expect(link?.attributes.href).not.toContain('private');
    });

    it('observes actionable controls inside open Shadow DOM', () => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        const root = host.attachShadow({ mode: 'open' });
        root.innerHTML = '<button>Shadow action</button>';

        const observation = observer.capture('document-1');

        expect(observation.nodes.some(node =>
            node.tagName === 'button' && node.accessibleName === 'Shadow action' && node.elementRef,
        )).toBe(true);
    });

    it('increments the document revision for mutations inside an observed Shadow root', async () => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        const root = host.attachShadow({ mode: 'open' });
        root.innerHTML = '<button>Before</button>';
        observer.capture('document-1');

        root.querySelector('button')!.textContent = 'After';
        await new Promise(resolve => setTimeout(resolve, 0));
        const observation = observer.capture('document-1');

        expect(observation.revision).toBe(1);
        expect(observation.nodes.some(node => node.accessibleName === 'After')).toBe(true);
    });

    it('reports when the Shadow DOM depth budget is reached', () => {
        const outer = document.createElement('div');
        document.body.appendChild(outer);
        const inner = document.createElement('div');
        outer.attachShadow({ mode: 'open' }).appendChild(inner);
        inner.attachShadow({ mode: 'open' }).innerHTML = '<button>Too deep</button>';

        const observation = observer.capture('document-1', { maximumShadowDepth: 1 });

        expect(observation.warnings).toContainEqual(expect.objectContaining({ code: 'SHADOW_DEPTH_LIMIT' }));
        expect(observation.nodes.some(node => node.accessibleName === 'Too deep')).toBe(false);
    });
});
