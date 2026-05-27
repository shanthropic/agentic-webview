export async function waitForElementStability(
    el: Element,
    timeoutMs: number = 1000,
    pollIntervalMs: number = 50,
): Promise<boolean> {
    const startTime = performance.now();
    let lastRect: DOMRect | null = null;
    let stableCount = 0;
    const STABLE_THRESHOLD = 2; // px
    const REQUIRED_STABLE_CYCLES = 1;

    return new Promise<boolean>((resolve) => {
        const check = () => {
            const rect = el.getBoundingClientRect();
            if (lastRect) {
                const dx = Math.abs(rect.x - lastRect.x);
                const dy = Math.abs(rect.y - lastRect.y);
                const dw = Math.abs(rect.width - lastRect.width);
                const dh = Math.abs(rect.height - lastRect.height);

                if (dx < STABLE_THRESHOLD && dy < STABLE_THRESHOLD &&
                    dw < STABLE_THRESHOLD && dh < STABLE_THRESHOLD) {
                    stableCount++;
                    if (stableCount >= REQUIRED_STABLE_CYCLES) {
                        resolve(true);
                        return;
                    }
                } else {
                    stableCount = 0;
                }
            }
            lastRect = rect;

            if (performance.now() - startTime >= timeoutMs) {
                resolve(false);
                return;
            }

            setTimeout(check, pollIntervalMs);
        };

        check();
    });
}
