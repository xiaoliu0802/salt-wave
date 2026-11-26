const http = require('http');
const fs = require('fs');
const path = require('path');
const https = require('https');
const http_ = require('http');
const crypto = require('crypto');
const URL = require('url').URL;

const PORT = 8080;

// Session store: token -> { serverUrl, username, password }
const sessions = {};

function genSessionId() {
    return crypto.randomBytes(16).toString('hex');
}

function md5(str) {
    return crypto.createHash('md5').update(str, 'utf8').digest('hex');
}

function genSalt() {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let salt = '';
    for (let i = 0; i < 12; i++) salt += chars[Math.floor(Math.random() * chars.length)];
    return salt;
}

// Build Subsonic API auth params using server-side crypto
function buildAuthParams(session, extra = {}) {
    const salt = genSalt();
    const token = md5(session.password + salt);
    const params = new URLSearchParams();
    params.append('u', session.username);
    params.append('t', token);
    params.append('s', salt);
    params.append('v', '1.16.1');
    params.append('c', 'SaltWave');
    params.append('f', 'json');
    for (const [key, value] of Object.entries(extra)) {
        params.append(key, value);
    }
    return params.toString();
}

// Proxy a request to the Navidrome server using session auth
function proxyApiCall(session, method, extraParams, req, res) {
    const paramString = buildAuthParams(session, extraParams);
    const targetUrl = `${session.serverUrl}/rest/${method}?${paramString}`;
    let target;
    try {
        target = new URL(targetUrl);
    } catch (e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid url' }));
        return;
    }

    const lib = target.protocol === 'https:' ? https : http_;
    const reqHeaders = { 'User-Agent': 'SaltWave/1.0' };
    // Forward Range header for stream seeking
    if (method === 'stream' && req.headers['range']) {
        reqHeaders['Range'] = req.headers['range'];
    }
    const options = {
        hostname: target.hostname,
        port: target.port || (target.protocol === 'https:' ? 443 : 80),
        path: target.pathname + target.search,
        method: req.method,
        headers: reqHeaders
    };

    const proxyReq = lib.request(options, (proxyRes) => {
        console.log(`[Proxy] ${proxyRes.statusCode} ${method} ${targetUrl.substring(0, 150)}`);

        // Collect response body for JSON endpoints
        const contentType = proxyRes.headers['content-type'] || '';
        if (contentType.includes('json') || contentType.includes('xml')) {
            let body = '';
            proxyRes.on('data', (chunk) => body += chunk);
            proxyRes.on('end', () => {
                // Log lyrics response for debugging
                if (method === 'getLyrics' || method === 'getLyricsBySongId') {
                    console.log(`[Lyrics] ${method} Response: ${body.substring(0, 800)}`);
                }
                res.writeHead(proxyRes.statusCode, {
                    'Content-Type': contentType || 'application/json',
                    'Access-Control-Allow-Origin': '*',
                });
                res.end(body);
            });
        } else {
            // Stream binary (audio/images) directly — forward all necessary headers for seeking
            const respHeaders = {
                'Content-Type': contentType || 'application/octet-stream',
                'Access-Control-Allow-Origin': '*',
                'Accept-Ranges': proxyRes.headers['accept-ranges'] || 'bytes',
            };
            if (proxyRes.headers['content-length']) respHeaders['Content-Length'] = proxyRes.headers['content-length'];
            if (proxyRes.headers['content-range']) respHeaders['Content-Range'] = proxyRes.headers['content-range'];
            res.writeHead(proxyRes.statusCode, respHeaders);
            proxyRes.pipe(res);
        }
    });

    proxyReq.on('error', (e) => {
        console.error('Proxy error:', e.message);
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Proxy failed: ' + e.message }));
    });

    req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
    const pathname = parsedUrl.pathname;

    // === Login endpoint ===
    if (pathname === '/api/login') {
        let body = '';
        req.on('data', (chunk) => body += chunk);
        req.on('end', () => {
            try {
                const { serverUrl, username, password } = JSON.parse(body);
                if (!serverUrl || !username || !password) {
                    res.writeHead(400, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Missing fields' }));
                    return;
                }

                const session = {
                    serverUrl: serverUrl.replace(/\/+$/, ''),
                    username,
                    password
                };

                // Test with ping
                const params = buildAuthParams(session);
                const targetUrl = `${session.serverUrl}/rest/ping?${params}`;
                const target = new URL(targetUrl);
                const lib = target.protocol === 'https:' ? https : http_;

                const proxyReq = lib.request({
                    hostname: target.hostname,
                    port: target.port || (target.protocol === 'https:' ? 443 : 80),
                    path: target.pathname + target.search,
                    method: 'GET',
                    headers: { 'User-Agent': 'SaltWave/1.0' }
                }, (proxyRes) => {
                    let respBody = '';
                    proxyRes.on('data', (chunk) => respBody += chunk);
                    proxyRes.on('end', () => {
                        console.log(`[Login] ${proxyRes.statusCode} for ${username}@${session.serverUrl}`);
                        console.log(`[Login] Response: ${respBody.substring(0, 300)}`);

                        try {
                            const data = JSON.parse(respBody);
                            if (data['subsonic-response']?.status === 'ok') {
                                const sessionId = genSessionId();
                                sessions[sessionId] = session;
                                res.writeHead(200, { 'Content-Type': 'application/json' });
                                res.end(JSON.stringify({ ok: true, sessionId }));
                            } else {
                                const errMsg = data['subsonic-response']?.error?.message || 'Unknown error';
                                const errCode = data['subsonic-response']?.error?.code || 0;
                                res.writeHead(200, { 'Content-Type': 'application/json' });
                                res.end(JSON.stringify({ ok: false, error: errMsg, code: errCode }));
                            }
                        } catch (e) {
                            res.writeHead(200, { 'Content-Type': 'application/json' });
                            res.end(JSON.stringify({ ok: false, error: 'Invalid response from server: ' + respBody.substring(0, 200) }));
                        }
                    });
                });

                proxyReq.on('error', (e) => {
                    console.error('[Login] Error:', e.message);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ ok: false, error: 'Cannot connect: ' + e.message }));
                });

                proxyReq.end();
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid request' }));
            }
        });
        return;
    }

    // === API proxy endpoint: /api/:method?sessionId=xxx&param1=yyy&param2=zzz ===
    if (pathname.startsWith('/api/')) {
        const method = pathname.replace('/api/', '');
        const sessionId = parsedUrl.searchParams.get('sessionId');
        const session = sessions[sessionId];

        if (!session) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not authenticated' }));
            return;
        }

        // Collect extra params (everything except sessionId)
        const extra = {};
        for (const [key, value] of parsedUrl.searchParams.entries()) {
            if (key !== 'sessionId') extra[key] = value;
        }

        proxyApiCall(session, method, extra, req, res);
        return;
    }

    // === Stream/CoverArt endpoint: /stream/:id?sessionId=xxx or /cover/:id?sessionId=xxx&size=300 ===
    if (pathname.startsWith('/stream/') || pathname.startsWith('/cover/')) {
        const isStream = pathname.startsWith('/stream/');
        const id = decodeURIComponent(pathname.split('/')[2]);
        const sessionId = parsedUrl.searchParams.get('sessionId');
        const session = sessions[sessionId];

        if (!session) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Not authenticated' }));
            return;
        }

        const extra = { id };
        if (!isStream) {
            const size = parsedUrl.searchParams.get('size') || 300;
            extra.size = size;
        }

        proxyApiCall(session, isStream ? 'stream' : 'getCoverArt', extra, req, res);
        return;
    }

    // === Serve static files ===
    let filePath = pathname === '/' ? '/index.html' : pathname;
    filePath = path.join(__dirname, filePath.split('?')[0]);

    const ext = path.extname(filePath);
    const mimeTypes = {
        '.html': 'text/html; charset=utf-8',
        '.js': 'application/javascript',
        '.css': 'text/css',
        '.json': 'application/json',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.svg': 'image/svg+xml',
        '.ico': 'image/x-icon',
    };

    fs.readFile(filePath, (err, data) => {
        if (err) {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            res.end('Not found');
            return;
        }
        res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
        res.end(data);
    });
});

server.listen(PORT, () => {
    console.log(`Salt Wave server running at http://localhost:${PORT}`);
});
