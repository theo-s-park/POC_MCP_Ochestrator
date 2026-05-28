# MCP 관리 서버 API 명세

이 문서는 **MCP 관리 서버가 내부 소비자(WEB FE, Backoffice)에게 제공하는 API**를 정의한다.

Base URL: `http://54.241.171.136:8080` (TB EC2)  
Swagger UI: `http://54.241.171.136:8080/swagger-ui/index.html`

---

## Wrapper 개요

### 왜 Wrapper가 필요한가

MCP 서버는 Tool 실행 결과를 아래 구조로만 반환한다.

```json
{ "content": [{ "type": "text", "text": "..." }] }
```

이 구조는 "Tool이 무엇을 반환했는가"만 담고 있다. WEB FE가 서비스를 노출하려면 이것만으로는 부족하다.

- **WEB**은 도구를 노출하기 위해 표시 이름, 썸네일, 설명, 실행 비용(크레딧)이 필요하다
- **Backoffice**는 각 도구의 공개 여부·비용·설명을 중앙에서 제어해야 한다

MCP 관리 서버는 MCP 표준 응답을 변형하지 않고 **상위에 Wrapper 객체를 추가**한다.

---

### Wrapper 필드 목록

**앱 메타데이터 Wrapper** — Backoffice에서 설정, WEB이 읽는다.

| 필드 | 설정 주체 | 소비 주체 | 설명 |
|---|---|---|---|
| `displayName` | Backoffice | WEB | 사용자에게 보이는 앱 이름 |
| `description` | Backoffice | WEB | 앱 설명 (공개용) |
| `thumbnail` | Backoffice | WEB | 앱 썸네일 이미지 URL |
| `isVisible` | Backoffice | WEB | 공개 앱 목록 노출 여부 |
| `toolCredits` | Backoffice | WEB | 툴별 크레딧 설정 (`Map<toolName, {serviceType, deductCredit, visible}>`) |

> 크레딧은 앱 단위가 아닌 **툴 단위**로 설정한다. Backoffice에서 각 툴마다 serviceType과 deductCredit을 독립적으로 지정한다.

---

## 소비자별 API 목록

| 소비자 | 엔드포인트 |
|---|---|
| **Backoffice** | `POST /api/mcp/servers/register` · `DELETE /api/mcp/servers/{id}` · `GET /api/mcp/servers/{id}/probe` · `GET /api/mcp/apps` · `PATCH /api/mcp/apps/{id}` · `GET /api/oss/service-types` |
| **WEB** | `GET /api/mcp/apps/public` · `POST /api/web/execute` |

---

## Backoffice

### POST /api/mcp/servers/register

Backoffice UI에서 URL 입력 후 등록 버튼 클릭 시 호출된다.

**Request**
```json
{
  "name": "hwp-converter",
  "url": "https://hwp-converter.internal",
  "type": "MCP"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `url` | string | O | MCP 서버 Base URL |
| `name` | string | 선택 | 서버 등록명 (미입력 시 URL에서 자동 추출) |
| `type` | string | 선택 | `MCP` (기본값) / `WEBAPP` |

**Response 200**
```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

> 등록 완료 후 자동 수집한다. `type=MCP`이면 `tools/list` · `resources/list` (JSON-RPC), `type=WEBAPP`이면 `GET /tools` · `GET /resources` (REST)로 수집한다.

---

### DELETE /api/mcp/servers/{serverId}

서버 삭제. 연결된 앱 메타데이터도 함께 삭제된다.

**Response 200**
```json
{ "status": "ok" }
```

---

### GET /api/mcp/servers/{serverId}/probe

등록된 서버에 메서드를 실시간 직접 호출해 결과를 반환한다. 캐시 없이 서버에 직접 요청한다.

**Query Parameter**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `method` | string | `tools/list` / `resources/list` / `ping` |

