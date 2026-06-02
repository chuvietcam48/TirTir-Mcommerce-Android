"""Quick performance test for AI Service endpoints"""
import requests
import time
import json
import base64
import numpy as np

BASE = "http://localhost:8000"

# 1. Health Check
print("=" * 60)
print("1. HEALTH CHECK")
print("=" * 60)
start = time.time()
r = requests.get(f"{BASE}/health")
elapsed = (time.time() - start) * 1000
data = r.json()
print(f"   Status: {r.status_code}")
print(f"   Response: {json.dumps(data, indent=2)}")
print(f"   Time: {elapsed:.1f}ms")

# 2. Chatbot Test - Multiple intents
print("\n" + "=" * 60)
print("2. CHATBOT TESTS (/chat)")
print("=" * 60)
chat_tests = [
    "da dau dung cushion nao",
    "gia bao nhieu",
    "chao shop",
    "cach dung",
    "ship bao lau",
    "da nhay cam dung duoc khong",
]
for msg in chat_tests:
    start = time.time()
    r = requests.post(f"{BASE}/chat", json={"message": msg})
    elapsed = (time.time() - start) * 1000
    data = r.json()
    intent = data.get("data", {}).get("intent", "?")
    reply = data.get("data", {}).get("message", "?")[:80]
    ptime = data.get("processing_time_ms", "?")
    print(f"   [{intent:12s}] \"{msg}\"")
    print(f"      -> {reply}...")
    print(f"      Server: {ptime}ms | Round-trip: {elapsed:.1f}ms")

# 3. Skin Analysis - No face (error handling test)
print("\n" + "=" * 60)
print("3. SKIN ANALYSIS - NO FACE (error handling)")
print("=" * 60)
import cv2
img = np.zeros((100, 100, 3), dtype=np.uint8)
img[:] = [255, 128, 0]
_, buf = cv2.imencode('.jpg', img)
b64 = base64.b64encode(buf).decode()

start = time.time()
r = requests.post(f"{BASE}/analyze", json={"image_base64": b64})
elapsed = (time.time() - start) * 1000
data = r.json()
print(f"   Status: {r.status_code}")
print(f"   Response: {json.dumps(data, indent=2)}")
print(f"   Time: {elapsed:.1f}ms")

# 4. Skin Analysis - Invalid base64 (error handling)
print("\n" + "=" * 60)
print("4. SKIN ANALYSIS - INVALID BASE64 (error handling)")
print("=" * 60)
start = time.time()
r = requests.post(f"{BASE}/analyze", json={"image_base64": "not_valid_base64!!!"})
elapsed = (time.time() - start) * 1000
data = r.json()
print(f"   Status: {r.status_code}")
print(f"   Response: {json.dumps(data, indent=2)}")
print(f"   Time: {elapsed:.1f}ms")

# 5. Rate Limit Test (fast burst on /chat - 30/min limit)
print("\n" + "=" * 60)
print("5. RATE LIMIT TEST (/chat - 30/min limit)")
print("=" * 60)
successes = 0
rate_limited = 0
for i in range(35):
    r = requests.post(f"{BASE}/chat", json={"message": "hello"})
    if r.status_code == 200:
        successes += 1
    elif r.status_code == 429:
        rate_limited += 1
        break
print(f"   Successes: {successes}")
print(f"   Rate limited (429): {rate_limited}")
if rate_limited:
    print(f"   Rate limiter triggered at request #{successes + 1}")
else:
    print(f"   Rate limiter did NOT trigger after {successes} requests")

# 6. Chatbot - Empty message (validation)
print("\n" + "=" * 60)
print("6. CHATBOT - EMPTY MESSAGE (validation)")
print("=" * 60)
r = requests.post(f"{BASE}/chat", json={"message": ""})
print(f"   Status: {r.status_code}")
try:
    print(f"   Response: {r.json()}")
except:
    print(f"   Response: {r.text[:200]}")

print("\n" + "=" * 60)
print("DONE - All tests completed")
print("=" * 60)
