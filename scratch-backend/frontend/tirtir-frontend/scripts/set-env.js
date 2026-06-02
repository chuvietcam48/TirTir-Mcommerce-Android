const fs = require('fs');
const path = require('path');
const dotenv = require('dotenv');

// Load env vars
dotenv.config({ path: path.join(__dirname, '../.env') });

const targetPath = path.join(__dirname, '../src/environments/environment.ts');
const port = process.env.PORT || 5001;

const envConfigFile = `export const environment = {
    production: false,
    apiUrl: 'http://localhost:${port}/api/v1'
};
`;

fs.writeFileSync(targetPath, envConfigFile, { encoding: 'utf-8' });

console.log(`Environment variables generated to ${targetPath} with PORT=${port}`);
