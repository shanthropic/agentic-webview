import { ElementRegistry } from './elementRegistry';
import { DocumentRevisionTracker } from './revisionTracker';
import { SemanticActionExecutor } from './actionExecutor';

describe('SemanticActionExecutor', () => {
    let registry: ElementRegistry;
    let revisions: DocumentRevisionTracker;
    let executor: SemanticActionExecutor;

    beforeEach(() => {
        document.body.innerHTML = '';
        registry = new ElementRegistry();
        revisions = new DocumentRevisionTracker();
        executor = new SemanticActionExecutor(registry, revisions, () => 'document-1');
    });

    it('types through the native setter and verifies the resulting value', async () => {
        const input = document.createElement('input');
        document.body.appendChild(input);
        const target = reference(registry.getOrCreate(input));

        const receipt = await executor.execute('document-1', {
            type: 'type_text',
            target,
            text: 'Hello',
            mode: 'REPLACE_ALL',
        });

        expect(input.value).toBe('Hello');
        expect(receipt.dispatched).toBe(true);
        expect(receipt.verified).toBe(true);
        expect(receipt.strategy).toBe('DOM_NATIVE_SETTER');
    });

    it.each([
        ['APPEND', 'Start', ' plus', 'Start plus'],
        ['CLEAR', 'Start', 'ignored', ''],
    ])('supports %s text mode', async (mode, initial, text, expected) => {
        const input = document.createElement('input');
        input.value = initial;
        document.body.appendChild(input);

        const receipt = await executor.execute('document-1', {
            type: 'type_text',
            target: reference(registry.getOrCreate(input)),
            text,
            mode,
        });

        expect(input.value).toBe(expected);
        expect(receipt.verified).toBe(true);
    });

    it('inserts text at the current selection', async () => {
        const input = document.createElement('input');
        input.value = 'abcd';
        document.body.appendChild(input);
        input.setSelectionRange(1, 3);

        await executor.execute('document-1', {
            type: 'type_text',
            target: reference(registry.getOrCreate(input)),
            text: 'X',
            mode: 'INSERT_AT_SELECTION',
        });

        expect(input.value).toBe('aXd');
    });

    it('rejects detached element references as stale', async () => {
        const button = document.createElement('button');
        document.body.appendChild(button);
        const target = reference(registry.getOrCreate(button));
        button.remove();

        await expect(executor.execute('document-1', { type: 'click', target }))
            .rejects.toMatchObject({ code: 'STALE_ELEMENT' });
    });

    it('verifies a click only when it produces an observable effect', async () => {
        const button = document.createElement('button');
        button.addEventListener('click', () => button.setAttribute('aria-pressed', 'true'));
        document.body.appendChild(button);

        const receipt = await executor.execute('document-1', {
            type: 'click',
            target: reference(registry.getOrCreate(button)),
        });

        expect(receipt.dispatched).toBe(true);
        expect(receipt.verified).toBe(true);
    });

    it('reports an effectless click as dispatched but unverified', async () => {
        const button = document.createElement('button');
        document.body.appendChild(button);

        const receipt = await executor.execute('document-1', {
            type: 'click',
            target: reference(registry.getOrCreate(button)),
        });

        expect(receipt.dispatched).toBe(true);
        expect(receipt.verified).toBe(false);
    });

    it('selects options by label and verifies the value', async () => {
        document.body.innerHTML = '<select><option value="a">Alpha</option><option value="b">Beta</option></select>';
        const select = document.querySelector('select')!;
        const target = reference(registry.getOrCreate(select));

        const receipt = await executor.execute('document-1', {
            type: 'select_option',
            target,
            option: { type: 'label', label: 'Beta' },
        });

        expect(select.value).toBe('b');
        expect(receipt.verified).toBe(true);
    });

    it('rejects disabled controls before dispatch', async () => {
        const button = document.createElement('button');
        button.disabled = true;
        document.body.appendChild(button);
        const target = reference(registry.getOrCreate(button));

        await expect(executor.execute('document-1', { type: 'click', target }))
            .rejects.toMatchObject({ code: 'ELEMENT_NOT_ACTIONABLE' });
    });

    it('rejects references whose observation revision is in the future', async () => {
        const button = document.createElement('button');
        document.body.appendChild(button);
        const target = { ...reference(registry.getOrCreate(button)), observedAtRevision: 1 };

        await expect(executor.execute('document-1', { type: 'click', target }))
            .rejects.toMatchObject({ code: 'STALE_ELEMENT' });
    });

    it('types into a validated same-origin frame element', async () => {
        const iframe = document.createElement('iframe');
        document.body.appendChild(iframe);
        const input = iframe.contentDocument!.createElement('input');
        iframe.contentDocument!.body.appendChild(input);
        const elementId = registry.getOrCreate(input, 'main:f1');
        const framedExecutor = new SemanticActionExecutor(
            registry,
            revisions,
            () => 'document-1',
            (_root, frameId, documentId) =>
                frameId === 'main:f1' && documentId === 'document-1#main:f1',
        );

        const receipt = await framedExecutor.execute('document-1', {
            type: 'type_text',
            target: {
                documentId: 'document-1#main:f1',
                frameId: 'main:f1',
                elementId,
                observedAtRevision: 0,
            },
            text: 'Frame text',
            mode: 'REPLACE_ALL',
        });

        expect(input.value).toBe('Frame text');
        expect(receipt.verified).toBe(true);
    });

    it('dispatches and verifies a long press without retrying', async () => {
        const button = document.createElement('button');
        button.addEventListener('pointerup', () => button.setAttribute('aria-expanded', 'true'));
        document.body.appendChild(button);

        const receipt = await executor.execute('document-1', {
            type: 'long_press',
            target: reference(registry.getOrCreate(button)),
            durationMs: 1,
        });

        expect(receipt.dispatched).toBe(true);
        expect(receipt.verified).toBe(true);
        expect(receipt.strategy).toBe('DOM_POINTER');
    });

    it('dispatches a key chord to the active element', async () => {
        const input = document.createElement('input');
        input.addEventListener('keydown', () => { input.value = 'handled'; });
        document.body.appendChild(input);
        input.focus();

        const receipt = await executor.execute('document-1', {
            type: 'press_keys',
            chord: { key: 'a', control: true, alt: false, shift: false, meta: false },
        });

        expect(receipt.verified).toBe(true);
        expect(receipt.strategy).toBe('DOM_KEYBOARD');
    });

    it('prepares and verifies an externally dispatched native click', async () => {
        const button = document.createElement('button');
        button.getBoundingClientRect = () => ({
            x: 10, y: 20, left: 10, top: 20, width: 100, height: 40,
            right: 110, bottom: 60, toJSON: () => ({}),
        } as DOMRect);
        button.addEventListener('click', () => button.setAttribute('aria-pressed', 'true'));
        document.body.appendChild(button);
        const target = reference(registry.getOrCreate(button));

        const preparation = await executor.prepareNativeClick(
            'document-1',
            target,
            () => ({ left: 0, top: 0 }),
        );
        button.click();
        const receipt = executor.verifyNativeClick('document-1', preparation.token as string);

        expect(receipt.strategy).toBe('ANDROID_NATIVE_POINTER');
        expect(receipt.verified).toBe(true);
    });

    function reference(elementId: string) {
        return {
            documentId: 'document-1',
            frameId: 'main',
            elementId,
            observedAtRevision: 0,
        };
    }
});
