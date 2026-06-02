const { ensureAdminAndToken, assert, request } = require('./test_utils');
async function run() {
  const token = await ensureAdminAndToken();
  const payload = [];
  for (let i = 1; i <= 20; i++) {
    payload.push({ Product_ID: `ADM-BULK-${i.toString().padStart(3,'0')}`, Name: `Bulk Item ${i}`, Price: 1000 + i, Category: 'Cushion', Stock_Quantity: i });
  }
  payload.push({ Product_ID: 'ADM-BULK-005', Name: 'Bulk Item 5 Updated', Price: 9999, Category: 'Cushion', Stock_Quantity: 99 });
  payload.push({ Name: 'Missing ID' });
  let r = await request('POST', '/admin/products/bulk-import', payload, token);
  assert(r.status === 200, 'bulk_status');
  assert(r.data.created >= 20, 'bulk_created');
  assert(r.data.updated >= 1, 'bulk_updated');
  assert(Array.isArray(r.data.errors), 'bulk_errors_array');
  for (let i = 1; i <= 20; i++) {
    await request('DELETE', `/admin/products/ADM-BULK-${i.toString().padStart(3,'0')}`, undefined, token);
  }
  console.log('BULK_OK');
}
run().catch(e => { console.error(e); process.exitCode = 1; });
