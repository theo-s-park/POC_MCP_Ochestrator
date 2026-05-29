# 프론트엔드 개발자 API 가이드

이 문서는 **Backoffice 및 PO WEB을 개발하는 프론트엔드 개발자**를 대상으로 한다.

MCP 서버 개발자가 서버를 등록하고 나면, 기획팀이 Backoffice에서 앱 이름·설명·썸네일·툴별 크레딧을 설정하고 `isVisible`을 ON 하면 PO WEB에 노출된다. 이 문서는 그 과정에서 사용하는 API를 정의한다.

| 환경 | Base URL | Swagger |
|---|---|---|
| TB | `http://54.241.171.136:8080` | `http://54.241.171.136:8080/swagger-ui/index.html` |
| VF | `VF: -----` | `VF: -----` |

---

## 전체 흐름

```
MCP 서버 개발자가 서버 등록 → ACTIVE
         ↓
기획팀이 Backoffice에서 앱 메타데이터 설정
  - displayName, description, thumbnail
  - 툴별 serviceType · deductCredit · visible
  - isVisible = true
         ↓
GET /api/mcp/apps/public 에 노출 → PO WEB에서 조회 및 실행 가능
```

---

## Wrapper 개요

MCP 서버가 반환하는 원본 응답은 아래 구조뿐이다.

```json
{ "content": [{ "type": "text", "text": "..." }] }
```

WEB에서 서비스를 노출하려면 표시 이름·썸네일·크레딧 정보가 필요하다. 관리 서버는 MCP 표준 응답을 변형하지 않고 상위에 Wrapper 객체를 추가한다.

| 필드 | 설정 주체 | 소비 주체 | 설명 |
|---|---|---|---|
| `displayName` | Backoffice | WEB | 사용자에게 보이는 앱 이름 |
| `description` | Backoffice | WEB | 앱 설명 |
| `thumbnail` | Backoffice | WEB | 썸네일 이미지 URL |
| `isVisible` | Backoffice | WEB | `true`일 때만 공개 API에 노출 |
| `toolCredits` | Backoffice | WEB | 툴별 `{serviceType, deductCredit, visible}` |

> 크레딧은 앱 단위가 아닌 **툴 단위**로 설정한다.

---

## API 목록

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
  "url": "https://hwp-converter.internal",
  "name": "hwp-converter"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `url` | string | O | MCP 서버 Base URL |
| `name` | string | 선택 | 서버 등록명 (미입력 시 URL에서 자동 추출) |

**Response 200**
```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

> 등록 직후 관리 서버가 `ping` → `tools/list` → `resources/list` 순으로 핸드셰이킹을 진행한다. tools 수집 성공 시 `ACTIVE`, 실패 시 `REGISTRATION_FAILED`.

---

### DELETE /api/mcp/servers/{serverId}

서버 삭제. 연결된 앱 메타데이터도 함께 삭제된다.

**Response 200**
```json
{ "status": "ok" }
```

---

### GET /api/mcp/servers/{serverId}/probe

등록된 서버에 메서드를 실시간으로 직접 호출해 결과를 반환한다. 캐시 없이 서버에 직접 요청한다.

**Query Parameter**

| 파라미터 | 설명 |
|---|---|
| `method` | `ping` / `tools/list` / `resources/list` |

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
기획팀이 이 API로 앱 정보를 설정하고 `isVisible: true`로 바꾸는 순간 PO WEB에 노출된다.

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
| `displayName` | string | WEB에 노출되는 앱 이름 |
| `description` | string | 앱 설명 |
| `thumbnail` | string | 썸네일 이미지 URL |
| `isVisible` | boolean | `true` 설정 시 `GET /api/mcp/apps/public`에 노출 |
| `toolCredits` | object | 툴명 → `{serviceType, deductCredit, visible}` 맵 |
| `toolCredits.*.serviceType` | string | OSS 서비스 타입 식별자 |
| `toolCredits.*.deductCredit` | integer | 툴 실행 1회당 차감 크레딧 |
| `toolCredits.*.visible` | boolean | `false`면 공개 API 응답에서 해당 툴 제외 |

**Response 200**
```json
{ "status": "ok" }
```

---

### GET /api/oss/service-types

OSS에서 활성 서비스 타입 목록을 반환한다.  
Backoffice에서 툴별 `serviceType` 드롭다운 구성 시 사용한다.

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

`isVisible=true`인 앱만 반환.

WEB은 이 API로 앱과 툴 목록을 받아 UI를 구성한다. 툴 실행은 이 서버를 거치지 않고, 각 툴의 `webUrl`로 리다이렉트한다. `mcpUrl`(Lambda URL)은 응답에 포함되지만 현재 WEB에서 직접 사용하지 않는다.

**Response 200 — 예시 JSON**
```json
[
  {
    "id": "app-uuid-001",
    "serverId": "server-uuid-001",
    "displayName": "폴라리스 문서 변환",
    "description": "HWP, PDF 변환 서비스",
    "thumbnail": "https://service.polarisoffice.com/icon.png",
    "mcpUrl": "https://lambda.polarisoffice.com/doc-converter",
    "tools": [
      {
        "name": "hwp_to_pdf",
        "description": "HWP 파일을 PDF로 변환합니다.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "fileUrl": {
              "type": "string",
              "description": "변환할 HWP 파일의 URL"
            }
          },
          "required": ["fileUrl"]
        },
        "serviceType": "GPT3",
        "deductCredit": 5,
        "webUrl": "https://service.polarisoffice.com/hwp-converter"
      },
      {
        "name": "pdf_compress",
        "description": "PDF 파일을 압축합니다.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "fileUrl": {
              "type": "string",
              "description": "압축할 PDF 파일의 URL"
            },
            "quality": {
              "type": "string",
              "description": "압축 품질 (high / medium / low)",
              "default": "medium"
            }
          },
          "required": ["fileUrl"]
        },
        "serviceType": "GPT3",
        "deductCredit": 3,
        "webUrl": "https://service.polarisoffice.com/pdf-compress"
      }
    ]
  }
]
```

| 필드 | 출처 | 설명 |
|---|---|---|
| `id`, `displayName`, `description`, `thumbnail` | Backoffice 설정 | 앱 단위 메타데이터 |
| `mcpUrl` | 등록 시 입력한 Lambda URL | WEB에서 현재 직접 사용하지 않음 |
| `tools[].name`, `description`, `inputSchema`, `webUrl` | 핸드셰이킹 수집값 | MCP 서버가 반환한 원본 |
| `tools[].serviceType`, `deductCredit` | Backoffice 설정 | 툴별 크레딧 정보 |
| `tools[].webUrl` | 핸드셰이킹 수집값 | **WEB이 툴 실행 시 리다이렉트하는 URL** |

> `visible=false`로 설정된 툴은 이 응답에서 제외된다.

---

### POST /api/web/execute

WEB에서 툴을 직접 실행한다. 관리 서버가 크레딧 주입 → MCP 서버 forwarding을 처리한다.

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
| `appId` | string | O | `GET /api/mcp/apps/public` 응답의 `id` |
| `authorToken` | string | O | OAuth author token |
| `toolName` | string | O | 실행할 툴 이름 |
| `arguments` | object | 선택 | 툴 파라미터 |
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
| `result` | MCP 서버 원본 응답 (MCP 표준 구조, 변경 불가) |
| `creditUsed` | 이번 요청에 적용된 크레딧 |
