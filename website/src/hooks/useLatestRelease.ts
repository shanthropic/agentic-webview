import { useState, useEffect } from 'react';

export function useLatestRelease() {
  const [version, setVersion] = useState<string>('0.2.0'); // default fallback

  useEffect(() => {
    fetch('https://api.github.com/repos/shantoislamdev/agentic-webview/releases/latest')
      .then((res) => {
        if (!res.ok) {
          console.warn('Could not fetch latest release (repository might be private). Using static fallback 0.2.0.');
          return null;
        }
        return res.json();
      })
      .then((data) => {
        if (data && data.tag_name) {
          // Sometimes tags start with 'v', we can keep it or remove it depending on standard.
          // In Home.tsx, it's "v0.2.0" and "0.2.0", let's provide both.
          const tag = data.tag_name; // e.g. "v0.2.0" or "0.2.0"
          const cleanVersion = tag.startsWith('v') ? tag.slice(1) : tag;
          setVersion(cleanVersion);
        }
      })
      .catch((err) => {
        // Silently catch network errors to keep it clean in console
        console.warn('Network error when fetching release, falling back to 0.2.0');
      });
  }, []);

  return {
    version,
    fetchVersion: () => version,
    tagVersion: `v${version.replace(/^v/, '')}`
  };
}
