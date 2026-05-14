---
description: MCP 서버를 생성하거나 기존 서버에 MCP 어댑터를 추가할 때 사용하는 스킬. PO 오케스트레이터 연동에 필요한 `/health`, `/mcp` 엔드포인트 구현과 배포 후 등록 스크립트까지 안내한다.
---

# MCP 서버 생성 스킬 — PO 오케스트레이터 연동

## 언제 이 스킬을 사용하는가

- "MCP 서버 만들어줘"
- "MCP 서버 추가해줘"
- "이 서버를 MCP로 등록하고 싶어"
- "PO 오케스트레이터에 연동하고 싶어"

## 스킬 개요

PO 오케스트레이터는 MCP 서버를 등록·관리·제공하는 레지스트리다.
이 스킬은 어떤 언어/프레임워크로 구현하든 PO 오케스트레이터와 연동 가능한 MCP 서버를 만드는 데 필요한 요건을 안내한다.

기존 REST API는 건드리지 않는다. **MCP 어댑터만 추가하면 된다.**

---

## Use Cases

### Use Case 1 — 신규 MCP 서버 생성

- **사용자 목표**: 새로운 기능을 MCP 서버로 만들어 PO 오케스트레이터에 등록
- **트리거 문장**: "이미지 리사이즈 기능을 MCP 서버로 만들어줘"
- **내부 수행 단계**:
  1. 기술 스택 파악 (언어, 프레임워크)
  2. 비즈니스 로직 구현
  3. `GET /health` 엔드포인트 추가
  4. `POST /mcp` 엔드포인트 추가 (tools/list, tools/call)
  5. `inputSchema` 작성
  6. 배포 후 오케스트레이터 등록 스크립트 추가
- **사용 도구**: 해당 언어/프레임워크 기본 도구
- **최종 결과 상태**: PO 오케스트레이터에 ACTIVE 상태로 등록, WEB에서 직접 호출 가능

### Use Case 2 — 기존 서버에 MCP 어댑터 추가

- **사용자 목표**: 기존 REST API 서버를 MCP 서버로 등록
- **트리거 문장**: "우리 기존 서버를 PO 오케스트레이터에 등록하고 싶어"
- **내부 수행 단계**:
  1. 기존 API 파악
  2. `GET /health` 엔드포인트 추가 (없으면)
  3. `POST /mcp` 엔드포인트 추가 — 기존 로직을 호출하는 래퍼
  4. 기존 API 파라미터를 `inputSchema`로 변환
  5. 배포 후 오케스트레이터 등록 스크립트 추가
- **사용 도구**: 해당 언어/프레임워크 기본 도구
- **최종 결과 상태**: 기존 REST API 유지 + MCP 어댑터 추가 완료

---

## 필수 구현 요건

### 1. GET /health

오케스트레이터가 60초마다 폴링하여 서버 상태를 판단한다. 3회 연속 실패 시 INACTIVE 전환.

**Response**
```json
{ "status": "ok" }
```

---

### 2. POST /mcp

JSON-RPC 2.0 형식으로 `method` 값에 따라 분기한다.

#### tools/list

오케스트레이터가 등록 시점에 1회 호출한다. 서버가 제공하는 도구 목록을 반환한다.

**Request**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

**Response**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "tool_name",
        "description": "도구 설명 — 클라이언트가 이 설명을 보고 도구를 선택한다. 명확하게 작성할 것",
        "inputSchema": {
          "type": "object",
          "properties": {
            "param1": { "type": "string", "description": "파라미터 설명" }
          },
          "required": ["param1"]
        }
      }
    ]
  }
}
```

#### tools/call

WEB/WAS 클라이언트가 오케스트레이터를 경유하지 않고 **직접** 호출한다.

**Request**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": { "param1": "value1" }
  }
}
```

**Response**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "실행 결과" }
    ]
  }
}
```

#### 에러 응답

처리할 수 없는 요청에 대해서는 JSON-RPC 표준 에러 형식으로 응답한다.

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

| 코드 | 의미 |
|------|------|
| -32600 | Invalid Request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

---

### 3. inputSchema 작성 지침

오케스트레이터가 `tools/list` 수집 시 `inputSchema`를 함께 저장하여 WEB/WAS에 그대로 전달한다.
클라이언트는 이 정보만 보고 어떤 값을 어떤 형식으로 넘겨야 하는지 판단한다. **명확하게 작성할 것.**

- `description`: 값의 의미를 구체적으로 작성
  - 좋은 예: `"이미지 URL (HTTPS, jpg/png/webp 지원)"`
  - 나쁜 예: `"url"`
- `type`: `string`, `number`, `boolean`, `array`, `object` 중 정확한 타입 사용
- `required`: 필수 파라미터는 반드시 포함

---

### 4. 배포 후 등록 스크립트

배포 성공 시 아래 스크립트를 CI/CD 파이프라인(GitHub Actions 등)의 success 단계에 추가한다.

```sh
curl -X POST https://po-server/api/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "서버 이름",
    "url":  "https://your-server.com"
  }'
```

**도구 변경 시**: 재배포 후 동일하게 register를 호출하면 tools/list가 자동으로 갱신된다.

---

## 구현 체크리스트

### 엔드포인트
- [ ] `GET /health` — `{"status": "ok"}` 반환
- [ ] `POST /mcp` — `tools/list` 응답 구현
- [ ] `POST /mcp` — `tools/call` 응답 구현
- [ ] `POST /mcp` — 알 수 없는 method에 에러 응답 구현

### inputSchema
- [ ] 모든 파라미터에 `type`, `description` 작성
- [ ] `required` 배열 정의

### 배포 및 등록
- [ ] HTTPS 엔드포인트로 외부 접근 가능하게 배포
- [ ] CI/CD success 단계에 register 스크립트 추가

### 검증
- [ ] 등록 후 `GET /api/mcp/servers` 에서 `status: ACTIVE` 확인
- [ ] WEB에서 `POST {url}/mcp` (tools/call) 직접 호출 성공 확인
