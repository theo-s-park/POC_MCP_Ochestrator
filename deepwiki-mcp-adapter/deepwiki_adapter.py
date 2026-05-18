"""
DeepWiki MCP Adapter
실제 공개 MCP 서버(mcp.deepwiki.com)를 우리 오케스트레이터 포맷으로 프록시합니다.
- GET  /health  → 200 OK
- POST /mcp     → mcp.deepwiki.com/mcp 로 포워딩
"""
import requests
from flask import Flask, request, jsonify

app = Flask(__name__)

UPSTREAM_URL = "https://mcp.deepwiki.com/mcp"

@app.get("/health")
def health():
    try:
        # upstream이 살아있는지 간단 확인
        requests.post(UPSTREAM_URL, json={"jsonrpc": "2.0", "id": 0, "method": "tools/list", "params": {}},
                      headers={"Accept": "application/json"}, timeout=5)
        return jsonify({"status": "ok"})
    except Exception:
        return jsonify({"status": "ok"})  # adapter 자체는 살아있음


@app.post("/mcp")
def mcp():
    body = request.get_json()
    try:
        resp = requests.post(
            UPSTREAM_URL,
            json=body,
            headers={
                "Accept": "application/json, text/event-stream",
                "Content-Type": "application/json"
            },
            timeout=15
        )
        # SSE 응답이면 첫 번째 data: 라인에서 JSON 추출
        content_type = resp.headers.get("Content-Type", "")
        if "text/event-stream" in content_type:
            for line in resp.text.splitlines():
                if line.startswith("data:"):
                    import json
                    return jsonify(json.loads(line[5:].strip()))
            return jsonify({"jsonrpc": "2.0", "id": body.get("id", 1), "error": {"code": -32000, "message": "Empty SSE response"}})
        return jsonify(resp.json())
    except Exception as e:
        return jsonify({
            "jsonrpc": "2.0",
            "id": body.get("id", 1),
            "error": {"code": -32000, "message": str(e)}
        }), 500


if __name__ == "__main__":
    print("DeepWiki MCP Adapter starting on port 8082...")
    print(f"Proxying to: {UPSTREAM_URL}")
    app.run(host="0.0.0.0", port=8082, debug=False)
