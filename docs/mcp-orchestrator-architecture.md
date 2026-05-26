# MCP 관리 서버 - 역할과 구조

---

## MCP 전송 방식과 우리가 HTTP를 선택한 이유

MCP(Model Context Protocol)는 LLM이 외부 툴을 호출하는 방식을 표준화한 프로토콜이다.
전송 방식은 두 가지가 있다.

| 방식 | 동작 | 주 사용처 |
|---|---|---|
| stdio | 로컬 프로세스를 직접 실행, 표준 입출력으로 통신 | Claude Desktop, Cursor 등 로컬 IDE |
| Streamable HTTP | HTTP 엔드포인트로 JSON-RPC 요청/응답 | 서버 배포, 멀티 클라이언트, 원격 연동 |

공개 MCP 서버들도 이미 다양하게 나와 있다.
Vercel, Cloudflare, Zapier, DeepWiki 등은 모두 HTTP 방식으로 공개 MCP 엔드포인트를 제공한다.

**stdio를 선택하지 않은 이유**

stdio는 로컬 환경 전용이다.

- 서버를 실행하는 머신과 클라이언트가 같아야 한다
- 여러 클라이언트가 동시에 접근할 수 없다
- 배포 서버에 올릴 수 없다

우리가 만드는 환경은 EC2에 올라간 MCP 관리 서버가 여러 사용자의 요청을 받아 여러 MCP 서버로 라우팅해야 한다. stdio로는 이 구조 자체가 불가능하다.

---

## MCP 서버가 /health와 /mcp를 가져야 하는 이유

HTTP 방식에서 MCP 서버는 두 엔드포인트를 노출해야 한다.

```
GET  /health   →  { "status": "ok" }   ← MCP 관리 서버가 생존 여부 폴링
POST /mcp      →  JSON-RPC 2.0         ← 실제 툴 호출 진입점
```

**/health** — MCP 관리 서버가 60초마다 호출해 서버가 살아있는지 확인한다. 3회 연속 실패하면 INACTIVE로 전환되어 라우팅 대상에서 제외된다.

**/mcp** — MCP 표준 엔드포인트. tools/list, tools/call, resources/list 등 모든 JSON-RPC 메서드를 이 하나의 경로로 받는다. Vercel, DeepWiki 같은 공개 서버도 동일한 경로를 사용하기 때문에, MCP 관리 서버는 외부 공개 MCP 서버도 그대로 등록해서 사용할 수 있다.

---

## 전송 방식과 MCP 관리 서버의 관계

현재 POC는 EC2에 HTTP 방식으로 배포되어 있고, WEB 사용자는 HTTP를 통해 MCP 관리 서버에 요청한다.

추후 Claude Code나 Cursor 같은 로컬 IDE에 연동한다면 stdio 방식을 그대로 쓰면 된다.
이 경우 MCP 관리 서버는 라우터로서 필요 없다. stdio는 IDE가 MCP 서버를 직접 프로세스로 실행하고 1:1로 통신하기 때문에, 중간에서 라우팅을 처리하는 MCP 관리 서버가 끼어들 자리가 없다.

단, 인증과 크레딧은 여전히 필요하다. 사용자는 우리 서비스에 로그인해서 토큰을 발급받아 IDE에 등록해두고, MCP 서버가 그 토큰으로 직접 인증·크레딧 차감을 처리하는 구조가 될 것이다.

| 사용 시나리오 | 전송 방식 | MCP 관리 서버 역할 | 인증·크레딧 |
|---|---|---|---|
| WEB 사용자 → 서비스 툴 실행 (현재 POC) | HTTP | 라우팅·인증 중계 | MCP 관리 서버가 처리 |
| Claude Code / Cursor에서 우리 MCP 연결 | stdio | 불필요 | MCP 서버가 직접 처리 |

---

## 한 줄 정의

**MCP 관리 서버는 검증하고, 라우팅한다.**

인증·크레딧·서비스 실행 모두 외부 서버 담당.
MCP 관리 서버는 올바른 순서로 연결하는 중계자다. 크레딧 차감은 MCP 서버가 직접 처리한다.

---

## 왜 필요한가

연구소의 각 서비스(HWP 변환, PDF 압축, AI 이미지 처리 등)를 MCP 표준으로 감싸면, MCP 관리 서버 하나에 등록하는 것만으로 사용자에게 제공할 수 있다.

| 기존 방식 | MCP 관리 서버 방식 |
|---|---|
| 서비스마다 인증/크레딧 로직 직접 구현 | MCP 관리 서버 한 곳에서 일괄 처리 |
| 새 서비스 추가 시 클라이언트 코드 변경 | 배포 파이프라인에서 등록 한 줄 추가 |
| 서비스별 스펙 파편화 | MCP 표준(JSON-RPC 2.0)으로 통일 |

---

## 핵심 역할 3가지

### 1. MCP 서버 레지스트리

등록된 MCP 서버 목록을 관리한다.

- `POST /api/mcp/servers/register` — 서버 등록 (CI/CD 파이프라인에서 자동 호출)
- 등록 시 tools/list · resources/list 자동 수집
- 헬스체크 폴링 (60초 간격, 3회 연속 실패 → INACTIVE)

