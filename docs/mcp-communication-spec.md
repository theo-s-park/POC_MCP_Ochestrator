# MCP 서버 통신 규약

**대상**: MCP 서버를 개발하는 팀 (연구소, 외부 개발자)  
**기반 표준**: MCP 공식 스펙 `2024-11-05` · Streamable HTTP · JSON-RPC 2.0

> 전체 아키텍처 및 시퀀스 다이어그램: [docs/img/sequence-diagram.md](./img/sequence-diagram.md)

---

## 1. MCP 서버 필수 구현 요건

MCP 관리 서버에 등록되려면 아래 두 엔드포인트를 반드시 노출해야 한다.

| 엔드포인트 | 메서드 | 설명 |
|---|---|---|
| `/health` | GET | 관리 서버가 생존 여부 폴링 |
| `/mcp` | POST | MCP 표준 JSON-RPC 2.0 처리 |

### GET /health

```json
{ "status": "ok" }
```

### POST /mcp

모든 MCP 메서드(`tools/list`, `tools/call`, `resources/list` 등)를 이 단일 경로로 받는다.

**요청 포맷 (JSON-RPC 2.0)**
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
    "code": -32601,
    "message": "Method not found"
  }
}
```

---

## 2. 등록 절차

MCP 서버 배포 완료 후 CI/CD 파이프라인에서 아래 API를 호출한다.

```http
POST /api/mcp/servers/register
Content-Type: application/json
```

```json
{
  "name": "hwp-converter",
  "url": "https://hwp-converter.internal",
  "description": "HWP 파일을 PDF로 변환하는 MCP 서버",
  "version": "1.0.0",
  "type": "MCP"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `name` | O | 서버 등록명 |
| `url` | O | MCP 서버 Base URL (`/mcp`, `/health` 경로 제외한 루트) |
| `description` | 선택 | 서버 설명 |
| `version` | 선택 | 서버 버전 |
| `type` | 선택 | `MCP` (기본값) / `WEBAPP` |

**응답**
```json
{
  "serverId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

> 같은 URL로 재등록하면 기존 `serverId`를 유지한다.  
> `status`가 `REGISTRATION_FAILED`이면 `tools/list` 수집에 실패한 것이다. 서버가 정상 기동됐는지 확인 후 재등록한다.

---

## 3. 등록 후 자동 수집

등록 즉시 관리 서버가 아래 순서로 MCP 서버를 호출한다.

```
POST {url}/mcp  →  tools/list     (필수)
POST {url}/mcp  →  resources/list (선택)
```

수집된 Tool 목록은 LLM 라우팅에 사용되며, Resource 중 `mimeType`이 `image/*`인 항목은 썸네일로 자동 등록된다.

`type=WEBAPP`이면 수집을 건너뛰고 바로 `ACTIVE`로 전환한다.

---

## 4. 헬스체크 폴링

등록 완료 후 관리 서버가 60초마다 `GET /health`를 폴링한다.

| 조건 | 처리 |
|---|---|
| 200 OK | `ACTIVE` 유지 |
| 3회 연속 실패 | `INACTIVE` 전환 → 라우팅 대상 제외 |
| 이후 복구 확인 | `ACTIVE` 자동 전환 |

---

## 5. tools/list 응답 규격

관리 서버가 수집하는 Tool 목록 응답 형식이다. MCP 표준을 따른다.

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "convert_hwp_to_pdf",
        "description": "HWP 파일을 PDF로 변환",
        "inputSchema": {
          "type": "object",
          "properties": {
            "fileUrl": { "type": "string", "description": "변환할 HWP 파일 URL" },
            "fileName": { "type": "string", "description": "출력 파일명 (확장자 제외)" }
          },
          "required": ["fileUrl"]
        }
      }
    ]
  }
}
```

---

## 6. tools/call 요청 형식

관리 서버가 Tool 실행 시 MCP 서버로 전달하는 요청이다. body를 변환하지 않고 그대로 포워딩한다.

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "convert_hwp_to_pdf",
    "arguments": {
      "fileUrl": "https://storage.example.com/report.hwp",
      "fileName": "report"
    }
  }
}
```

**응답 (MCP 표준, 변경 불가)**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      { "type": "text", "text": "변환 완료. 다운로드: https://storage.example.com/report.pdf" }
    ]
  }
}
```

> `content` 배열 구조는 MCP 표준 범위이므로 임의로 변경하면 관리 서버가 파싱에 실패할 수 있다.

---

## 7. 인증 (구현 예정)

현재 인증은 미구현 상태이며, OAuth 임시 토큰 교환 방식으로 구현 예정이다.

확정되면 `tools/call` 요청에 아래 헤더가 추가된다.

```http
Authorization: Bearer {accessToken}
```

MCP 서버는 이 토큰으로 사용자를 식별하고, 크레딧 차감 등 필요한 처리를 수행한다.  
크레딧 차감 정책은 미정. 현재 유력한 방식은 MCP 서버가 실행 후 크레딧 서버를 직접 호출하는 구조다.
