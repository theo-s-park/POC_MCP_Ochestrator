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
- **WAS**는 유저의 크레딧을 차감하기 위해 이번 실행에서 얼마를 소모시켜야 하는지 알아야 한다
- **Backoffice**는 각 도구의 공개 여부·비용·설명을 중앙에서 제어해야 한다

이를 위해 MCP 관리 서버는 MCP 표준 응답을 변형하지 않고 **상위에 Wrapper 객체를 추가**한다.

---

### Wrapper 필드 목록

Wrapper는 두 레벨로 존재한다.

**앱 메타데이터 Wrapper** - Backoffice에서 설정하며, WEB/WAS가 읽는다.

| 필드 | 설정 주체 | 소비 주체 | 설명 |
|---|---|---|---|
| `displayName` | Backoffice | WEB | 사용자에게 보이는 앱 이름 |
| `description` | Backoffice | WEB | 앱 설명 (공개용) |
| `thumbnail` | Backoffice | WEB | 앱 썸네일 이미지 URL |
| `credit` | Backoffice | WEB, WAS | Tool 실행 1회당 차감할 크레딧 수 |
| `isVisible` | Backoffice | WEB | 공개 앱 목록 노출 여부 |

**실행 결과 Wrapper** - Tool 실행 시 MCP 관리 서버가 생성하며, WAS가 크레딧 차감에 사용한다. (현재 임시 구현 기준이며 프로덕션에서 구조 변경 예정)

| 필드 | 설명 |
|---|---|
| `creditUsed` | 이번 요청에서 소모된 총 크레딧 (trace 전체 합산) |
| `trace[].toolName` | 실행된 Tool 이름 |
| `trace[].server` | Tool이 속한 MCP 서버 이름 |
| `trace[].credit` | 해당 Tool 호출에 적용된 크레딧 (Backoffice 설정값) |
| `trace[].args` | Tool에 전달된 파라미터 |
| `trace[].result` | MCP 서버 원본 응답 **(MCP 표준, 변경 불가)** |

---

### 소비자별 Wrapper 사용 방식

| 소비자 | 사용하는 Wrapper 필드 | 목적 |
|---|---|---|
| **WEB** | `displayName`, `description`, `thumbnail`, `credit`, `tools[].inputSchema` | 도구 목록 UI 렌더링, 실행 전 비용 표시 |
| **WAS** | `creditUsed` (실행 결과), `credit` (앱 메타) | 유저 토큰 차감 기준값으로 사용 |
| **Backoffice** | 앱 메타 전체 | `credit`, `isVisible` 등 설정·수정 |

> MCP 표준 범위인 `trace[].result` 내부의 `content` 배열은 MCP 서버가 반환한 원본이며, MCP 관리 서버가 변형하지 않는다.

---

## 소비자별 API 목록

| 소비자 | 엔드포인트 |
|---|---|
| **MCP 서버 CI/CD** (등록 대상 서버) | `POST /api/mcp/servers/register` |
| **Backoffice** | `GET /api/mcp/servers` · `DELETE /api/mcp/servers/{id}` · `GET /api/mcp/servers/{id}/resources/content` · `GET /api/mcp/apps` · `PATCH /api/mcp/apps/{id}` |
| **WAS / Credit 서버** | `GET /api/mcp/apps` (polling) · `GET /api/mcp/apps/{id}` (캐시 미스 시 단건 조회) |
| **WEB** | `GET /api/mcp/apps/public` · `GET /api/mcp/servers/{id}/resources/content` (Backoffice와 동일 엔드포인트) |
| **WEB (임시)** | `POST /agent/chat` |

---

## MCP 서버 - CI/CD 파이프라인

MCP 관리 서버에 등록되는 각 MCP 서버(예: hwp-converter)가 배포 시 자신의 CI/CD 파이프라인에서 호출하는 API다.

### POST /api/mcp/servers/register

신규 MCP 서버 등록. 등록 완료 후 MCP 관리 서버가 MCP 표준 통신(JSON-RPC 2.0)으로 해당 서버의 `tools/list`, `resources/list`를 직접 수집한다.

