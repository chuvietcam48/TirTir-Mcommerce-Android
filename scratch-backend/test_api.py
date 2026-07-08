import urllib.request
import json

url = "http://localhost:5000/api/v1/ai/recommend-routine"
data = json.dumps({"skinType": "Dry", "concerns": ["dry_flaky"]}).encode("utf-8")
headers = {"Content-Type": "application/json"}

req = urllib.request.Request(url, data=data, headers=headers, method="POST")
try:
    with urllib.request.urlopen(req) as res:
        print(json.dumps(json.loads(res.read().decode("utf-8")), indent=2))
except Exception as e:
    print("Error:", e)
