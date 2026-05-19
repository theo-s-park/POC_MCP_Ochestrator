# MCP 관리 서버 API 명세

이 문서는 **MCP 관리 서버가 내부 소비자(WEB, WAS, Backoffice)에게 제공하는 API**를 정의한다.

Base URL: `http://localhost:8080` (로컬) / `http://3.34.126.190:8080` (EC2)

---

## Wrapper 개요

전체 흐름 시퀀스 다이어그램: [docs/img/sequence-diagram.md](./img/sequence-diagram.md)

### 왜 Wrapper가 필요한가

MCP 서버(hwp-converter 등)는 [MCP 공식 스펙 `2024-11-05`](https://modelcontextprotocol.io)에 따라 Tool 실행 결과를 아래 구조로만 반환한다.

```json
{ "content": [{ "type": "text", "text": "..." }] }
```

이 구조는 "Tool이 무엇을 반환했는가"만 담고 있다. MCP 관리 서버가 소비자(WEB, WAS, Backoffice)에게 서비스를 제공하려면 이것만으로는 부족하다.

- **WEB**은 도구를 노출하기 위해 표시 이름, 썸네일, 설명, 실행 비용(크레딧)이 필요하다
- **Backoffice**는 각 도구의 공개 여부·비용·설명을 중앙에서 제어해야 한다

이를 위해 MCP 관리 서버는 MCP 표준 응답을 변형하지 않고 **상위에 Wrapper 객체를 추가**한다.

---

### Wrapper 필드 목록

**앱 메타데이터 Wrapper** - Backoffice에서 설정하며, WEB이 읽는다.

| 필드 | 설정 주체 | 소비 주체 | 설명 |
|---|---|---|---|
| `displayName` | Backoffice | WEB | 사용자에게 보이는 앱 이름 |
| `description` | Backoffice | WEB | 앱 설명 (공개용) |
| `thumbnail` | Backoffice | WEB | 앱 썸네일 이미지 URL |
| `credit` | Backoffice | WEB | Tool 실행 1회당 차감할 크레딧 수 (참고용) |
| `isVisible` | Backoffice | WEB | 공개 앱 목록 노출 여부 |

> MCP 관리 서버는 크레딧 차감에 개입하지 않는다. tools/call 시 `arguments.credit`을 자동 주입하며, MCP/WEBAPP 서버가 실행 성공 후 직접 OSS 크레딧 서버를 호출해 차감한다.

**실행 결과 Wrapper** - Tool 실행 시 MCP 관리 서버가 생성 (현재 임시 구현)

| 필드 | 설명 |
|---|---|
| `creditUsed` | 이번 요청에서 소모된 총 크레딧 (trace 전체 합산) |
| `trace[].toolName` | 실행된 Tool 이름 |
| `trace[].server` | Tool이 속한 MCP 서버 이름 |
| `trace[].credit` | 해당 Tool 호출에 적용된 크레딧 (Backoffice 설정값) |
| `trace[].args` | Tool에 전달된 파라미터 |
| `trace[].result` | MCP 서버 원본 응답 **(MCP 표준, 변경 불가)** |

---

## 소비자별 API 목록

| 소비자 | 엔드포인트 |
|---|---|
| **MCP 서버 CI/CD** | `POST /api/mcp/servers/register` |
| **Backoffice** | `GET /api/mcp/servers` · `GET /api/mcp/servers/{id}` · `DELETE /api/mcp/servers/{id}` · `GET /api/mcp/servers/{id}/probe` · `GET /api/mcp/servers/{id}/resources/content` · `GET /api/mcp/apps` · `PATCH /api/mcp/apps/{id}` |
| **WEB** | `GET /api/mcp/apps/public` · `GET /api/mcp/servers/{id}/resources/content` |
| **WEB (임시 데모)** | `POST /agent/chat` |

---

## MCP 서버 등록 (CI/CD 파이프라인)

MCP 서버 배포 시 CI/CD 파이프라인에서 자동 호출한다.

### POST /api/mcp/servers/register

**Request**
```json
{
  "name": "hwp-converter",
  "url": "https://hwp-converter.internal",
  "description": "HWP 파일을 PDF로 변환하는 MCP 서버",
  "version": "1.0.0",
  "type": "MCP"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | O | 서버 등록명 |
| `url` | string | O | MCP 서버 Base URL |
| `description` | string | 선택 | 서버 설명 |
| `version` | string | 선택 | 서버 버전 |
| `type` | string | 선택 | `MCP` (기본값) / `WEBAPP` |

**Response 200**
```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `serverId` | string (UUID) | 등록된 서버 ID (같은 URL로 재등록 시 기존 ID 유지) |
| `status` | string | `ACTIVE` / `REGISTRATION_FAILED` |

> 등록 완료 후 자동 수집한다. `type=MCP`이면 `tools/list` · `resources/list` (JSON-RPC), `type=WEBAPP`이면 `GET /tools` · `GET /resources` (REST)로 수집한다.

---

## Backoffice

### GET /api/mcp/servers

등록된 MCP 서버 전체 목록. Tool, Resource, 앱 메타데이터 포함.

**Response 200**
```json
{
  "servers": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "hwp-converter",
      "url": "https://hwp-converter.internal",
      "description": "HWP 파일을 PDF로 변환하는 MCP 서버",
      "status": "ACTIVE",
      "type": "MCP",
      "registeredAt": "2026-05-15T03:00:00Z",
      "displayName": "HWP 변환기",
      "thumbnail": "https://example.com/icon.png",
      "credit": 5,
      "appDescription": "HWP 파일을 PDF로 변환합니다",
      "isVisible": true,
      "tools": [
        {
          "name": "convert_hwp_to_pdf",
          "description": "HWP 파일을 PDF로 변환",
          "inputSchema": {
            "type": "object",
            "properties": {
              "fileUrl": { "type": "string", "description": "변환할 HWP 파일 URL" }
            },
            "required": ["fileUrl"]
          }
        }
      ],
      "resources": [
        {
          "uri": "image://logo.png",
          "name": "App Logo",
          "mimeType": "image/png"
        }
      ]
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string (UUID) | 서버 고유 ID |
| `name` | string | 서버 등록명 |
| `url` | string | MCP 서버 Base URL |
| `status` | string | `ACTIVE` / `INACTIVE` / `PENDING` / `REGISTRATION_FAILED` |
| `type` | string | `MCP` / `WEBAPP` |
| `registeredAt` | string (ISO 8601) | 최초 등록 시각 |
| `displayName` | string \| null | Backoffice 설정값 |
| `thumbnail` | string \| null | 썸네일 URL (리소스에서 자동 추출 또는 수동 설정) |
| `credit` | integer | Tool 실행 시 차감 크레딧 |
| `appDescription` | string \| null | 공개용 앱 설명 |
| `isVisible` | boolean | WEB 공개 여부 |
| `tools` | array | 수집된 Tool 목록 |
| `resources` | array | 수집된 Resource 목록 |

---

### GET /api/mcp/servers/{serverId}

특정 서버 단건 조회. 응답 구조는 `GET /api/mcp/servers` 배열 원소와 동일.

**Response 404**: 서버 없음

---

### GET /api/mcp/servers/{serverId}/probe

등록된 서버에 MCP 메서드를 실시간 호출해 결과를 반환한다. 캐시 없이 서버에 직접 요청한다.

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `method` | string | O | `tools/list` / `resources/list` |

**Response 200**: 해당 MCP 서버의 JSON-RPC 2.0 원본 응답

**Response 404**: 서버 없음

---

### DELETE /api/mcp/servers/{serverId}

서버 삭제. 연결된 앱 메타데이터도 함께 삭제된다.

**Response 200**
```json
{ "status": "ok" }
```

**Response 404**: 서버 없음

---

### GET /api/mcp/servers/{serverId}/resources/content?uri={uri}

MCP 서버의 리소스 내용을 프록시로 반환. WEB도 동일 엔드포인트 사용.

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `uri` | string | O | 리소스 URI (URL 인코딩 필요) |

**Response**: 리소스 Content-Type 그대로 반환 (`image/png`, `text/plain` 등)

---

### GET /api/mcp/apps

전체 앱 목록 (비공개 포함). MCP 서버 등록 시 자동 생성된다.

**Response 200**
```json
[
  {
    "id": "app-uuid",
    "mcpServerId": "server-uuid",
    "serverName": "hwp-converter",
    "serverUrl": "https://hwp-converter.internal",
    "serverStatus": "ACTIVE",
    "displayName": "HWP 변환기",
    "thumbnail": "https://example.com/icon.png",
    "credit": 5,
    "description": "HWP 파일을 PDF로 변환",
    "isVisible": true
  }
]
```

---

### PATCH /api/mcp/apps/{id}

앱 메타데이터 수정. 전달한 필드만 업데이트 (null 전송 시 무시).

**Request**
```json
{
  "displayName": "HWP 변환기",
  "thumbnail": "https://example.com/icon.png",
  "credit": 5,
  "description": "HWP 파일을 PDF로 변환하는 서비스",
  "isVisible": true
}
```

**Response 200**
```json
{ "status": "ok" }
```

**Response 404**: 앱 없음

---

## WEB

### GET /api/mcp/apps/public

공개 앱 목록. `isVisible=true`인 앱만 반환. Tool 파라미터 스키마 포함.

**Response 200**
```json
[
  {
    "id": "app-uuid",
    "displayName": "HWP 변환기",
    "description": "HWP 파일을 PDF로 변환",
    "thumbnail": "https://example.com/icon.png",
    "credit": 5,
    "mcpUrl": "https://hwp-converter.internal",
    "tools": [
      {
        "name": "convert_hwp_to_pdf",
        "description": "HWP 파일을 PDF로 변환",
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
]
```

---

## WEB - 임시 데모

> **이 섹션은 임시 구현이다.** MCP 관리 서버에 LLM Agent를 직접 붙여 자연어 질문 → Tool 자동 선택 → 실행까지 처리하는 구조로, 동작 검증 목적으로만 사용한다. 실제 프로덕션 플로우에서는 사용하지 않는다.

### POST /agent/chat

**Request**
```json
{
  "message": "서울 날씨 알려줘",
  "sessionId": "optional-session-id"
}
```

**Response 200**
```json
{
  "sessionId": "8f111f71",
  "answer": "서울의 현재 날씨는 맑음, 기온 22°C입니다.",
  "creditUsed": 5,
  "trace": [
    {
      "toolName": "get_current_weather",
      "server": "weather-mcp",
      "credit": 5,
      "args": "{\"city\":\"서울\"}",
      "result": "{\"content\":[{\"type\":\"text\",\"text\":\"맑음, 22°C\"}]}"
    }
  ]
}
```

| 필드 | 레이어 | 설명 |
|---|---|---|
| `sessionId` | Wrapper | 요청 세션 ID |
| `answer` | Wrapper (임시) | LLM이 생성한 최종 답변 |
| `creditUsed` | Wrapper | 이번 요청 소모 크레딧 합산 |
| `trace[].toolName` | Wrapper | 실행된 Tool 이름 |
| `trace[].server` | Wrapper | Tool이 속한 MCP 서버 이름 |
| `trace[].credit` | Wrapper | 해당 Tool 호출 크레딧 |
| `trace[].args` | Wrapper | Tool 파라미터 (JSON string) |
| `trace[].result` | **MCP 표준** | MCP 서버 원본 응답 (변경 불가) |

---

## DB 스키마

> H2 File DB (`./data/mcporchestrator.mv.db`). Hibernate `ddl-auto: update` 로 자동 생성.

### mcp_server

```sql
CREATE TABLE mcp_server (
    server_id            VARCHAR(36)   PRIMARY KEY,
    name                 VARCHAR(255)  NOT NULL,
    url                  VARCHAR(255)  NOT NULL,
    description          VARCHAR(255),
    version              VARCHAR(255),
    type                 VARCHAR(20)   NOT NULL DEFAULT 'MCP',   -- MCP | WEBAPP
    status               VARCHAR(30)   NOT NULL,                  -- PENDING | ACTIVE | INACTIVE | REGISTRATION_FAILED
    tools_json           TEXT,
    resources_json       TEXT,
    registered_at        TIMESTAMP,
    health_check_failures INT          NOT NULL DEFAULT 0
);
```

| 컬럼 | 설명 |
|---|---|
| `server_id` | UUID. 동일 URL 재등록 시 재사용 |
| `type` | `MCP` — tools/list 수집 후 등록. `WEBAPP` — 수집 생략 |
| `status` | 헬스체크 3회 연속 실패 → `INACTIVE`. 복구 시 자동 `ACTIVE` |
| `tools_json` | `tools/list` 응답 JSON 직렬화 배열 |
| `resources_json` | `resources/list` 응답 JSON 직렬화 배열 |
| `health_check_failures` | 연속 실패 횟수. 성공 시 0 리셋 |

---

### mcp_app

MCP 서버 등록 시 자동 생성 (1:1). Backoffice에서 메타데이터를 설정하면 WEB 공개 앱으로 노출된다.

```sql
CREATE TABLE mcp_app (
    id             VARCHAR(36)   PRIMARY KEY,
    mcp_server_id  VARCHAR(36)   NOT NULL UNIQUE,
    display_name   VARCHAR(255),
    thumbnail      TEXT,
    credit         INT           NOT NULL DEFAULT 0,
    description    TEXT,
    is_visible     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP
);
```

| 컬럼 | 설명 |
|---|---|
| `mcp_server_id` | `mcp_server.server_id` 참조. UNIQUE (1:1) |
| `display_name` | Backoffice 설정. WEB 노출명 |
| `thumbnail` | 이미지 URL. `resources/list` image/* 에서 자동 추출 또는 수동 설정 |
| `credit` | Tool 실행 1회당 크레딧 (참고용, 실제 차감 미구현) |
| `is_visible` | `true` 인 앱만 `GET /api/mcp/apps/public` 에 노출 |
