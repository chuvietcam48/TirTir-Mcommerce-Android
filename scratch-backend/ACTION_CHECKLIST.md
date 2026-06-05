# ✅ Action Checklist - Code Review Fixes Implementation

## 🚨 CRITICAL - Do Before Production

- [ ] **ROTATE GEMINI_API_KEY** (Exposed)
  - Go to: https://console.cloud.google.com/apis/credentials
  - Disable: `AIzaSyBwLT4mNQfJjCJCOQ0y9HqizIRNFtQrED8`
  - Create new key
  - Update .env and docker-compose.yml
  - Delete old key
  - Test locally before deploying

- [ ] **GENERATE NEW AI_SERVICE_API_KEY**
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
  ```
  - Copy output
  - Add to .env as `AI_SERVICE_API_KEY=<output>`
  - Add to docker-compose.yml/prod environment
  - Add to deployment CI/CD variables

- [ ] **VERIFY REDIS CONNECTION** (Local Dev)
  ```bash
  # If doing local development:
  docker-compose up -d redis
  # OR
  redis-cli ping  # Should output: PONG
  ```

## ✅ Verify Fixes Applied

- [ ] **Schema Fix** - Check [backend/models/chat.history.model.js](backend/models/chat.history.model.js)
  - Should have `recommendations`, `cheaper_alternative`, `filters`, `scoring_formula` fields
  - Should have `strict: false` option

- [ ] **Auth Fix** - Check [backend/chatbot/main.py](backend/chatbot/main.py)
  - Should have `ENVIRONMENT` variable
  - Should fail in production if `AI_API_KEY` is empty
  - Should show clear log messages

- [ ] **Redis Fix** - Check [backend/chatbot/chatbot_engine.py](backend/chatbot/chatbot_engine.py)
  - Should show Redis connection status clearly
  - Should show helpful error message with next steps

- [ ] **Test File** - Check [backend/__tests__/chat.e2e.test.js](backend/__tests__/chat.e2e.test.js)
  - Run tests: `npm test -- __tests__/chat.e2e.test.js`
  - All tests should pass ✅

- [ ] **Security Docs** - Check [SECURITY.md](SECURITY.md)
  - Review with team
  - Choose secret management strategy

## 📝 Update Files

- [ ] Create `.env.example` locally from provided template ✅ (Done)
- [ ] Verify `.gitignore` includes `.env` ✅ (Already done)
- [ ] Update `docker-compose.yml`:
  ```yaml
  # Find environment section for chatbot service
  environment:
    - AI_SERVICE_API_KEY=${AI_SERVICE_API_KEY}  # Load from .env
    - REDIS_URL=${REDIS_URL}
    - NODE_ENV=${NODE_ENV}
  ```

## 🧪 Testing

**Local Testing:**
```bash
# Start all services
docker-compose up -d

# Run E2E tests
npm test -- __tests__/chat.e2e.test.js

# Manual test with auth header
curl -X POST http://localhost:8001/chat \
  -H "X-API-Key: your_generated_key" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'

# Check MongoDB for full recommendation data
mongosh
> db.chathistories.findOne()
# Should show: recommendations, cheaper_alternative, filters, scoring_formula
```

## 👥 Team Communication

- [ ] Notify team of security fixes
- [ ] Share SECURITY.md with team
- [ ] Update team's .env files with new keys
- [ ] Brief team on secret handling policy

## 🚀 Deploy Checklist

**Before Staging:**
- [ ] All tests passing locally
- [ ] GEMINI_API_KEY rotated
- [ ] AI_SERVICE_API_KEY generated and set
- [ ] REDIS_URL configured
- [ ] Secret management strategy chosen
- [ ] Code review passed

**Before Production:**
- [ ] Staging deployment successful
- [ ] All services communicating correctly
- [ ] Tests running in CI/CD
- [ ] Secret injection working via CI/CD
- [ ] Monitoring/alerting on secret access configured
- [ ] Team trained on incident response

## 📊 Verification Commands

**Verify Schema:**
```bash
mongosh
> db.chathistories.findOne()
# Check for: recommendations[], cheaper_alternative, filters, scoring_formula
```

**Verify Auth:**
```bash
# Should fail without key
curl -X POST http://localhost:8001/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'
# Response: 403 Forbidden (or 500 if key not configured)

# Should work with key
curl -X POST http://localhost:8001/chat \
  -H "X-API-Key: your_generated_key" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'
# Response: 200 OK with SSE stream
```

**Verify Redis:**
```bash
# Check logs
docker logs <chatbot-container-id> | grep -i redis
# Should show: "✅ Redis session store connected"
# OR: "⚠️ Redis unavailable. For persistence, ensure Redis is running"
```

**Verify Tests:**
```bash
npm test -- __tests__/chat.e2e.test.js
# Should show: 7 passing
```

## 📖 Documentation

**Read these files:**
1. [CODE_REVIEW_FIXES.md](CODE_REVIEW_FIXES.md) - Full explanation of all fixes
2. [SECURITY.md](SECURITY.md) - Secret management and rotation guide
3. [.env.example](.env.example) - Environment variable template

## ❓ Questions Before Deployment

Ask team lead:
1. Which secret management system should we use? (AWS Secrets Manager / Vault / Docker Secrets)
2. What's the Gemini API quota limit? (Understand cost implications)
3. Should we implement conversation feedback collection?
4. Any compliance requirements? (GDPR, HIPAA, PCI-DSS, etc.)
5. What's the plan for secret rotation? (Every 30/90/365 days?)

---

**Priority Order:**
1. 🔴 Rotate GEMINI_API_KEY immediately
2. 🔴 Generate AI_SERVICE_API_KEY 
3. 🔴 Run tests and verify fixes
4. 🟡 Choose and implement secret management
5. 🟡 Brief team and deploy

**Estimated Time:** 2-3 hours for all fixes + testing