**Response 200**: 해당 서버의 원본 응답 JSON

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
    "description": "HWP 파일을 PDF로 변환",
    "isVisible": true,
    "tools": [
      {
        "toolName": "convert_hwp_to_pdf",
        "serviceType": "GPT3",
        "deductCredit": 5,
        "visible": true
      }
    ]
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
  "description": "HWP 파일을 PDF로 변환하는 서비스",
  "isVisible": true,
  "toolCredits": {
    "convert_hwp_to_pdf": {
      "serviceType": "GPT3",
      "deductCredit": 5,
      "visible": true
    }
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `toolCredits` | object | 툴명 → `{serviceType, deductCredit, visible}` 맵 |
| `toolCredits.*.serviceType` | string | OSS 서비스 타입 식별자 (예: `"GPT3"`) |
| `toolCredits.*.deductCredit` | integer | 툴 실행 1회당 차감 크레딧 |
| `toolCredits.*.visible` | boolean | `false`면 공개 API 응답에서 해당 툴 제외 |

**Response 200**
```json
{ "status": "ok" }
```

---

### GET /api/oss/service-types

OSS tbAIServiceInfo에서 활성 서비스 타입 목록을 반환한다.  
Backoffice에서 툴별 serviceType 드롭다운 구성 시 사용한다.

**Response 200**
```json
[
  {
    "serviceType": "2",
    "status": "ON",
    "deductCredit": 2,
    "serviceDesc": "AI WRITE_GPT-3.5",
    "inputLimit": null
  }
]
```

---

## WEB

### GET /api/mcp/apps/public

공개 앱 목록. `isVisible=true`인 앱만 반환. Tool 파라미터 스키마 포함.

**Response 200**
```json
[
  {
    "id": "app-uuid",
    "serverId": "server-uuid",
    "displayName": "HWP 변환기",
    "description": "HWP 파일을 PDF로 변환",
    "thumbnail": "https://example.com/icon.png",
    "mcpUrl": "https://hwp-converter.internal",
    "clientId": "cli_xxx",
    "redirectUri": "https://your-app.com/callback",
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
        },
        "serviceType": "GPT3",
        "deductCredit": 5
      }
    ]
  }
]
```

> - 크레딧은 툴 단위로 내려온다.  
> - `visible=false`로 설정된 툴은 이 응답에서 제외된다.  
> - `serverId`는 probe 호출 시 사용한다.

---

### POST /api/web/execute

WEB에서 특정 Tool을 직접 실행한다.  
MCP 관리 서버가 OAuth 토큰 교환 → 크레딧 주입 → MCP 서버 forwarding을 처리한다.

**Request**
```json
{
  "appId": "app-uuid",
  "authorToken": "oauth-author-token",
  "toolName": "convert_hwp_to_pdf",
  "arguments": {
    "fileUrl": "https://example.com/doc.hwp"
  },
  "serviceType": "GPT3"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `appId` | string | O | 앱 UUID (`GET /api/mcp/apps/public` 응답의 `id`) |
| `authorToken` | string | O | OAuth author token (MCP 관리 서버가 access token으로 교환) |
| `toolName` | string | O | 실행할 Tool 이름 |
| `arguments` | object | 선택 | Tool 파라미터 |
| `serviceType` | string | 선택 | 크레딧 주입용 서비스 타입. 미전달 시 credit 미주입 |

**Response 200**
```json
{
  "appId": "app-uuid",
  "toolName": "convert_hwp_to_pdf",
  "result": {
    "content": [
      { "type": "text", "text": "변환 완료. 다운로드 URL: https://..." }
    ]
  },
  "creditUsed": 5
}
```

| 필드 | 설명 |
|---|---|
| `result` | MCP 서버 원본 응답 (MCP 표준, 변경 불가) |
| `creditUsed` | 이번 요청에 적용된 크레딧 |

---

## DB 스키마

> MySQL (`pclouddeveloper` RDS). Hibernate `ddl-auto: update`로 자동 생성.

### mcp_server

```sql
CREATE TABLE mcp_server (
    server_id            VARCHAR(36)   PRIMARY KEY,
    name                 VARCHAR(255)  NOT NULL,
    url                  VARCHAR(255)  NOT NULL,
    type                 VARCHAR(20)   NOT NULL DEFAULT 'MCP',
    status               VARCHAR(30)   NOT NULL,
    tools_json           TEXT,
    resources_json       TEXT,
    registered_at        TIMESTAMP,
    health_check_failures INT          NOT NULL DEFAULT 0
);
```

| 컬럼 | 설명 |
|---|---|
| `type` | `MCP` / `WEBAPP` |
| `status` | `PENDING` / `ACTIVE` / `INACTIVE` / `REGISTRATION_FAILED` |
| `tools_json` | `tools/list` 응답 JSON 직렬화 배열 |
| `health_check_failures` | 연속 실패 횟수. 성공 시 0 리셋 |

---

### mcp_app

MCP 서버 등록 시 자동 생성 (1:1). Backoffice에서 메타데이터를 설정하면 WEB 공개 앱으로 노출된다.

```sql
CREATE TABLE mcp_app (
    id                 VARCHAR(36)   PRIMARY KEY,
    mcp_server_id      VARCHAR(36)   NOT NULL UNIQUE,
    display_name       VARCHAR(255),
    thumbnail          TEXT,
    tool_credits_json  TEXT,
    description        TEXT,
    is_visible         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP
);
```

| 컬럼 | 설명 |
|---|---|
| `mcp_server_id` | `mcp_server.server_id` 참조. UNIQUE (1:1) |
| `tool_credits_json` | 툴별 크레딧 설정 JSON. `Map<toolName, {serviceType, deductCredit, visible}>` 형태 |
| `is_visible` | `true`인 앱만 `GET /api/mcp/apps/public`에 노출 |
