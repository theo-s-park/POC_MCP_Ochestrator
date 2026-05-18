import re
import json
import requests
from flask import Flask, request, jsonify

app = Flask(__name__)

SERVER_INFO = {"name": "weather-mcp-server", "version": "1.0.0"}
PROTOCOL_VERSION = "2024-11-05"

KO_TO_EN = {
    "서울": "Seoul", "부산": "Busan", "인천": "Incheon", "대구": "Daegu",
    "대전": "Daejeon", "광주": "Gwangju", "울산": "Ulsan", "수원": "Suwon",
    "제주": "Jeju", "강릉": "Gangneung", "춘천": "Chuncheon",
    "도쿄": "Tokyo", "오사카": "Osaka", "베이징": "Beijing", "상하이": "Shanghai",
    "뉴욕": "New York", "런던": "London", "파리": "Paris", "시드니": "Sydney",
}

WMO_CODES = {
    0: "맑음", 1: "대체로 맑음", 2: "부분적으로 흐림", 3: "흐림",
    45: "안개", 48: "착빙 안개",
    51: "가벼운 이슬비", 53: "이슬비", 55: "짙은 이슬비",
    61: "약한 비", 63: "비", 65: "강한 비",
    71: "약한 눈", 73: "눈", 75: "강한 눈",
    80: "소나기", 81: "강한 소나기", 82: "폭우",
    95: "천둥번개", 99: "우박 동반 천둥번개"
}

TOOLS = [
    {
        "name": "get_current_weather",
        "description": "도시의 현재 날씨(온도, 습도, 날씨 상태, 풍속)를 실시간으로 조회합니다.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "날씨를 조회할 도시 이름 (예: 서울, Tokyo, New York)"}
            },
            "required": ["city"]
        }
    },
    {
        "name": "get_weather_forecast",
        "description": "도시의 3일간 날씨 예보(최고/최저 기온, 날씨 상태)를 조회합니다.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "city": {"type": "string", "description": "예보를 조회할 도시 이름"}
            },
            "required": ["city"]
        }
    }
]

RESOURCES = [
    {
        "uri": "weather://supported-cities",
        "name": "지원 도시 목록",
        "description": "한국어 → 영어 도시명 매핑 테이블",
        "mimeType": "application/json"
    },
    {
        "uri": "weather://wmo-codes",
        "name": "WMO 날씨 코드 참조표",
        "description": "WMO 표준 날씨 코드와 한국어 설명 매핑",
        "mimeType": "application/json"
    }
]

RESOURCE_TEMPLATES = [
    {
        "uriTemplate": "weather://current/{city}",
        "name": "현재 날씨",
        "description": "특정 도시의 현재 날씨를 실시간으로 조회합니다.",
        "mimeType": "text/plain"
    },
    {
        "uriTemplate": "weather://forecast/{city}",
        "name": "3일 날씨 예보",
        "description": "특정 도시의 3일간 날씨 예보를 조회합니다.",
        "mimeType": "text/plain"
    }
]

PROMPTS = [
    {
        "name": "weather-report",
        "description": "특정 도시의 현재 날씨와 예보를 포함한 날씨 보고서를 요청하는 프롬프트",
        "arguments": [
            {"name": "city", "description": "날씨를 조회할 도시 이름", "required": True}
        ]
    },
    {
        "name": "weather-comparison",
        "description": "두 도시의 현재 날씨를 비교하는 프롬프트",
        "arguments": [
            {"name": "city1", "description": "첫 번째 도시", "required": True},
            {"name": "city2", "description": "두 번째 도시", "required": True}
        ]
    }
]


# ── 공통 데이터 조회 ──────────────────────────────────────────

def get_coordinates(city: str):
    city = KO_TO_EN.get(city.strip(), city)
    resp = requests.get(
        "https://geocoding-api.open-meteo.com/v1/search",
        params={"name": city, "count": 1, "language": "ko"},
        timeout=5
    )
    results = resp.json().get("results", [])
    if not results:
        return None
    r = results[0]
    return r["latitude"], r["longitude"], r.get("name", city), r.get("country", "")


def get_weather_data(lat, lon):
    resp = requests.get(
        "https://api.open-meteo.com/v1/forecast",
        params={
            "latitude": lat,
            "longitude": lon,
            "current": "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
            "daily": "temperature_2m_max,temperature_2m_min,weather_code",
            "forecast_days": 3,
            "timezone": "auto"
        },
        timeout=5
    )
    return resp.json()


def fetch_current_weather_text(city: str) -> str:
    coords = get_coordinates(city)
    if not coords:
        return f"'{city}' 도시를 찾을 수 없습니다."
    lat, lon, city_name, country = coords
    data = get_weather_data(lat, lon)
    cur = data["current"]
    desc = WMO_CODES.get(cur["weather_code"], f"코드 {cur['weather_code']}")
    return (
        f"[{city_name}, {country} 현재 날씨]\n"
        f"온도: {cur['temperature_2m']}°C\n"
        f"습도: {cur['relative_humidity_2m']}%\n"
        f"날씨: {desc}\n"
        f"풍속: {cur['wind_speed_10m']} km/h"
    )