### 2. 크레딧 차감

MCP 서버가 Tool 실행 성공 후 크레딧 서버를 직접 호출한다.
MCP 관리 서버는 크레딧 차감에 개입하지 않으며, tools/call 요청 시 해당 툴의 `arguments.credit`을 자동 주입하는 역할만 한다.

```
MCP 관리 서버 → tools/call arguments에 credit 주입
    { "credit": { "serviceType": "GPT3", "deductCredit": 5 } }

MCP 서버 Tool 실행 성공
    → POST /credits/deduct { userId, serviceType, deductCredit } → 크레딧 서버 (MCP 서버가 직접)
```

### 3. WEB 공개 앱 래퍼 (Wrapper)

MCP 표준 응답(`content[{type, text}]`)은 사용자 노출에 필요한 정보가 없다.
Backoffice에서 설정한 앱·툴 메타데이터를 상위에 래핑해서 반환한다.

| Wrapper 필드 | 설명 |
|---|---|
| `displayName` | 사용자에게 보이는 앱 이름 |
| `thumbnail` | 앱 썸네일 이미지 URL |
| `isVisible` | 공개 노출 여부 |
| `tools[].serviceType` | 툴별 OSS 서비스 타입 |
| `tools[].deductCredit` | 툴별 실행 1회당 크레딧 |
| `tools[].visible` | 툴별 공개 노출 여부 |
| `creditUsed` | 이번 요청 소모 크레딧 |
| `trace[]` | 실행된 툴·서버·결과 전체 추적 |

---

## 주요 기능 확인

기준 URL: `http://3.38.49.228:8080`
Swagger UI: `http://3.38.49.228:8080/swagger-ui/index.html`

### MCP 서버 등록

```bash
curl -X POST http://3.38.49.228:8080/api/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "weather-mcp",
    "url": "http://172.17.0.1:5001",
    "description": "날씨 조회 서버",
    "version": "1.0.0",
    "type": "MCP"
  }'
```
```json
{ "serverId": "3dafa118-...", "status": "ACTIVE" }
```

등록 즉시 tools/list를 자동 호출해 툴 목록을 수집한다.
헬스체크는 60초 간격으로 폴링하며, 3회 연속 실패 시 INACTIVE로 전환된다.

### 실시간 툴 탐색 (Probe)

등록된 서버가 현재 어떤 툴을 제공하는지 실시간 확인. 캐싱된 데이터가 아니라 MCP 서버에 직접 JSON-RPC 요청을 보낸 응답이다.

```bash
curl "http://3.38.49.228:8080/api/mcp/servers/{serverId}/probe?method=tools/list"
```
```json
{
  "result": {
    "tools": [
      {
        "name": "get_current_weather",
        "description": "도시의 현재 날씨를 조회합니다.",
        "inputSchema": {
          "type": "object",
          "properties": { "city": { "type": "string" } },
          "required": ["city"]
        }
      }
    ]
  }
}
```

### Agent (자연어 → 툴 자동 선택 → 실행)

```bash
curl -X POST http://3.38.49.228:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{ "question": "서울 날씨 알려줘" }'
```
```json
{
  "answer": "서울의 현재 날씨는 맑음, 기온 22°C입니다.",
  "trace": [
    {
      "toolName": "get_current_weather",
      "server": "weather-mcp",
      "args": "{ \"city\": \"서울\" }",
      "result": "{ \"content\": [{ \"type\": \"text\", \"text\": \"맑음, 22°C\" }] }"
    }
  ]
}
```

### Backoffice 앱 설정

등록된 서버를 WEB에 공개하려면 Backoffice에서 메타데이터를 설정해야 한다.
기본값은 `isVisible=false`이며, 툴별로 `serviceType`과 `deductCredit`을 독립적으로 지정한다.

```bash
curl -X PATCH http://3.38.49.228:8080/api/mcp/apps/{appId} \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "날씨 조회",
    "isVisible": true,
    "description": "도시명으로 현재 날씨를 조회합니다.",
    "toolCredits": {
      "get_current_weather": { "serviceType": "GPT3", "deductCredit": 3, "visible": true },
      "get_weather_forecast": { "serviceType": "GPT3", "deductCredit": 5, "visible": true }
    }
  }'
```

```bash
# 공개 앱 목록 (WEB에서 호출)
curl http://3.38.49.228:8080/api/mcp/apps/public
```
```json
[
  {
    "id": "app-uuid",
    "displayName": "날씨 조회",
    "description": "도시명으로 현재 날씨를 조회합니다.",
    "thumbnail": null,
    "mcpUrl": "http://172.17.0.1:5001",
    "tools": [
      {
        "name": "get_current_weather",
        "description": "도시의 현재 날씨를 조회합니다.",
        "inputSchema": {
          "type": "object",
          "properties": { "city": { "type": "string" } },
          "required": ["city"]
        },
        "serviceType": "GPT3",
        "deductCredit": 3
      }
    ]
  }
]
```

`isVisible=true`인 앱만 공개 API에 노출되며, 툴 단위로 `visible=false` 설정 시 해당 툴은 응답에서 제외된다.
