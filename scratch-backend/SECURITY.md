# 🔐 Security Operations Guide

## Secret Rotation & Management

### IMMEDIATELY REQUIRED (Before Production Deployment)

#### 1. Rotate Exposed Secrets
```bash
# GEMINI_API_KEY exposure risk
# Current: AIzaSyBwLT4mNQfJjCJCOQ0y9HqizIRNFtQrED8
# Action: 
#   1. Disable this key in Google Cloud Console
#   2. Generate new key from: https://console.cloud.google.com/
#   3. Update .env with NEW key
#   4. Test in development before deploying
#   5. NEVER commit the key again

# AI_SERVICE_API_KEY - Generate strong key
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
# Output example: a1b2c3d4e5f6... (copy to .env)
```

#### 2. Implement Secret Management Strategy

**Option A: AWS Secrets Manager (Recommended for Production)**
```javascript
// backend/utils/secretsManager.js
const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager();

async function getSecret(secretName) {
    const result = await secretsManager.getSecretValue({ SecretId: secretName });
    return JSON.parse(result.SecretString);
}

// Usage in main.js
const dbCredentials = await getSecret('tirtir/mongodb');
const apiKeys = await getSecret('tirtir/api-keys');
```

**Option B: HashiCorp Vault (For Self-Hosted)**
```bash
# Install Vault, then:
vault kv put secret/tirtir/gemini GEMINI_API_KEY="xxx"
vault kv put secret/tirtir/auth AI_SERVICE_API_KEY="xxx"
```

**Option C: Docker Secrets (For Swarm/Simple Deployments)**
```bash
# Create secrets
echo "your_gemini_key" | docker secret create gemini_api_key -
echo "your_service_key" | docker secret create ai_service_key -

# In docker-compose.yml:
# services:
#   backend:
#     secrets:
#       - gemini_api_key
#       - ai_service_key
```

#### 3. Update Deployment Pipeline
- Store `.env` template in secure location (NOT git)
- Inject secrets at deployment time via CI/CD (GitHub Actions/GitLab CI)
- Use encrypted environment variables in CI/CD configuration

### Environment-Specific Configuration

**.env.develop** (Local development)
```
NODE_ENV=development
MONGODB_URI=mongodb://localhost:27017/tirtir_dev
REDIS_URL=redis://localhost:6379
# Use dummy keys for local testing
GEMINI_API_KEY=AIzaSyDummyKeyForLocalTesting123
AI_SERVICE_API_KEY=dev_key_only_for_testing
```

**.env.production** (Production - Never committed)
```
NODE_ENV=production
# All secrets fetched from AWS Secrets Manager at runtime
SECRETS_MANAGER_ENABLED=true
```

#### 4. Audit Checklist

- [ ] GEMINI_API_KEY disabled in Google Cloud Console and replaced
- [ ] AI_SERVICE_API_KEY is cryptographically random (32+ bytes)
- [ ] MongoDB credentials rotated
- [ ] JWT_SECRET is random and strong (256+ bits)
- [ ] .env file is in .gitignore ✅ (already done)
- [ ] .env.example created with placeholders ✅ (already done)
- [ ] Secrets Manager implementation chosen (AWS/Vault/Docker)
- [ ] CI/CD pipeline updated to inject secrets
- [ ] All developers instructed on secret handling policy
- [ ] Audit logs enabled on secrets access

### Secret Rotation Schedule

- **API Keys (Gemini, GHN, Stripe)**: Every 90 days or after breach
- **Database Credentials**: Every 180 days + on team change
- **JWT Secrets**: Every 365 days + after major deployment
- **Service API Keys (AI_SERVICE_API_KEY)**: Every 30 days
- **Master keys (if any)**: Every 7 days (or implement key versioning)

### Incident Response

**If .env accidentally committed:**
```bash
# 1. Immediate: Rotate all secrets NOW
# 2. Remove from history (this creates a new commit):
git filter-branch --tree-filter 'rm -f .env' HEAD

# 3. Force push (DANGEROUS - involves team communication)
git push origin --force

# 4. Create security advisory for team
```

### Monitoring & Alerts

Add to your deployment monitoring:
```javascript
// backend/utils/secretsAudit.js
async function auditSecretsUsage() {
    // Log all secret access attempts
    // Alert if:
    // - Secrets accessed from unexpected IPs
    // - Multiple failed authentication attempts
    // - Secrets viewed in logs (security incident!)
}
```

### Team Onboarding for Secrets

New developer checklist:
- [ ] Received .env.example from team lead (NOT .env)
- [ ] Created local .env from .example
- [ ] Tested that local dev works with mock keys
- [ ] Understanding of secret handling policies
- [ ] Signed security acknowledgment agreement