**Request**
```json
{
  "name": "hwp-converter",
  "url": "http://localhost:8081",
  "description": "HWP 파일을 PDF로 변환하는 MCP 서버",
  "version": "1.0.0"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | O | 서버 등록명 |
| `url` | string | O | MCP 서버 Base URL |
| `description` | string | 선택 | 서버 설명 |
| `version` | string | 선택 | 서버 버전 |

**Response 200**
```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `serverId` | string (UUID) | 등록된 서버 ID (재등록 시 기존 ID 유지) |
| `status` | string | 등록 결과 (`ACTIVE` / `REGISTRATION_FAILED`) |

---

## Backoffice

### GET /api/mcp/servers

등록된 MCP 서버 전체 목록. Tool, Resource 포함. WAS도 이 엔드포인트를 사용한다.

**Response 200**
```json
{
  "servers": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "hwp-converter",
      "url": "http://localhost:8081",
      "description": "HWP 파일을 PDF로 변환하는 MCP 서버",
      "status": "ACTIVE",
      "registeredAt": "2026-05-15T03:00:00Z",
      "tools": [
        {
          "name": "convert_hwp_to_pdf",
          "description": "HWP 파일을 PDF로 변환",
          "inputSchema": {
            "type": "object",
            "properties": {
              "fileUrl": { "type": "string", "description": "변환할 HWP 파일 URL" },
              "fileName": { "type": "string", "description": "출력 PDF 파일명 (확장자 제외)" }
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
| `servers` | array | 서버 목록 |
| `servers[].id` | string (UUID) | 서버 고유 ID |
| `servers[].name` | string | 서버 등록명 |
| `servers[].url` | string | MCP 서버 Base URL |
| `servers[].description` | string | 서버 설명 |
| `servers[].status` | string | 현재 상태 (`ACTIVE` / `INACTIVE` / `PENDING` / `REGISTRATION_FAILED`) |
| `servers[].registeredAt` | string (ISO 8601) | 최초 등록 시각 |
| `servers[].tools` | array | 수집된 Tool 목록 |
| `servers[].tools[].name` | string | Tool 이름 |
| `servers[].tools[].description` | string | Tool 설명 |
| `servers[].tools[].inputSchema` | object | JSON Schema 형식의 파라미터 정의 |
| `servers[].resources` | array | 수집된 Resource 목록 (없으면 빈 배열) |
| `servers[].resources[].uri` | string | 리소스 고유 식별자 |
| `servers[].resources[].name` | string | 리소스 이름 |
| `servers[].resources[].mimeType` | string | 리소스 타입 (`image/*` 이면 썸네일 자동 추출) |

---

### DELETE /api/mcp/servers/{serverId}

서버 삭제.

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `serverId` | string (UUID) | 삭제할 서버 ID |

**Response 200**
```json
{ "status": "ok" }
```

**Response 404**: 서버 없음

---

### GET /api/mcp/servers/{serverId}/resources/content?uri={uri}

MCP 서버의 리소스 내용을 프록시로 반환. WEB도 이 엔드포인트를 사용한다.

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `uri` | string | O | 리소스 URI (URL 인코딩 필요) |

**Response**: 리소스 Content-Type 그대로 반환 (image/png, text/plain 등)

---

### GET /api/mcp/apps

전체 앱 목록. 비공개 앱 포함. MCP 서버 등록 시 앱 메타데이터가 자동 생성되며, 이 API로 조회·수정한다.

**Response 200**
```json
[
  {
    "id": "app-uuid",
    "mcpServerId": "server-uuid",
    "serverName": "hwp-converter",
    "serverUrl": "http://localhost:8081",
    "serverStatus": "ACTIVE",
    "displayName": "HWP 변환기",
    "thumbnail": "https://example.com/icon.png",
    "credit": 5,
    "description": "HWP 파일을 PDF로 변환",
    "isVisible": true
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string (UUID) | 앱 고유 ID |
| `mcpServerId` | string (UUID) | 연결된 MCP 서버 ID |
| `serverName` | string | MCP 서버 등록명 |
| `serverUrl` | string | MCP 서버 Base URL |
| `serverStatus` | string | MCP 서버 현재 상태 |
| `displayName` | string | 사용자에게 보이는 앱 이름 |
| `thumbnail` | string | 썸네일 이미지 URL |
| `credit` | integer | Tool 실행 시 차감할 크레딧 수 |
| `description` | string | 앱 설명 (공개용) |
| `isVisible` | boolean | WEB 노출 여부 |

---

### PATCH /api/mcp/apps/{id}

앱 메타데이터 수정. 전달한 필드만 업데이트 (null 전송 시 무시).

**Path Parameter**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `id` | string (UUID) | 수정할 앱 ID |

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

| 필드 | 타입 | 설명 |
|---|---|---|
| `displayName` | string | 사용자에게 보이는 이름 |
| `thumbnail` | string | 썸네일 이미지 URL |
| `credit` | integer | Tool 실행 시 차감할 크레딧 수 |
| `description` | string | 앱 설명 (공개용) |
| `isVisible` | boolean | WEB 노출 여부 |

**Response 200**
```json
{ "status": "ok" }
```

**Response 404**: 앱 없음

---

## WAS / Credit 서버

WAS는 MCP 관리 서버를 **polling**해서 앱별 credit 값을 자체 캐시로 유지한다.
유저가 도구를 실행하면 WAS는 캐시에서 credit을 조회해 차감하므로, 실행 흐름에서 MCP 관리 서버를 거치지 않는다.

```
WEB → WAS: "유저가 appId=xyz 사용" (credit 값은 전달하지 않음 - 신뢰 불가)
WAS: 캐시에서 appId=xyz → credit=5 확인
WAS → Credit 서버: userId 잔액 차감 + 히스토리 저장 { userId, appId, toolName, creditCost }
WAS → WEB: OK
```

### GET /api/mcp/apps

→ [Backoffice 섹션 참조](#get-apimcpapps). **Credit 서버가 주기적으로 polling해 앱-credit 매핑을 최신화**한다.

---

### GET /api/mcp/apps/{id}

특정 앱 단건 조회. 캐시 미스 또는 즉시 확인이 필요할 때 사용한다.

**Response 200**
```json
{
  "id": "app-uuid",
  "displayName": "HWP 변환기",
  "description": "HWP 파일을 PDF로 변환",
  "thumbnail": "https://example.com/icon.png",
  "credit": 5,
  "mcpUrl": "http://localhost:8081",
  "tools": [
    {
      "name": "convert_hwp_to_pdf",
      "description": "HWP 파일을 PDF로 변환",
      "inputSchema": {
        "type": "object",
        "properties": {
          "fileUrl": { "type": "string", "description": "변환할 HWP 파일 URL" },
          "fileName": { "type": "string", "description": "출력 PDF 파일명 (확장자 제외)" }
        },
        "required": ["fileUrl"]
      }
    }
  ]
}
```

응답 구조는 `GET /api/mcp/apps/public` 단건과 동일하다.

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
    "mcpUrl": "http://localhost:8081",
    "tools": [
      {
        "name": "convert_hwp_to_pdf",
        "description": "HWP 파일을 PDF로 변환",
        "inputSchema": {
          "type": "object",
          "properties": {
            "fileUrl": { "type": "string", "description": "변환할 HWP 파일 URL" },
            "fileName": { "type": "string", "description": "출력 PDF 파일명 (확장자 제외)" }
          },
          "required": ["fileUrl"]
        }
      }
    ]
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string (UUID) | 앱 고유 ID |
| `displayName` | string | 사용자에게 보이는 앱 이름 |
| `description` | string | 앱 설명 |
| `thumbnail` | string | 썸네일 이미지 URL |
| `credit` | integer | Tool 실행 시 차감될 크레딧 수 |
| `mcpUrl` | string | MCP 서버 Base URL |
| `tools` | array | 해당 앱의 Tool 목록 |
| `tools[].name` | string | Tool 이름 |
| `tools[].description` | string | Tool 설명 |
| `tools[].inputSchema` | object | JSON Schema 형식의 파라미터 정의 |

---

### GET /api/mcp/servers/{serverId}/resources/content?uri={uri}

→ [Backoffice 섹션 참조](#get-apimcpserversserveridresourcescontenturiuri). 동일 엔드포인트이며 응답 구조가 같다.

---

## WEB - 임시 구현

> **이 섹션 전체는 임시 구현이다.** MCP 관리 서버에 LLM Agent를 직접 붙여 자연어 질문 → Tool 자동 선택 → 실행까지 처리하는 구조로, 동작 검증 목적으로만 사용한다.
>
> **실제 프로덕션 flow에서는 이 API를 사용하지 않는다.** 실제 flow는 WEB이 Tool과 파라미터를 직접 지정하여 호출하며, 인증·크레딧 처리는 WAS가 interceptor로 담당하는 방식으로 설계 중이다. (설계 미확정)
>
> 아래 응답의 `question`, `answer` 필드는 LLM 기반 임시 구현에만 존재하며 프로덕션 API에서는 제거된다. `trace`와 `creditUsed` 구조는 프로덕션에서도 유지될 가능성이 높다.

### POST /agent/chat

**대상**: WEB (임시)

**Request**
```json
{
  "question": "이 HWP 파일 PDF로 변환해줘: https://storage.example.com/report.hwp"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `question` | string | O | 자연어 질문 - LLM이 Tool을 자동 선택하기 위한 입력. **임시 구현 전용** |

**Response 200**
```json
{
  "sessionId": "8f111f71",
  "question": "이 HWP 파일 PDF로 변환해줘: https://storage.example.com/report.hwp",
  "answer": "HWP 파일을 PDF로 변환 완료했습니다. 다운로드: https://storage.example.com/report.pdf",
  "creditUsed": 5,
  "trace": [
    {
      "toolName": "convert_hwp_to_pdf",
      "server": "hwp-converter",
      "credit": 5,
      "args": "{\"fileUrl\":\"https://storage.example.com/report.hwp\",\"fileName\":\"report\"}",
      "result": "{\"content\":[{\"type\":\"text\",\"text\":\"변환 완료. 다운로드 URL: https://storage.example.com/report.pdf\"}]}"
    }
  ]
}
```

> **레이어 구분**
> - **Wrapper (MCP 관리 서버 추가)**: `sessionId`, `question`, `answer`, `creditUsed`, `trace[].toolName`, `trace[].server`, `trace[].credit`, `trace[].args`
> - **MCP 표준 (원본 그대로)**: `trace[].result` 내부의 `content` 배열 - MCP 서버가 반환한 JSON-RPC 2.0 응답을 문자열로 전달

| 필드 | 타입 | 레이어 | 존속 여부 | 설명 |
|---|---|---|---|---|
| `sessionId` | string | Wrapper | 임시 | 요청 세션 ID |
| `question` | string | Wrapper | **임시** | 입력된 자연어 질문 (echo). LLM 임시 구현 전용, 프로덕션에서 제거 |
| `answer` | string | Wrapper | **임시** | LLM이 생성한 최종 답변. LLM 임시 구현 전용, 프로덕션에서 제거 |
| `creditUsed` | integer | Wrapper | 유지 예정 | 이번 요청에서 차감된 총 크레딧 (trace 전체 합산) |
| `trace` | array | Wrapper | 유지 예정 | Tool 실행 이력 |
| `trace[].toolName` | string | Wrapper | 유지 예정 | 실행된 Tool 이름 |
| `trace[].server` | string | Wrapper | 유지 예정 | Tool이 속한 MCP 서버 이름 |
| `trace[].credit` | integer | Wrapper | 유지 예정 | 해당 Tool 호출에 적용된 크레딧 (Backoffice 설정값) |
| `trace[].args` | string (JSON) | Wrapper | 유지 예정 | Tool에 전달된 파라미터 |
| `trace[].result` | string (JSON) | **MCP 표준** | 유지 예정 | MCP 서버 원본 응답. `content[].type`, `content[].text` 구조는 MCP 스펙 필수 범위 |
