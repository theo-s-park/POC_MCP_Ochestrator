# MCP 통신 규약 초안 v0.1

## 0. 기반 표준

본 규약은 **Model Context Protocol (MCP) 공식 표준 Specification `2024-11-05`** 에 의거한다.

- 공식 스펙: https://modelcontextprotocol.io
- 전송 방식: **Streamable HTTP**
- 메시지 포맷: **JSON-RPC 2.0**

### MCP 표준 구성

MCP 표준은 세 가지 프리미티브(Primitive)로 구성된다.

| 프리미티브 | 설명 | 본 규약 사용 여부 |
|-----------|------|----------------|
| **Tools** | LLM 또는 클라이언트가 호출할 수 있는 함수 | ✅ 사용 |
| **Resources** | 서버가 노출하는 데이터/파일 | 미사용 |
| **Prompts** | 서버가 제공하는 프롬프트 템플릿 | 미사용 |

### MCP 표준 통신 흐름

모든 MCP 통신은 아래 순서를 따른다.

```
1. initialize    클라이언트 ↔ 서버 핸드셰이크, 프로토콜 버전 협상
2. tools/list    클라이언트가 서버의 사용 가능한 Tool 목록 조회
3. tools/call    클라이언트가 특정 Tool 실행 요청
```

### 메시지 포맷 (JSON-RPC 2.0)

**요청**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

**정상 응답**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": { }
}
```

**에러 응답**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32600,
    "message": "Invalid Request"
  }
}
```

---

## 1. MCP 서버 구현 요건

MCP 서버는 아래 두 엔드포인트를 반드시 노출해야 한다.

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/health` | GET | 서버 상태 확인 |
| `/mcp` | POST | MCP 표준 JSON-RPC 2.0 처리 |

**`GET /health` 응답**
```json
{
  "status": "ok"
}
```

---

## 2. PO 백엔드 Inbound

> 외부(GitHub Actions, 바이브 코딩 플랫폼 등)에서 PO 백엔드로 들어오는 요청

### MCP 서버 등록

Vercel 배포 성공 시 GitHub Actions에서 호출한다.

**Request**
```http
POST /api/mcp/servers/register
Authorization: Bearer {PAT}
Content-Type: application/json
```
```json
{
  "name": "image-resize-tool",
  "url": "https://image-resize-tool.vercel.app",
  "description": "이미지 크기 조정 및 포맷 변환",
  "version": "1.0.0"
}
```

**Response**
```json
{
  "serverId": "uuid",
  "status": "PENDING"
}
```

---

### MCP 서버 삭제

등록된 MCP 서버를 제거한다.

**Request**
```http
DELETE /api/mcp/servers/{serverId}
Authorization: Bearer {PAT}
```

**Response**
```json
{
  "status": "ok"
}
```

---

### MCP 서버 목록 조회

프론트엔드에서 사용 가능한 MCP 서버 목록을 조회한다.

**Request**
```http
GET /api/mcp/servers
Authorization: Bearer {PAT}
```

**Response**
```json
{
  "servers": [
    {
      "id": "uuid",
      "name": "image-resize-tool",
      "url": "https://image-resize-tool.vercel.app",
      "description": "이미지 크기 조정 및 포맷 변환",
      "status": "ACTIVE",
      "tools": [
        { "name": "resize_image", "description": "이미지 크기 조정" }
      ],
      "registeredAt": "2026-05-07T10:00:00Z",
      "lastHealthCheck": "2026-05-07T10:30:00Z"
    }
  ]
}
```

---

### 사용자 MCP 선택 저장

사용자가 사용할 MCP 서버 목록을 저장한다.

**Request**
```http
POST /api/mcp/users/{userId}/selections
Authorization: Bearer {PAT}
Content-Type: application/json
```
```json
{
  "serverIds": ["uuid1", "uuid2"]
}
```

**Response**
```json
{
  "status": "ok"
}
```

---

## 3. PO 백엔드 Outbound

> PO 백엔드에서 등록된 MCP 서버로 나가는 요청

### 헬스체크 폴링

**Request**
```http
GET {server.url}/health
```

| 조건 | 처리 |
|------|------|
| 200 OK | status = `ACTIVE` |
| 타임아웃 또는 비정상 응답 3회 연속 | status = `INACTIVE` |

- 폴링 주기: **5분**

---

### tools/list 조회

서버 등록 시 및 주기적으로 Tool 목록을 갱신한다.

**Request**
```http
POST {server.url}/mcp
Content-Type: application/json
```
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
        "name": "resize_image",
        "description": "이미지 크기 조정",
        "inputSchema": {
          "type": "object",
          "properties": {
            "url": { "type": "string" },
            "width": { "type": "number" },
            "height": { "type": "number" }
          },
          "required": ["url", "width", "height"]
        }
      }
    ]
  }
}
```

---

## 4. 외부 에이전트 연동 규약

MCP 표준 `2024-11-05`를 준수하는 모든 클라이언트(Claude, GPT 등)와 호환된다.

**Request**
```http
POST /mcp
Authorization: Bearer {PAT}
Content-Type: application/json
```

PAT 검증 방식은 별도 담당자 구현체에 의거한다.

---

## 미결 사항

- [ ] PAT 포맷 및 검증 방식 (담당자 협의)
- [ ] 헬스체크 실패 기준 (3회? 5회?)
- [ ] tools/list 갱신 주기
- [ ] userId 식별자 (PO 기존 시스템 어떤 값 쓸지)
- [ ] 서버 등록 시 소유자 식별 방식
