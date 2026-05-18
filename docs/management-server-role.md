# MCP 관리 서버 — 역할과 구조

> 작성일: 2026-05-18 · 대상: 개발팀 / 발표용

---

## 한 줄 정의

> **MCP 관리 서버는 검증하고, 라우팅한다.**

인증·크레딧·서비스 실행 모두 외부 서버 담당.  
MCP 관리 서버는 올바른 순서로 연결하는 **허브**다. 크레딧 차감은 MCP 서버가 직접 처리한다.

---

## 왜 필요한가

연구소의 각 서비스(HWP 변환, PDF 압축, AI 이미지 처리 등)를 MCP 표준으로 감싸면, **MCP 관리 서버 하나에 등록하는 것만으로** 사용자에게 제공할 수 있다.

| 기존 방식 | MCP 관리 서버 방식 |
|---|---|
| 서비스마다 인증/크레딧 로직 직접 구현 | 관리 서버 한 곳에서 일괄 처리 |
| 새 서비스 추가 시 클라이언트 코드 변경 | 배포 파이프라인에서 등록 한 줄 추가 |
| 서비스별 스펙 파편화 | MCP 표준(JSON-RPC 2.0)으로 통일 |

---

## 핵심 역할 5가지

### 1. MCP 서버 레지스트리

등록된 MCP 서버 목록을 관리한다.

- `POST /api/mcp/servers/register` — 서버 등록 (CI/CD 파이프라인에서 자동 호출)
- 등록 시 `tools/list` · `resources/list` 자동 수집 → 툴 라우팅 맵 구성
- 헬스체크 폴링 (60초 간격, 3회 연속 실패 → `INACTIVE`)

### 2. 툴 라우팅

`tools/call` 요청이 들어오면 toolName을 보고 해당 MCP 서버로 포워딩한다.  
body 변환 없이 그대로 전달. 관리 서버는 중계만 한다.

```
tools/call { name: "hwpx_to_pdf", args: {...} }
    → 레지스트리 조회 → hwp-mcp 서버 URL 확인
    → POST https://hwp-mcp.internal/mcp (body 그대로)
```

### 3. 인증 (OAuth 임시토큰 교환)

사용자가 OAuth 서버에서 발급받은 **임시 토큰**을 관리 서버에 전달하면,  
관리 서버가 이를 OAuth 서버에서 **Access Token으로 교환**한 뒤 MCP 서버 요청에 실어 보낸다.  
시퀀스 다이어그램: [docs/img/sequence-diagram.md — Flow 3](./img/sequence-diagram.md)

### 4. 크레딧 차감

> **정책 미정** — MCP 서버가 크레딧 서버를 직접 호출하는 방식이 유력.

현재 유력한 흐름: MCP 서버가 Tool 실행 성공 후 크레딧 서버를 직접 호출한다.  
관리 서버는 크레딧 차감에 개입하지 않는다.

```
MCP 서버 Tool 실행 성공
    → POST /credits/deduct { userId, toolId } → 크레딧 서버 (MCP 서버가 직접)
```

### 5. WEB 공개 앱 래퍼 (Wrapper)

MCP 표준 응답(`content[{type, text}]`)은 사용자 노출에 필요한 정보가 없다.  
Backoffice에서 설정한 **앱 메타데이터를 상위에 래핑**해서 반환한다.

| Wrapper 필드 | 설명 |
|---|---|
| `displayName` | 사용자에게 보이는 앱 이름 |
| `thumbnail` | 앱 썸네일 이미지 URL |
| `credit` | 실행 1회당 크레딧 |
| `isVisible` | 공개 노출 여부 |
| `creditUsed` | 이번 요청 소모 크레딧 |
| `trace[]` | 실행된 툴·서버·결과 전체 추적 |

---

## 시연 가이드

> 기준 URL: `http://3.34.126.190:8080`

### 시연 1 — MCP 서버 등록

**UI**
1. `Servers` 메뉴 → `Register Server` 클릭
2. Name / URL / Description / Version 입력 → `Register`
3. 카드에 `ACTIVE` 상태 확인

