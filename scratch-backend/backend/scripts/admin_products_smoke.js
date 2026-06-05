const { ensureAdminAndToken, assert, request } = require('./test_utils');
async function run() {
  const token = await ensureAdminAndToken();
  const id = 'ADM-SMOKE-001';
  let r = await request('POST', '/admin/products', { Product_ID: id, Name: 'Smoke Cushion', Price: 150000, Category: 'Cushion', Stock_Quantity: 10 }, token);
  assert(r.status === 201, 'create_status');
  assert(r.data.Product_ID === id, 'create_id');
  r = await request('PUT', `/admin/products/${id}`, { Price: 123456 }, token);
  assert(r.status === 200, 'update_status');
  assert(r.data.Price === 123456, 'update_price');
  r = await request('PATCH', `/admin/products/${id}/stock`, { stock: 77 }, token);
  assert(r.status === 200, 'stock_status');
  assert(r.data.stock === 77, 'stock_value');
  r = await request('DELETE', `/admin/products/${id}`, undefined, token);
  assert(r.status === 200, 'delete_status');
  console.log('SMOKE_OK');
}
run().catch(e => { console.error(e); process.exitCode = 1; });
