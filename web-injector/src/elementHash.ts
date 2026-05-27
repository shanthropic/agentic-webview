import { AccessibilityNode } from './domParser';

async function sha256(input: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(input);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

function getParentBranchPath(node: AccessibilityNode, allNodes: AccessibilityNode[]): string[] {
    const path: string[] = [];
    // Walk up via id chain - simplified: use xpath segments
    if (node.xpath) {
        const segments = node.xpath.split('/');
        for (const seg of segments) {
            const tag = seg.replace(/\[\d+\]/, '');
            if (tag) path.push(tag);
        }
    }
    return path;
}

export async function hashDomElement(node: AccessibilityNode): Promise<{
    branchPathHash: string;
    attributesHash: string;
    xpathHash: string;
}> {
    const branchPath = getParentBranchPath(node, []);
    const branchPathStr = branchPath.join('/');
    const attributesStr = Object.entries(node.attributes)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => `${k}=${v}`)
        .join('');

    const [branchPathHash, attributesHash, xpathHash] = await Promise.all([
        sha256(branchPathStr),
        sha256(attributesStr),
        sha256(node.xpath || ''),
    ]);

    return { branchPathHash, attributesHash, xpathHash };
}

export async function hashDomElementQuick(node: AccessibilityNode): Promise<string> {
    const { branchPathHash, attributesHash, xpathHash } = await hashDomElement(node);
    return `${branchPathHash}-${attributesHash}-${xpathHash}`;
}

export async function findElementInTree(
    targetHash: { branchPathHash: string; attributesHash: string; xpathHash: string },
    nodes: AccessibilityNode[],
): Promise<AccessibilityNode | null> {
    for (const node of nodes) {
        if (node.highlightIndex === null || node.highlightIndex === undefined) continue;
        const hash = await hashDomElement(node);
        if (
            hash.branchPathHash === targetHash.branchPathHash &&
            hash.attributesHash === targetHash.attributesHash &&
            hash.xpathHash === targetHash.xpathHash
        ) {
            return node;
        }
    }
    return null;
}
