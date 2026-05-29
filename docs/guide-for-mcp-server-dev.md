# MCP 서버 개발자 가이드

이 문서는 **MCP 관리 서버에 서비스를 연동하려는 개발자**를 대상으로 한다.

> 인증 연동 방식은 별도 확정 예정. woodie님 가이드 참고.

---

## 전체 흐름

```
1. MCP 서버 구현      POST /mcp 엔드포인트 구현 (이 문서 참고)
          ↓
2. Lambda 생성        운영파트 gray님에게 Lambda 생성 및 URL 발급 요청
          ↓
3. Lambda 배포        구현한 코드를 Lambda에 배포 (개발자 + 운영파트)
          ↓
4. 엔드포인트 등록    Backoffice에서 Lambda Base URL 등록
          ↓
5. 핸드셰이킹         관리 서버가 자동으로 ping → tools/list 수집 → ACTIVE
```

---

## MCP 프로토콜 배경

MCP(Model Context Protocol)는 LLM이 외부 툴을 호출하는 방식을 표준화한 프로토콜이다.  
전송 방식은 두 가지가 있다.

| 방식 | 동작 | 주 사용처 |
|---|---|---|
| **stdio** | 로컬 프로세스를 직접 실행, 표준 입출력으로 통신 | Claude Desktop, Cursor 등 로컬 IDE |
| **Streamable HTTP** | HTTP 엔드포인트로 JSON-RPC 요청/응답 | 서버 배포, 멀티 클라이언트, 원격 연동 |

**우리가 Streamable HTTP를 선택한 이유**

stdio는 클라이언트와 서버가 같은 머신에 있어야 한다. 여러 사용자의 요청을 받는 서버 환경에서는 사용할 수 없다. MCP 관리 서버가 등록된 서버들을 중계하고 다수의 WEB 사용자 요청을 처리하려면 HTTP 방식이 필수다.

Streamable HTTP에서는 `POST /mcp` 단일 엔드포인트에서 body의 `"method"` 필드로 동작을 구분한다. Vercel, Cloudflare, DeepWiki 같은 공개 MCP 서버도 동일한 구조를 따른다.

```
POST /mcp   →   { "jsonrpc": "2.0", "method": "tools/list", ... }
POST /mcp   →   { "jsonrpc": "2.0", "method": "tools/call", ... }
POST /mcp   →   { "jsonrpc": "2.0", "method": "ping",       ... }
```

---

## 구현 스펙

`POST /mcp` 하나만 노출하고 아래 메서드를 처리한다.

| 메서드 | 필수 | 설명 |
|---|---|---|
| `ping` | **O** | 헬스체크 응답 |
| `tools/list` | **O** | 등록 핸드셰이킹 + 툴 목록 수집 |
| `tools/call` | **O** | 툴 실행 |
| `resources/list` | 선택 | `image/*` 있으면 썸네일 자동 추출 |
| `resources/read` | 선택 | resources/list 구현 시 함께 구현 |

> `initialize`는 구현하지 않아도 된다. MCP SDK/라이브러리 사용 시 자동 처리된다.

### ping

**예시 JSON**
```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "ping", "params": {} }

// Response
{ "jsonrpc": "2.0", "id": 1, "result": {} }
```

### tools/list

**예시 JSON**
```json
// Request
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
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
        "webUrl": "https://service.polarisoffice.com/pdf-compress"
      }
    ]
  }
}
```

**tool 객체 필드 구조**

| 필드 | 위치 | 설명 |
|---|---|---|
| `name` | tool 최상위 | 툴 식별자 |
| `description` | tool 최상위 | 툴 설명 |
| `inputSchema` | tool 최상위 | 툴 실행 시 전달할 파라미터 스키마 (JSON Schema) |
| `inputSchema.properties` | inputSchema 하위 | 각 파라미터 정의 (`type`, `description`, `default` 등) |
| `inputSchema.required` | inputSchema 하위 | 필수 파라미터 목록 |
| `webUrl` | tool 최상위 | 이 툴의 고유 웹페이지 URL. 관리 서버가 수집해 WEB에 내려준다 |

