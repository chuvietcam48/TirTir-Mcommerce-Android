const { ensureAdminAndToken, assert, request } = require('./test_utils');
async function run() {
  const token = await ensureAdminAndToken();
  let r = await request('POST', '/admin/products', { Name: 'Invalid' }, token);
  assert(r.status === 400, 'missing_fields');
  r = await request('PATCH', `/admin/products/NOT-FOUND-ID/stock`, { stock: -5 }, token);
  assert(r.status === 400, 'invalid_stock');
  r = await request('PUT', `/admin/products/NOT-FOUND-ID`, { Price: 1 }, token);
  assert(r.status === 404, 'update_not_found');
  r = await request('DELETE', `/admin/products/NOT-FOUND-ID`, undefined, token);
  assert(r.status === 404, 'delete_not_found');
  console.log('VALIDATION_OK');
}
run().catch(e => { console.error(e); process.exitCode = 1; });
