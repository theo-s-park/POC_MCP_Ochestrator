# MCP 통신 규약 v0.4

---

## 0. 기반 표준

본 규약은 **Model Context Protocol (MCP) 공식 표준 Specification `2024-11-05`** 에 의거한다.

- 공식 스펙: https://modelcontextprotocol.io
- 전송 방식: **Streamable HTTP**
- 메시지 포맷: **JSON-RPC 2.0**

### MCP 표준 구성

| 프리미티브 | 설명 | 본 규약 사용 여부 |
|---|---|---|
| **Tools** | LLM 또는 클라이언트가 호출할 수 있는 함수 | 사용 |
| **Resources** | 서버가 노출하는 데이터/파일 | 사용 (등록 시 수집, 이미지 리소스는 썸네일 자동 추출) |
| **Prompts** | 서버가 제공하는 프롬프트 템플릿 | 미사용 |

### MCP 표준 통신 흐름

1. **initialize** - 클라이언트 - 서버 핸드셰이크, 프로토콜 버전 협상
2. **tools/list** - 클라이언트가 서버의 사용 가능한 Tool 목록 조회
3. **tools/call** - 클라이언트가 특정 Tool 실행 요청
4. **resources/list** - 클라이언트가 서버가 노출하는 리소스 목록 조회
5. **resources/read** - 클라이언트가 특정 리소스 내용 조회

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
  "result": {}
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

| 에러 코드 | 의미 |
|---|---|
| -32600 | Invalid Request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

---

## 1. 아키텍처 개요

MCP 관리 서버는 MCP 서버를 **등록 - 관리 - 제공**하는 레지스트리 역할을 담당한다.
`tools/call` 실행은 각 클라이언트(WEB 등)가 MCP 서버에 **직접** 호출한다.

### 역할 분리

| 주체 | 역할 |
|---|---|
| **MCP 서버** | 도구 구현 및 HTTP 래퍼 제공 |
| **MCP 관리 서버** | 등록 수신 - tools/list, resources/list 수집 - 헬스체크 - 리스트 제공 - resources/read 프록시 |
| **WAS** (User, Credit) | 리스트 조회 및 MCP 서버 직접 호출 |
| **OSS** (Management) | 리스트 조회 및 관리 |
| **WEB** (User) | 공개 앱 목록 조회, 관리 서버 경유 resources/read 호출 |

---

## 2. MCP 서버 구현 요건

MCP 서버는 아래 두 엔드포인트를 반드시 노출해야 한다.
기존 REST API는 건드리지 않는다. **MCP 어댑터만 추가하면 된다.**

### 도구 명세 흐름

MCP 서버는 등록 시점에 자신이 제공하는 도구의 명세(`tools/list`)를 관리 서버에 전달해야 한다. 명세에는 도구 이름, 설명, 그리고 각 파라미터의 타입 - 설명 - 필수 여부(`inputSchema`)가 포함된다.

관리 서버는 이를 수집 - 저장하여 클라이언트(WEB, WAS)에게 그대로 제공한다.

### 리소스 명세 흐름

`resources/list`는 선택 구현이다. 서버가 파일 - DB 레코드 - 이미지 등의 데이터를 노출할 때 사용한다.
`mimeType: image/*` 리소스가 있으면 관리 서버가 해당 이미지를 앱 썸네일로 자동 추출한다.

| 엔드포인트 | 메서드 | 설명 |
|---|---|---|
| `/health` | GET | 서버 상태 확인. 관리 서버에서 60초마다 폴링하여 ACTIVE/INACTIVE 판단 |
| `/mcp` | POST | MCP 표준 JSON-RPC 2.0 처리 (tools/list, tools/call, resources/list, resources/read) |

---

### GET /health

- **호출부**: MCP 관리 서버 (60초 주기 폴링)

**Request**
```
GET /health
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{ "status": "ok" }
```

---

### POST /mcp - tools/list

- **호출부**: MCP 관리 서버 (서버 등록 시 1회 자동 호출)
- 요청/응답 포맷은 **MCP 공식 표준(Anthropic)** 을 따른다 - https://modelcontextprotocol.io/specification

**Request**
```
POST /mcp
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
```
HTTP/1.1 200 OK
Content-Type: application/json
```
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
            "url":    { "type": "string",  "description": "이미지 URL" },
            "width":  { "type": "number",  "description": "목표 너비 (px)" },
            "height": { "type": "number",  "description": "목표 높이 (px)" }
          },
          "required": ["url", "width", "height"]
        }
      }
    ]
  }
}
```

---

### POST /mcp - resources/list

- **호출부**: MCP 관리 서버 (서버 등록 시 1회 자동 호출, 선택 구현)
- 빈 목록이거나 미구현이어도 등록 실패로 처리하지 않는다

