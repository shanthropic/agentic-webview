export interface ExperimentalPagePatchConfiguration {
    hideWebDriverProperty?: boolean;
    installChromeLikeGlobals?: boolean;
    forceFutureShadowRootsOpen?: boolean;
}

let webdriverPatched = false;
let chromeGlobalsInstalled = false;
let shadowAttachmentPatched = false;

export function installExperimentalPagePatches(config: ExperimentalPagePatchConfiguration): void {
    if (config.hideWebDriverProperty === true && !webdriverPatched) {
        const descriptor = Object.getOwnPropertyDescriptor(Navigator.prototype, 'webdriver');
        if (!descriptor || descriptor.configurable) {
            Object.defineProperty(Navigator.prototype, 'webdriver', {
                configurable: true,
                get: () => undefined,
            });
            webdriverPatched = true;
        }
    }

    if (config.installChromeLikeGlobals === true && !chromeGlobalsInstalled) {
        const target = window as Window & { chrome?: Record<string, unknown> };
        if (!target.chrome) target.chrome = Object.freeze({ runtime: Object.freeze({}) });
        chromeGlobalsInstalled = true;
    }

    if (config.forceFutureShadowRootsOpen === true && !shadowAttachmentPatched) {
        const original = Element.prototype.attachShadow;
        Object.defineProperty(Element.prototype, 'attachShadow', {
            configurable: true,
            writable: true,
            value(init: ShadowRootInit): ShadowRoot {
                return original.call(this, { ...init, mode: 'open' });
            },
        });
        shadowAttachmentPatched = true;
    }
}