def fetch_forecast_text(city: str) -> str:
    coords = get_coordinates(city)
    if not coords:
        return f"'{city}' 도시를 찾을 수 없습니다."
    lat, lon, city_name, country = coords
    data = get_weather_data(lat, lon)
    daily = data["daily"]
    lines = [f"[{city_name}, {country} 3일 예보]"]
    for i in range(3):
        fc_desc = WMO_CODES.get(daily["weather_code"][i], "알 수 없음")
        lines.append(f"{daily['time'][i]}: {daily['temperature_2m_min'][i]}°C ~ {daily['temperature_2m_max'][i]}°C, {fc_desc}")
    return "\n".join(lines)


# ── tools/call ───────────────────────────────────────────────

def call_tool(name, args):
    city = args.get("city", "")
    try:
        if name == "get_current_weather":
            return {"content": [{"type": "text", "text": fetch_current_weather_text(city)}]}
        if name == "get_weather_forecast":
            return {"content": [{"type": "text", "text": fetch_forecast_text(city)}]}
    except Exception as e:
        return {"content": [{"type": "text", "text": f"날씨 조회 실패: {str(e)}"}]}
    return {"content": [{"type": "text", "text": "알 수 없는 도구입니다."}]}


# ── resources/read ───────────────────────────────────────────

def read_resource(uri: str):
    if uri == "weather://supported-cities":
        return {"uri": uri, "mimeType": "application/json",
                "text": json.dumps(KO_TO_EN, ensure_ascii=False, indent=2)}

    if uri == "weather://wmo-codes":
        return {"uri": uri, "mimeType": "application/json",
                "text": json.dumps({str(k): v for k, v in WMO_CODES.items()}, ensure_ascii=False, indent=2)}

    m = re.match(r"weather://current/(.+)", uri)
    if m:
        city = m.group(1)
        try:
            text = fetch_current_weather_text(city)
        except Exception as e:
            text = f"조회 실패: {str(e)}"
        return {"uri": uri, "mimeType": "text/plain", "text": text}

    m = re.match(r"weather://forecast/(.+)", uri)
    if m:
        city = m.group(1)
        try:
            text = fetch_forecast_text(city)
        except Exception as e:
            text = f"조회 실패: {str(e)}"
        return {"uri": uri, "mimeType": "text/plain", "text": text}

    return None


# ── prompts/get ──────────────────────────────────────────────

def get_prompt(name: str, arguments: dict):
    if name == "weather-report":
        city = arguments.get("city", "도시")
        return {
            "description": f"{city} 날씨 보고서",
            "messages": [{
                "role": "user",
                "content": {"type": "text", "text": (
                    f"{city}의 현재 날씨와 앞으로 3일간의 날씨 예보를 알려주세요. "
                    f"get_current_weather와 get_weather_forecast 도구를 활용해서 상세하게 설명해주세요."
                )}
            }]
        }

    if name == "weather-comparison":
        city1 = arguments.get("city1", "도시1")
        city2 = arguments.get("city2", "도시2")
        return {
            "description": f"{city1} vs {city2} 날씨 비교",
            "messages": [{
                "role": "user",
                "content": {"type": "text", "text": (
                    f"{city1}과 {city2}의 현재 날씨를 비교해주세요. "
                    f"두 도시 모두 get_current_weather로 조회한 뒤, "
                    f"온도·습도·날씨 상태를 표 형식으로 비교해주세요."
                )}
            }]
        }

    return None


# ── HTTP endpoints ───────────────────────────────────────────

@app.get("/health")
def health():
    return jsonify({"status": "ok"})


@app.post("/mcp")
def mcp():
    body = request.get_json()
    method = body.get("method")
    req_id = body.get("id", 1)

    if method == "initialize":
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {
                "tools": {},
                "resources": {"listChanged": False},
                "prompts": {}
            },
            "serverInfo": SERVER_INFO
        }})

    if method == "ping":
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {}})

    if method == "tools/list":
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {"tools": TOOLS}})

    if method == "tools/call":
        name = body.get("params", {}).get("name")
        args = body.get("params", {}).get("arguments", {})
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": call_tool(name, args)})

    if method == "resources/list":
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {
            "resources": RESOURCES,
            "resourceTemplates": RESOURCE_TEMPLATES
        }})

    if method == "resources/read":
        uri = body.get("params", {}).get("uri", "")
        content = read_resource(uri)
        if content is None:
            return jsonify({"jsonrpc": "2.0", "id": req_id,
                            "error": {"code": -32002, "message": f"Resource not found: {uri}"}})
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {"contents": [content]}})

    if method == "prompts/list":
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": {"prompts": PROMPTS}})

    if method == "prompts/get":
        name = body.get("params", {}).get("name")
        arguments = body.get("params", {}).get("arguments", {})
        result = get_prompt(name, arguments)
        if result is None:
            return jsonify({"jsonrpc": "2.0", "id": req_id,
                            "error": {"code": -32002, "message": f"Prompt not found: {name}"}})
        return jsonify({"jsonrpc": "2.0", "id": req_id, "result": result})

    return jsonify({"jsonrpc": "2.0", "id": req_id,
                    "error": {"code": -32601, "message": f"Method not found: {method}"}})


if __name__ == "__main__":
    print("Weather MCP Server (Open-Meteo) starting on port 8081...")
    app.run(host="0.0.0.0", port=8081, debug=False)