**curl**
```bash
curl -X POST http://3.34.126.190:8080/api/mcp/servers/register \
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

등록 즉시 `tools/list`를 자동 호출해 툴 목록을 수집한다.  
헬스체크는 60초 간격으로 폴링하며, 3회 연속 실패 시 `INACTIVE`로 전환된다.

---

### 시연 2 — 실시간 툴 탐색 (Probe)

등록된 서버가 **지금 이 순간** 어떤 툴을 제공하는지 실시간 확인.

**UI**
- 서버 카드의 `tools/list` 버튼 클릭 → 모달에 실제 응답 출력

**curl**
```bash
curl "http://3.34.126.190:8080/api/mcp/servers/{serverId}/probe?method=tools/list"
```

```json
{
  "result": {
    "tools": [
      {
        "name": "get_weather",
        "description": "도시명으로 현재 날씨 조회",
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

캐싱된 데이터가 아니라 MCP 서버에 직접 JSON-RPC 요청을 보낸 응답이다.

---

### 시연 3 — Agent Demo (자연어 → 툴 자동 선택 → 실행)

**UI**
1. `Agent` 메뉴 → 채팅창에 자연어 질문 입력
2. 예: `"서울 날씨 알려줘"`
3. LLM이 `get_weather` 툴을 자동 선택 → 실행 결과 반환

**curl**
```bash
curl -X POST http://3.34.126.190:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{ "message": "서울 날씨 알려줘", "sessionId": "demo-001" }'
```

```json
{
  "answer": "서울의 현재 날씨는 맑음, 기온 22°C입니다.",
  "creditUsed": 5,
  "trace": [
    {
      "toolName": "get_weather",
      "server": "weather-mcp",
      "credit": 5,
      "args": { "city": "서울" },
      "result": { "content": [{ "type": "text", "text": "맑음, 22°C" }] }
    }
  ]
}
```

`trace` 배열에서 어느 서버의 어느 툴이 실행됐는지, 크레딧은 얼마 소모됐는지 확인 가능.

---

### 시연 4 — Backoffice 앱 설정

등록된 서버를 WEB에 공개하려면 Backoffice에서 메타데이터를 설정해야 한다.

**UI**
1. `Backoffice` 메뉴 → 앱 목록 확인 (등록 시 자동 생성, 기본값: `isVisible=false`)
2. `displayName`, `thumbnail`, `credit`, `isVisible` 설정

**curl**
```bash
# 앱 목록 조회
curl http://3.34.126.190:8080/api/mcp/apps

# 앱 메타데이터 설정
curl -X PATCH http://3.34.126.190:8080/api/mcp/apps/{appId} \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "날씨 조회",
    "credit": 3,
    "isVisible": true,
    "description": "도시명으로 현재 날씨를 조회합니다."
  }'

# 공개 앱 목록 (WEB에서 호출)
curl http://3.34.126.190:8080/api/mcp/apps/public
```

`isVisible=true`가 된 앱만 `GET /api/mcp/apps/public`에 노출된다.

---

## 현재 구현 상태

| 항목 | 상태 |
|---|---|
| MCP 프로토콜 (JSON-RPC 2.0, Streamable HTTP) | ✅ 완료 |
| 서버 등록 / 삭제 / 헬스체크 폴링 | ✅ 완료 |
| tools/list · resources/list 자동 수집 | ✅ 완료 |
| 툴 라우팅 (toolName → 서버 포워딩) | ✅ 완료 |
| 실시간 Probe API | ✅ 완료 |
| LLM 자동 툴 선택 (GPT-4o-mini) | ✅ 완료 |
| DB 영속화 (H2 파일 모드) | ✅ 완료 |
| Backoffice 앱 메타데이터 관리 | ✅ 완료 |
| WEB 공개 API + Wrapper 응답 | ✅ 완료 |
| MCP Proxy (외부 MCP 서버 연결) | ✅ 완료 |
| OAuth 임시토큰 교환 | 🔧 구현 중 |
| 크레딧 차감 연동 (외부 크레딧 서버) | ⏳ API 스펙 확정 후 |

