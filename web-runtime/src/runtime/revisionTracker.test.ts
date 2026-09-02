import { DocumentRevisionTracker } from './revisionTracker';

describe('DocumentRevisionTracker', () => {
    it('coalesces a synchronous mutation storm into one observer revision', async () => {
        const tracker = new DocumentRevisionTracker();
        tracker.start(document);

        for (let index = 0; index < 1_000; index++) {
            document.body.appendChild(document.createElement('span'));
        }
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(tracker.current).toBe(1);
        tracker.stop();
    });

    it('tracks mutations across multiple observed frame roots', async () => {
        const frameDocument = document.implementation.createHTMLDocument('frame');
        const tracker = new DocumentRevisionTracker();
        tracker.setRoots([document, frameDocument]);

        document.body.appendChild(document.createElement('p'));
        frameDocument.body.appendChild(frameDocument.createElement('button'));
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(tracker.current).toBe(2);
        tracker.stop();
    });
});