### tools/call

`arguments`에는 관리 서버가 `credit` 필드를 자동으로 주입한다. [→ Credit 처리](#credit-처리)

**예시 JSON**
```json
// Request
{
  "jsonrpc": "2.0", "id": 1,
  "method": "tools/call",
  "params": {
    "name": "hwp_to_pdf",
    "arguments": {
      "fileUrl": "https://storage.example.com/doc.hwp",
      "credit": { "serviceType": "GPT3", "deductCredit": 5 }
    }
  }
}

// Response
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "변환 완료. 다운로드 URL: https://storage.example.com/doc.pdf" }
    ]
  }
}
```

### resources/list (선택)

빈 배열이나 미구현은 등록 실패로 이어지지 않는다.  
`mimeType: image/*` 리소스가 있으면 앱 썸네일로 자동 추출된다.

**예시 JSON**
```json
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "resources": [
      { "uri": "image://logo.png", "name": "App Logo", "mimeType": "image/png" }
    ]
  }
}
```

### resources/read (선택)

**예시 JSON**
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

---

## Credit 처리

관리 서버는 `tools/call` 요청의 `arguments`에 `credit` 필드를 자동으로 주입한다. 서버가 별도로 크레딧 정보를 요청하거나 관리할 필요 없다.

**예시 JSON**
```json
"arguments": {
  "fileUrl": "https://storage.example.com/doc.hwp",
  "credit": { "serviceType": "GPT3", "deductCredit": 5 }
}
```

**서버가 해야 할 것**

1. `arguments`에서 `credit` 꺼내기 (비즈니스 로직에 넘기지 않음)
2. 비즈니스 로직 실행
3. **성공 시만** → 크레딧 서버에 차감 요청 (`credit.serviceType`, `credit.deductCredit` 사용)
4. **실패 시** → 차감 없음

> 인증(사용자 식별)은 woodie님 인증 가이드 참고. 관리 서버는 `Authorization` 헤더를 그대로 전달할 뿐 사용자 식별에 관여하지 않는다.

---

## 등록과 핸드셰이킹

Backoffice에서 Lambda Base URL을 입력하고 등록하면 관리 서버가 아래 순서로 핸드셰이킹을 진행한다.

```
1. ping 확인
   관리 서버 → POST {baseUrl}/mcp   { "method": "ping" }
                                    ↳ 200 응답 확인 (서버 생존 여부)

2. capabilities 수집
   관리 서버 → POST {baseUrl}/mcp   { "method": "tools/list" }
                                    ↳ tools 1개 이상 → ACTIVE
                                    ↳ 빈 배열 또는 오류 → REGISTRATION_FAILED

   관리 서버 → POST {baseUrl}/mcp   { "method": "resources/list" }
                                    ↳ 선택, 실패해도 무관
                                    ↳ mimeType: image/* 리소스 있으면 썸네일 자동 추출
```

> `url`은 Base URL만 입력 (`/mcp` 경로를 제외한 루트)

등록 완료 후 관리 서버는 20초마다 `ping`을 폴링해 서버 상태를 유지한다.

| 상태 | 설명 |
|---|---|
| `PENDING` | 등록 요청 수신, 핸드셰이킹 진행 중 |
| `ACTIVE` | 정상 등록 완료, 헬스체크 통과 |
| `INACTIVE` | 헬스체크 3회 연속 실패 |
| `REGISTRATION_FAILED` | tools 수집 실패 또는 빈 배열 반환 |

---

## Backoffice 접속

| 환경 | Backoffice | Swagger |
|---|---|---|
| TB | `http://54.241.171.136:8080/backoffice` | `http://54.241.171.136:8080/swagger-ui/index.html` |
| VF | `VF: -----` | `VF: -----` |

---

## 참고

- MCP 공식 스펙: https://modelcontextprotocol.io
- JSON-RPC 2.0: https://www.jsonrpc.org/specification
- 인증 방식: woodie님 가이드 참고
