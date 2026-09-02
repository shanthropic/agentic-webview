import { ElementRegistry } from './elementRegistry';

describe('ElementRegistry', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
    });

    it('keeps the same identity when unrelated siblings are inserted', () => {
        const registry = new ElementRegistry();
        const button = document.createElement('button');
        document.body.appendChild(button);
        const original = registry.getOrCreate(button);

        document.body.insertBefore(document.createElement('div'), button);

        expect(registry.getOrCreate(button)).toBe(original);
    });

    it('assigns a new identity to a replacement element', () => {
        const registry = new ElementRegistry();
        const original = document.createElement('button');
        document.body.appendChild(original);
        const originalId = registry.getOrCreate(original);
        const replacement = document.createElement('button');

        original.replaceWith(replacement);

        expect(registry.getOrCreate(replacement)).not.toBe(originalId);
        expect(registry.resolve(originalId)).toBeUndefined();
    });

    it('prunes disconnected elements', () => {
        const registry = new ElementRegistry();
        const button = document.createElement('button');
        document.body.appendChild(button);
        registry.getOrCreate(button);
        button.remove();

        expect(registry.prune()).toBe(1);
        expect(registry.size).toBe(0);
    });
});
