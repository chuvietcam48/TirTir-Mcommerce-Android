const http = require('http');

const data = JSON.stringify({
  skinType: 'Dry',
  concerns: ['dry_flaky']
});

const options = {
  hostname: 'localhost',
  port: 5000,
  path: '/api/v1/ai/recommend-routine',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': data.length
  }
};

const req = http.request(options, res => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => console.log(JSON.stringify(JSON.parse(body), null, 2)));
});

req.on('error', error => console.error(error));
req.write(data);
req.end();
