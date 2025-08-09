import http from 'http';

const port = process.env.PORT || 3000;
const host = process.env.HOST || '0.0.0.0';

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ ok: true, path: req.url }));
});

server.listen(port, host, () => {
  // eslint-disable-next-line no-console
  console.log(`[demo] Listening on http://${host}:${port}`);
}); 