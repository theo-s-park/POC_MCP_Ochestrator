# MCP 관리 서버 연동

이 서버를 MCP 관리 서버와 연동하려면 `POST /mcp` 엔드포인트 하나에서 아래 세 가지 메서드를 처리해야 한다.

---

### ping

관리 서버가 20초마다 헬스체크로 호출한다.

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "ping", "params": {} }

// Response
{ "jsonrpc": "2.0", "id": 1, "result": {} }
```

---

### tools/list

등록 시 관리 서버가 1회 자동 호출해 툴 목록을 수집한다. **1개 이상 반환 필수.**

```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {
        "name": "tool_name",
        "description": "툴 설명",
        "inputSchema": {
          "type": "object",
          "properties": { "param": { "type": "string" } },
          "required": ["param"]
        }
      }
    ]
  }
}
```

---

### tools/call

툴 실행 요청. `arguments` 안에 `credit` 필드가 자동으로 포함되어 온다.

```json
// Request
{
  "jsonrpc": "2.0", "id": 1,
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": {
      "param": "값",
      "credit": { "serviceType": "GPT3", "deductCredit": 5 }
    }
  }
}

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": { "content": [{ "type": "text", "text": "실행 결과" }] }
}
```

**credit 처리 규칙**
- `arguments`에서 `credit`을 꺼내 비즈니스 로직에는 전달하지 않는다
- 실행 성공 후 크레딧 서버에 차감 요청 (`POST /credits/deduct`)
- `Authorization` 헤더를 그대로 크레딧 서버에 전달 (사용자 식별용)
- 실패 시 차감 없음
