# MCP 서버 구현 및 등록 가이드

이 문서는 **MCP 관리 서버에 연결될 서버를 개발하는 개발자**를 대상으로 한다.  
구현해야 할 엔드포인트와 데이터 포맷을 정의한다.

> **확정된 내용만 기술한다.** 인증 연동 방식은 별도 확정 예정이며 이 문서에 포함하지 않는다.

---

## 역할 분리

| 역할 | 담당 | 내용 |
|---|---|---|
| 서버 인프라 등록 (Lambda 생성 · URL 발급 등) | 운영팀 gray님 문의 | AWS Lambda 함수 생성, API Gateway 연결 등 인프라 작업 |
| 서버 구현 (엔드포인트 코드) | 이 문서 참고 | MCP 관리 서버가 호출할 HTTP 엔드포인트 구현 |

Lambda 함수가 생성되고 외부에서 접근 가능한 Base URL이 확보되면, 아래 스펙대로 엔드포인트를 구현하고 Backoffice에 등록한다.

---

## 1. 서버 타입 선택

등록 시 `type` 필드로 통신 방식을 선택한다.

| type | 통신 방식 | 사용 대상 |
|---|---|---|
| `MCP` | JSON-RPC 2.0, 단일 엔드포인트 `POST /mcp` | **신규 개발 서버 (Lambda 포함)** — 권장 |
| `WEBAPP` | REST HTTP, 엔드포인트가 메서드 역할 | 기존 REST 서버를 수정 없이 붙일 때 |

**새로 만드는 Lambda 함수는 MCP 타입을 사용한다.**  
`POST /mcp` 하나만 구현하면 되며, JSON-RPC 라우팅만 추가하면 기존 비즈니스 로직을 그대로 유지할 수 있다.

---

## 2. MCP 타입 구현 요건 (JSON-RPC 2.0)

### 필수 엔드포인트

| 엔드포인트 | 메서드 | 필수 | 설명 |
|---|---|---|---|
| `/mcp` | POST | O | JSON-RPC 2.0. 모든 메서드를 이 경로로 수신 |

### 헬스체크 — ping

MCP 관리 서버는 20초마다 `ping` 메서드를 호출해 서버 생존 여부를 확인한다.  
3회 연속 실패 시 `INACTIVE`로 전환된다.

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "ping", "params": {} }

// Response — 정상 응답
{ "jsonrpc": "2.0", "id": 1, "result": {} }

// Response — ping 미구현 시 허용되는 응답
{ "jsonrpc": "2.0", "id": 1, "error": { "code": -32601, "message": "Method not found" } }
```

> `ping`을 구현하지 않아도 `error` 응답을 반환하는 한 헬스체크를 통과한다.  
> **권장: `ping` 메서드를 구현하고 `{"result": {}}`를 반환한다.**

### POST /mcp — tools/list

등록 시 1회 자동 호출. **1개 이상 반환 필수.**

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {
        "name": "get_current_weather",
        "description": "도시의 현재 날씨를 조회합니다.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "city": { "type": "string", "description": "도시 이름" }
          },
          "required": ["city"]
        }
      }
    ]
  }
}
```

### POST /mcp — tools/call

```json
// Request
{
  "jsonrpc": "2.0", "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": {
      "city": "서울",
      "credit": { "serviceType": "GPT3", "deductCredit": 50 }
    }
  }
}

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "[서울 현재 날씨] 온도: 25°C ..." }
    ]
  }
}
```

> `arguments.credit`은 MCP 관리 서버가 자동 주입한다. [→ 4절 참고](#4-credit-처리)

### POST /mcp — resources/list (선택)

미구현 또는 빈 배열 반환 시 등록 실패로 처리하지 않는다.  
`mimeType: image/*` 리소스가 있으면 썸네일로 자동 추출한다.

```json
// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "resources": [
      { "uri": "image://logo.png", "name": "App Logo", "mimeType": "image/png" }
    ]
  }
}
```

### POST /mcp — resources/read (선택)

`resources/list`를 구현했다면 함께 구현한다.

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "resources/read", "params": { "uri": "image://logo.png" } }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "contents": [
      { "uri": "image://logo.png", "mimeType": "image/png", "blob": "base64..." }
    ]
  }
}
```

### Lambda 구현 예시 (Python)

```python
import json

TOOLS = [
    {
        "name": "my_tool",
        "description": "도구 설명",
        "inputSchema": {
            "type": "object",
            "properties": {
                "input": {"type": "string", "description": "입력값"}
            },
            "required": ["input"]
        }
    }
]

def handler(event, context):
    body = json.loads(event.get("body", "{}"))
    method = body.get("method")
    req_id = body.get("id", 1)

    if method == "tools/list":
        return ok(req_id, {"tools": TOOLS})

    if method == "tools/call":
        args = body["params"]["arguments"].copy()
        credit = args.pop("credit", None)          # credit은 비즈니스 로직에 전달하지 않음

        result_text = execute(body["params"]["name"], args)

        if credit:
            deduct_credit(credit, event["headers"].get("Authorization"))

        return ok(req_id, {"content": [{"type": "text", "text": result_text}]})

    # ping 포함 미구현 메서드 — 이 응답이면 헬스체크 통과
    return ok(req_id, None, error={"code": -32601, "message": "Method not found"})


def ok(req_id, result, error=None):
    body = {"jsonrpc": "2.0", "id": req_id}
    if error:
        body["error"] = error
    else:
        body["result"] = result
    return {"statusCode": 200, "body": json.dumps(body)}