**Request**
```
POST /mcp
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/list",
  "params": {}
}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resources": [
      {
        "uri":         "image://logo.png",
        "name":        "App Logo",
        "description": "앱 대표 이미지",
        "mimeType":    "image/png"
      },
      {
        "uri":         "db://users/active",
        "name":        "Active Users",
        "mimeType":    "application/json"
      }
    ]
  }
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `uri` | 필수 | 리소스 고유 식별자 (파일 경로, DB URI, 커스텀 스킴 등) |
| `name` | 필수 | 사람이 읽을 수 있는 이름 |
| `description` | 선택 | 리소스 설명 |
| `mimeType` | 선택 | 데이터 형식. `image/*` 이면 썸네일 자동 추출 대상 |

---

### POST /mcp - tools/call

- **호출부**: WEB / WAS (관리 서버 미경유, 직접 호출)
- 요청/응답 포맷은 **MCP 공식 표준(Anthropic)** 을 따른다 - https://modelcontextprotocol.io/specification

**Request**
```
POST /mcp
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "resize_image",
    "arguments": {
      "url":    "https://example.com/image.jpg",
      "width":  800,
      "height": 600
    }
  }
}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "도구 실행 결과" }
    ]
  }
}
```

---

## 3. MCP 관리 서버 Inbound API

외부에서 MCP 관리 서버로 들어오는 요청

---

### POST /api/mcp/servers/register

- **호출부**: GitHub Actions (배포 성공 시 CI/CD 파이프라인)

**등록 내부 흐름**

```
POST /api/mcp/servers/register
  - status: PENDING 생성
  - tools/list 수집 (필수)
      - 빈 값 or 실패 → status: REGISTRATION_FAILED, 종료
  - resources/list 수집 (선택)
      - 빈 값 or 실패 → 무시, 정상 진행
      - mimeType: image/* 리소스 있으면 thumbnail URL 자동 추출
  - status: ACTIVE 전환
  - McpApp 생성 (displayName, description 등 비어있는 상태, thumbnail은 auto-extract 값)
```

**CollectResult**

| 값 | 설명 |
|---|---|
| `SUCCESS` | 수집 성공 |
| `EMPTY` | 응답은 왔으나 목록이 비어있음 |
| `FAILED` | 네트워크 오류 또는 파싱 실패 |

**ServerStatus**

| 값 | 설명 |
|---|---|
| `PENDING` | 등록 요청 수신, 수집 진행 중 |
| `ACTIVE` | 정상 등록 완료 |
| `INACTIVE` | 헬스체크 3회 연속 실패 |
| `REGISTRATION_FAILED` | tools/list 수집 실패 또는 빈 값 |

**Request**
```
POST /api/mcp/servers/register
Content-Type: application/json
```
```json
{
  "name": "image-resize-tool",
  "url":  "https://image-resize-tool.vercel.app"
}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "serverId": "uuid",
  "status":   "ACTIVE"
}
```

---

### DELETE /api/mcp/servers/{serverId}

- **호출부**: OSS (Management) 또는 운영자

**Request**
```
DELETE /api/mcp/servers/{serverId}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{ "status": "ok" }
```

---

### GET /api/mcp/servers

- **호출부**: WAS (User, Credit) / OSS (Management)
- 모든 서버 반환 (isVisible 무관). 관리/어드민 용도.

**Request**
```
GET /api/mcp/servers
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "servers": [
    {
      "id":             "uuid",
      "name":           "image-resize-tool",
      "url":            "https://image-resize-tool.vercel.app",
      "description":    "이미지 크기 조정 및 포맷 변환",
      "status":         "ACTIVE",
      "registeredAt":   "2026-05-07T10:00:00Z",
      "displayName":    "Image Resizer",
      "appDescription": "누구나 쉽게 이미지를 원하는 크기로 바꿔보세요",
      "thumbnail":      "/api/mcp/servers/uuid/resources/content?uri=image%3A%2F%2Flogo.png",
      "credit":         10,
      "isVisible":      true,
      "tools": [
        {
          "name":        "resize_image",
          "description": "이미지 크기 조정",
          "inputSchema": {
            "type": "object",
            "properties": {
              "url":    { "type": "string" },
              "width":  { "type": "number" },
              "height": { "type": "number" }
            },
            "required": ["url", "width", "height"]
          }
        }
      ],
      "resources": [
        {
          "uri":      "image://logo.png",
          "name":     "App Logo",
          "mimeType": "image/png"
        }
      ]
    }
  ]
}
```

---

### GET /api/mcp/apps/public

- **호출부**: WEB (User)
- `isVisible: true` 앱만 반환. WEB 마켓플레이스 화면용.
- `displayName` 이 없으면 서버 name으로 폴백

**Request**
```
GET /api/mcp/apps/public
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
[
  {
    "id":          "uuid",
    "displayName": "Image Resizer",
    "description": "누구나 쉽게 이미지를 원하는 크기로 바꿔보세요",
    "thumbnail":   "/api/mcp/servers/uuid/resources/content?uri=image%3A%2F%2Flogo.png",
    "credit":      10,
    "mcpUrl":      "https://image-resize-tool.vercel.app",
    "tools": [
      {
        "name":        "resize_image",
        "description": "이미지 크기 조정",
        "inputSchema": { "type": "object", "properties": {} }
      }
    ]
  }
]
```

---

### PATCH /api/mcp/apps/{id}

- **호출부**: 백오피스 (기획팀)
- null 필드는 무시 (부분 업데이트). 빈 문자열을 보내면 해당 필드를 빈 값으로 설정.

**Request**
```
PATCH /api/mcp/apps/{id}
Content-Type: application/json
```
```json
{
  "displayName":       "Image Resizer",
  "description":       "누구나 쉽게 이미지를 원하는 크기로 바꿔보세요",
  "thumbnail":         "https://cdn.example.com/logo.png",
  "credit":            10,
  "isVisible":         true,
  "serverDescription": "이미지 크기 조정 및 포맷 변환 MCP 서버"
}
```

**Response**
```
HTTP/1.1 200 OK
```

---

### GET /api/mcp/servers/{serverId}/resources/content

- **호출부**: WEB (thumbnail 렌더링 시)
- 관리 서버가 해당 MCP 서버에 `resources/read` 를 프록시 호출하여 바이너리로 응답

**Request**
```
GET /api/mcp/servers/{serverId}/resources/content?uri={encodedUri}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: image/png
```
```
(binary)
```

| 상태 | 설명 |
|---|---|
| 200 | 리소스 바이너리 또는 텍스트 정상 반환 |
| 404 | 서버 또는 리소스를 찾을 수 없음 |
| 502 | MCP 서버 호출 실패 |

---

## 4. MCP 관리 서버 Outbound

MCP 관리 서버에서 등록된 MCP 서버로 나가는 요청

---

### GET {server.url}/health - 헬스체크 폴링

- **호출부**: MCP 관리 서버 (60초 주기)

**Request**
```
GET {server.url}/health
```

| 조건 | 처리 |
|---|---|
| 200 OK | ACTIVE 유지 |
| 비정상 응답 3회 연속 | INACTIVE 전환 |
| 복구 확인 시 | ACTIVE 자동 전환 |

---

### POST {server.url}/mcp - tools/list 수집

- **호출부**: MCP 관리 서버 (서버 등록 시 1회)
- 도구 변경 시 재배포 후 재등록하면 덮어쓴다

**Request**
```
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

---

### POST {server.url}/mcp - resources/list 수집

- **호출부**: MCP 관리 서버 (서버 등록 시 1회, 선택)
- 실패해도 등록 흐름에 영향 없음

**Request**
```
POST {server.url}/mcp
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/list",
  "params": {}
}
```

---

### POST {server.url}/mcp - resources/read 프록시

- **호출부**: MCP 관리 서버 (WEB의 `/api/mcp/servers/{id}/resources/content` 요청 시)

**Request**
```
POST {server.url}/mcp
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "resources/read",
  "params": {
    "uri": "image://logo.png"
  }
}
```

**MCP 서버 응답 (바이너리)**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "contents": [
      {
        "uri":      "image://logo.png",
        "mimeType": "image/png",
        "blob":     "base64encodeddata..."
      }
    ]
  }
}
```

관리 서버는 `blob` (base64)을 디코딩하여 WEB에 바이너리로 전달한다.

---

## 5. 클라이언트 직접 호출

리스트 조회 후 WEB / WAS 등 클라이언트가 MCP 서버에 **직접** tools/call을 호출한다.
MCP 관리 서버를 경유하지 않는다.

---

### 1단계 - 공개 앱 목록 조회 (WEB)

- **호출부**: WEB
- **대상**: MCP 관리 서버

**Request**
```
GET /api/mcp/apps/public
```

---

### 2단계 - 도구 직접 호출

- **호출부**: WEB / WAS
- **대상**: MCP 서버 직접 (관리 서버 미경유)
- 하나의 서버에 도구가 여러 개 있더라도 엔드포인트는 동일하며, `params.name` 으로 도구를 구분한다

**Request**
```
POST {mcpUrl}/mcp
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "resize_image",
    "arguments": {
      "url":    "https://example.com/image.jpg",
      "width":  800,
      "height": 600
    }
  }
}
```

**Response**
```
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "도구 실행 결과" }
    ]
  }
}
```

> **CORS 주의**: WEB(브라우저)에서 외부 MCP 서버에 직접 호출할 경우 CORS 정책에 의해 차단될 수 있다. MCP 서버가 브라우저 origin을 허용하지 않는 경우 관리 서버를 통한 프록시 방식으로 전환이 필요하다. (별도 구현 문서 참고)

### 요약

| 단계 | 호출부 | 대상 | API |
|---|---|---|---|
| 앱 목록 조회 | WEB | MCP 관리 서버 | `GET /api/mcp/apps/public` |
| 서버 목록 조회 | WAS / OSS | MCP 관리 서버 | `GET /api/mcp/servers` |
| 도구 호출 | WEB / WAS | MCP 서버 직접 | `POST {mcpUrl}/mcp` |
| 리소스 조회 | WEB | MCP 관리 서버 경유 | `GET /api/mcp/servers/{id}/resources/content` |

---

## 6. CI/CD 재등록

MCP 서버는 배포 성공 시 CI/CD 파이프라인 마지막 스텝에서 관리 서버에 재등록을 호출한다.

```yaml
# GitHub Actions 예시
- name: Notify MCP management server
  run: |
    curl -X POST https://management-server/api/mcp/servers/register \
      -H "Content-Type: application/json" \
      -d '{"name":"my-mcp","url":"https://my-mcp.internal"}'
```

**재등록 동작 규칙**

같은 URL로 등록 요청이 오면 신규 생성이 아닌 업데이트로 처리한다.

| 항목 | 동작 |
|---|---|
| `serverId` | 기존 ID 재사용 (URL 기준으로 식별) |
| `tools` / `resources` | 재수집하여 최신 상태로 덮어씀 |
| `registeredAt` | 최초 등록 시각 유지 |
| `McpApp` (displayName, credit 등) | 변경 없음 — 기획팀 편집 내용 보존 |

---

## 7. 테스트

### 전략

단위 테스트(Mock) → 통합 테스트(실제 HTTP)로 계층화한다.

| 계층 | 대상 | 도구 |
|---|---|---|
| 단위 | Registry, Service 내부 로직 | JUnit 5 + Mockito |
| 통합 (API) | Controller 레이어 | MockMvc + Spring Boot Test |
| 통합 (E2E) | 실제 HTTP 등록 → MCP 수집 → 응답 검증 | java.net.http.HttpClient + RANDOM_PORT |

### 테스트 전용 MCP 서버

E2E 테스트는 외부 서버 없이 동일한 Spring 컨텍스트 안에서 MCP 서버를 구동한다. `@Profile("test")` 로 활성화되며, 런타임에 응답을 바꿀 수 있다.

| 경로 | 역할 |
|---|---|
| `/test-mcp` | 기본 정상 서버. `TestMcpServerState` 로 health / tools 동적 제어 |
| `/empty-tools-mcp` | tools/list 가 항상 빈 배열을 반환 |
| `/error-mcp` | tools/list 요청 시 500 반환 |

```java
// 테스트 내에서 상태 변경
testMcpServerState.setHealthy(false);   // health 503 반환
testMcpServerState.setToolNames(List.of("tool_a", "tool_b"));  // tools/list 응답 변경
testMcpServerState.reset();             // @AfterEach 에서 원복
```

### 검증 항목

**등록 흐름**

| 시나리오 | 기대 결과 |
|---|---|
| 정상 등록 | `status: ACTIVE`, tools/resources 수집 완료 |
| tools 빈 배열 | `status: REGISTRATION_FAILED` |
| tools/list HTTP 500 | `status: REGISTRATION_FAILED` |
| 등록 성공 시 image/* 리소스 존재 | `thumbnail` 자동 추출 |

**재등록**

| 시나리오 | 기대 결과 |
|---|---|
| 같은 URL 재등록, tools 변경 | 기존 serverId 유지, tools 목록 갱신 |

**헬스체크**

| 시나리오 | 기대 결과 |
|---|---|
| health 3회 연속 실패 | `status: INACTIVE` |
| 복구 후 health 1회 성공 | `status: ACTIVE` 자동 복구 |

> 헬스체크는 60초 스케줄러를 기다리지 않고 `healthCheckPoller.poll()` 을 테스트에서 직접 호출하여 검증한다.

**API**

| 시나리오 | 기대 결과 |
|---|---|
| isVisible=false 앱 | `/api/mcp/apps/public` 응답에서 제외 |
| PATCH 후 isVisible=true | `/api/mcp/apps/public` 에 노출 |
| resources/content 프록시 | 바이너리 응답, `Content-Type: image/png` |
