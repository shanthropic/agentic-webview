import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../fixtures/', import.meta.url));
const port = Number(process.env.AGENTIC_FIXTURE_PORT || 8787);

createServer(async (request, response) => {
  const pathname = new URL(request.url || '/', 'http://localhost').pathname;
  const relative = pathname === '/' ? 'complex.html' : pathname.slice(1);
  const file = normalize(join(root, relative));
  if (!file.startsWith(root)) {
    response.writeHead(403).end('Forbidden');
    return;
  }
  try {
    const metadata = await stat(file);
    if (!metadata.isFile()) throw new Error('Not a file');
    const mime = extname(file) === '.html' ? 'text/html; charset=utf-8' : 'text/plain; charset=utf-8';
    response.writeHead(200, { 'content-type': mime, 'cache-control': 'no-store' });
    createReadStream(file).pipe(response);
  } catch {
    response.writeHead(404).end('Not found');
  }
}).listen(port, '127.0.0.1', () => {
  process.stdout.write(`Agentic WebView fixtures: http://127.0.0.1:${port}/\n`);
});