def execute(tool_name, args):
    # 비즈니스 로직
    ...


def deduct_credit(credit, authorization):
    # POST /credits/deduct → 크레딧 서버
    # Authorization 헤더를 그대로 전달해 사용자 식별
    ...
```

---

## 3. WEBAPP 타입 구현 요건 (REST)

JSON-RPC 래퍼 없이 엔드포인트 자체가 메서드 역할을 한다.  
**데이터 구조는 MCP 타입과 동일하다.**

### 필수 엔드포인트

| 엔드포인트 | 메서드 | 필수 | 설명 |
|---|---|---|---|
| `/tools` | GET | O | 등록 시 1회 자동 호출. 1개 이상 반환 필수 |
| `/tools/call` | POST | O | Tool 실행 |
| `/resources` | GET | 선택 | Resource 목록 |
| `/resources/read` | GET | 선택 | `?uri=` 쿼리 파라미터 |

### 헬스체크

MCP 관리 서버는 20초마다 `GET {baseUrl}` (루트 URL)을 호출한다.  
HTTP 2xx 응답이면 정상으로 간주한다. 응답 바디는 확인하지 않는다.

### GET /tools

```json
{
  "tools": [
    {
      "name": "convert_hwp",
      "description": "HWP 파일을 PDF로 변환합니다.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "fileUrl": { "type": "string" }
        },
        "required": ["fileUrl"]
      }
    }
  ]
}
```

### POST /tools/call

```json
// Request
{
  "name": "convert_hwp",
  "arguments": {
    "fileUrl": "https://example.com/doc.hwp",
    "credit": { "serviceType": "GPT3", "deductCredit": 50 }
  }
}

// Response
{
  "content": [
    { "type": "text", "text": "변환 완료. 다운로드 URL: https://..." }
  ]
}
```

> `arguments.credit`은 MCP 관리 서버가 자동 주입한다. [→ 4절 참고](#4-credit-처리)

### GET /resources (선택)

```json
{
  "resources": [
    { "uri": "image://logo.png", "name": "App Logo", "mimeType": "image/png" }
  ]
}
```

### GET /resources/read?uri={uri} (선택)

```json
{
  "contents": [
    { "uri": "image://logo.png", "mimeType": "image/png", "blob": "base64..." }
  ]
}
```

---

## 4. Credit 처리

`tools/call` 요청의 `arguments`에는 항상 `credit` 필드가 포함된다.  
MCP 관리 서버가 자동 주입하며, 서버 개발자가 별도로 요청할 필요 없다.

```json
"arguments": {
  // ... 비즈니스 파라미터 ...
  "credit": {
    "serviceType": "GPT3",
    "deductCredit": 50
  }
}
```

**서버가 해야 할 것**

1. `arguments.credit` 추출 (비즈니스 로직에 전달하지 않음)
2. 비즈니스 로직 실행
3. 성공 시 → 크레딧 서버에 차감 요청
4. 실패 시 → 차감 없음

```python
def call_tool(name, args):
    credit = args.pop("credit", None)
    result = execute_business_logic(name, args)
    if result["success"] and credit:
        deduct_credit(
            service_type=credit["serviceType"],
            deduct_credit=credit["deductCredit"],
            token=request.headers.get("Authorization")
        )
    return result
```

**사용자 식별**

MCP 관리 서버가 `Authorization: Bearer {token}` 헤더를 그대로 전달한다.  
크레딧 차감 시 이 토큰을 크레딧 서버에 함께 전달하면 사용자가 특정된다.

| 항목 | 전달 방식 |
|---|---|
| 사용자 식별 | `Authorization: Bearer {token}` 헤더 |
| 차감 금액 | `arguments.credit.deductCredit` |
| 서비스 타입 | `arguments.credit.serviceType` |

---

## 5. 등록

등록은 **MCP 관리 서버 Backoffice**에서 수동으로 진행한다.

1. Backoffice 접속 → "새 MCP 서버 등록" 입력란에 URL 입력
2. 등록 버튼 클릭
3. 관리 서버가 자동으로 capabilities 수집 후 `ACTIVE` 상태로 전환

등록 시 자동으로 처리되는 것:

| type | 자동 수집 | 실패 시 |
|---|---|---|
| MCP | `tools/list` (필수), `resources/list` (선택) | `REGISTRATION_FAILED` |
| WEBAPP | `GET /tools` (필수), `GET /resources` (선택) | `REGISTRATION_FAILED` |

---

## 6. 헬스체크 & 상태 전환

| type | 헬스체크 방법 | 간격 |
|---|---|---|
| MCP | `POST /mcp` — `{"method":"ping"}` | 20초 |
| WEBAPP | `GET {baseUrl}` (루트 URL 2xx 확인) | 20초 |

3회 연속 실패 시 `INACTIVE`, 이후 복구 확인 시 `ACTIVE` 자동 전환.

---

## 7. ServerStatus

| 값 | 설명 |
|---|---|
| `PENDING` | 등록 요청 수신, 수집 진행 중 |
| `ACTIVE` | 정상 등록 완료 및 헬스체크 통과 |
| `INACTIVE` | 헬스체크 3회 연속 실패 |
| `REGISTRATION_FAILED` | tools 수집 실패 또는 빈 배열 반환 |

---

## 참고

- MCP 공식 스펙: https://modelcontextprotocol.io
- JSON-RPC 2.0: https://www.jsonrpc.org/specification
- 인증 방식은 woodie님의 가이드 참고
