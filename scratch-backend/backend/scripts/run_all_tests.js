const { execSync } = require('child_process');
function run(cmd) {
  try { const out = execSync(cmd, { stdio: 'pipe' }).toString(); console.log(out.trim()); return true; }
  catch (e) { console.error(e.stdout ? e.stdout.toString() : ''); console.error(e.stderr ? e.stderr.toString() : ''); return false; }
}
const results = [];
results.push(run('node scripts/admin_products_smoke.js'));
results.push(run('node scripts/admin_products_validation.js'));
results.push(run('node scripts/admin_products_bulk_edge.js'));
results.push(run('node scripts/admin_products_unauthorized.js'));
const ok = results.every(Boolean);
if (!ok) { console.error('TESTS_FAILED'); process.exitCode = 1; } else { console.log('ALL_TESTS_OK'); }
