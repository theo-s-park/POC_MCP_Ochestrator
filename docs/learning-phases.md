# MCP 프로토콜 직접 구현 학습 과제

추후 MCP 기반 연동 작업을 수행하기 위해 MCP 프로토콜을 서버/클라이언트 양쪽 모두 직접 구현해보는 학습 과제를 진행한다.

사전에 demoagent POC를 통해 "LLM이 JSON으로 도구 호출 지시 → 독립 HTTP 서버 호출 → 결과 반환" 흐름을 검증하였으며, 이 구조를 MCP 프로토콜로 전환하는 것을 목표로 한다.

---

## Phase 1 — MCP 서버 구현 + LLM Tool Calling 검증

**목표**
Spring AI 기반 MCP 서버 구성 및 MCP Inspector 검증, OpenAI LLM 연결 및 자연어 기반 tool 호출 흐름 확인

**산출물**
- `@Tool` 어노테이션 기반 MCP 서버 + AgentService
- MCP Inspector에서 tool list 조회 및 tool call 성공하는 Spring Boot MCP 서버

추후 MCP 기반 연동 작업을 준비하기 위해 MCP 프로토콜을 직접 구현해보는 학습 과제를 시작했다.

첫 번째 단계로 Spring Boot 4 + Spring AI 2.0.0-M3 기반의 MCP 서버를 구성하고, demoagent POC에서 HTTP 서버로 구현했던 난수 생성 도구를 `@Tool` 어노테이션으로 재구현했다. `ToolCallbackProvider` 빈을 등록함으로써 Spring AI가 해당 메서드를 MCP 도구로 인식하도록 했고, STREAMABLE transport로 `http://localhost:8080/mcp` 엔드포인트를 노출했다. 서버를 기동한 뒤 MCP Inspector에서 Streamable HTTP transport로 연결하여 tool list 조회 및 tool call 실행을 확인했다.

> 📷 **이미지**: MCP Inspector에서 tool list 조회된 화면

이어서 OpenAI를 LLM으로 연결하고, 자연어 질문을 받아 응답을 반환하는 `AgentController` / `AgentService`를 구현했다. `ChatClient`에 `ToolCallbackProvider`를 등록하여 LLM이 tool 목록을 인지하도록 했고, "1에서 100 사이 난수를 뽑아줘"라는 자연어 질문을 보내자 LLM이 스스로 random tool 호출이 필요하다고 판단하고 `minVal=1, maxVal=100` 인자를 구성해 실행한 뒤 결과를 자연어로 반환하는 것을 로그로 확인했다.

다만 이 시점에서 tool 실행은 MCP 프로토콜을 경유하지 않고 Spring AI의 `DefaultToolCallingManager`가 인-프로세스로 직접 호출하는 방식이다. MCP 서버는 Inspector 연결용으로 노출되어 있을 뿐, 에이전트 흐름과는 분리된 상태다. 즉 이 단계에서 검증한 것은 "LLM이 tool 호출 여부를 스스로 판단하고 실행할 수 있다"는 것이며, MCP 프로토콜이 실제로 개입하는 흐름은 서버와 호스트를 물리적으로 분리하는 다음 단계에서 검증한다.

> 📷 **이미지**: LLM이 tool 호출하고 자연어 응답 반환하는 로그 캡쳐

---

## Phase 2 — 서버/호스트 분리 → 진짜 MCP 프로토콜 경유

**목표**
MCP Server 프로젝트 분리, MCP Client로 외부 서버에 연결, LLM → MCP Client → HTTP → MCP Server → tool 실행 흐름 검증

**산출물**
- 분리된 MCP Server + MCP Host (Client + LLM)
- 외부 MCP 서버의 tool을 호출하고 결과를 받아오는 Spring Boot MCP 클라이언트

### 구조 변경

**Phase 1**
```
mcporchestrator
  └─ RandomTool (@Tool)
  └─ AgentService (LLM + 직접 호출)
```

**Phase 2**
```
mcp-tool-server (포트 8081)      ← 직접 구현한 MCP 서버
  └─ RandomTool

mcporchestrator (포트 8080)      ← MCP Host + Client
  └─ MCP Client A → http://localhost:8081/mcp (내부)
  └─ MCP Client B → 외부 공개 MCP 서버
  └─ AgentService (LLM + MCP Client 경유)
```

### 구축 과정

`mcp-tool-server`를 별도 Spring Boot 프로젝트로 분리하고 `RandomTool`을 이관했다. `mcporchestrator`에서는 직접 등록했던 `ToolCallbackProvider` 빈을 제거하고 MCP Client 설정으로 전환했다.

```properties
spring.ai.mcp.client.enabled=true
spring.ai.mcp.client.streamable-http.connections.tool-server.url=http://localhost:8081
```

추가로 외부 공개 MCP 서버에도 연결하여, 내 서버와 외부 서버의 tool을 LLM이 동시에 인지하고 사용할 수 있는지 검증했다. Spring AI MCP Client는 연결된 모든 서버의 tool 목록을 자동으로 수집하여 LLM에 전달한다.

### 검증 결과

**내부 MCP 서버 호출**

```
[Agent] 질문 수신: pick a random number between 1 and 100
[MCP Client] tools/call → http://localhost:8081/mcp
[mcp-tool-server] [Tool:random] LLM이 도구 호출 → minVal=1, maxVal=100
[mcp-tool-server] [Tool:random] 도구 실행 결과 → 42
[Agent] 최종 응답: The random number between 1 and 100 is 42.
```

tool 실행 로그가 `mcp-tool-server` 프로세스에 찍힌다. LLM의 tool 호출이 MCP 프로토콜(JSON-RPC 2.0)을 경유하여 별도 서버에서 실행됨을 확인했다.

> 📷 **이미지**: mcp-tool-server 터미널에 로그 찍힌 화면

**외부 MCP 서버 연결**

공식 MCP 테스트 서버(`@modelcontextprotocol/server-everything`)를 외부 MCP 서버로 연결하여 검증했다.

```properties
spring.ai.mcp.client.stdio.connections.everything-server.command=npx
spring.ai.mcp.client.stdio.connections.everything-server.args=-y,@modelcontextprotocol/server-everything
```

```
[MCP Client] 연결된 서버: mcp-tool-server (localhost:8081)
[MCP Client] 수집된 tool: random

[MCP Client] 연결된 서버: everything-server
[MCP Client] 수집된 tool: echo, add, longRunningOperation, printEnv ...
```

```
[Agent] 질문 수신: echo the message hello mcp
[MCP Client] tools/call → everything-server / echo
[everything-server] tool 실행 결과 → "hello mcp"
[Agent] 최종 응답: The echo result is "hello mcp".
```

LLM이 질문 내용을 분석하여 `mcp-tool-server`의 `random`과 `everything-server`의 `echo` 중 적절한 tool을 자율적으로 선택했다.

> 📷 **이미지**: everything-server tool 목록이 수집된 화면

---

## Phase 3 — MCP 오케스트레이터 구축

**목표**
여러 MCP 서버(내 서버 + 외부 공개 서버)를 단일 엔드포인트로 묶는 오케스트레이터 구축 및 외부 MCP 서버 제어 검증

**산출물**
- 2개 이상 MCP 서버를 단일 엔드포인트로 묶는 오케스트레이터
- 서버 관리 UI (등록/삭제/상태), Tool 탐색, Agent Demo

### 구조 변경

**Phase 2**
```
mcporchestrator (포트 8080)
  └─ MCP Client A → http://localhost:8081/mcp  (설정 파일에 정적 등록)
  └─ MCP Client B → 외부 공개 MCP 서버         (설정 파일에 정적 등록)
  └─ AgentService
```

**Phase 3**
```
mcporchestrator (포트 8080)      ← MCP 오케스트레이터
  └─ McpServerRegistry           (런타임 서버 등록/삭제)
  └─ McpProxyController          (단일 /mcp 엔드포인트, toolName 기반 라우팅)
  └─ HealthCheckPoller           (60초 폴링, 3회 실패 → INACTIVE)

  등록된 서버들 (런타임에 추가)
  └─ mcp-tool-server  → http://localhost:8081/mcp
  └─ everything-server → http://localhost:3001/mcp  (stdio→HTTP 브릿지)
  └─ playwright        → http://localhost:3003/mcp  (stdio→HTTP 브릿지)
```

### 구축 과정

Phase 2에서는 MCP Client가 설정 파일에 서버 목록을 정적으로 들고 있었다. 서버가 늘어날수록 설정 변경 + 재시작이 필요한 구조다. Phase 3에서는 Spring AI MCP Client를 걷어내고 오케스트레이터를 직접 구현했다.

책임을 세 컴포넌트로 분리했다.

| 컴포넌트 | 역할 |
|---|---|
| `McpServerRegistry` | 서버 목록 저장 (ConcurrentHashMap) |
| `ToolsListCollector` | 서버 등록 시 `tools/list` 자동 수집 |
| `McpProxyController` | 요청 수신 → `toolName`으로 서버 조회 → 포워딩 |
| `HealthCheckPoller` | 60초마다 `GET /health` 폴링, 3회 실패 시 INACTIVE |

툴 라우팅은 `toolName → serverId` 맵으로 처리한다. 서버 등록 시 `tools/list`를 수집하면서 이 맵을 채우고, `tools/call`이 들어오면 툴 이름으로 서버를 찾아 body를 그대로 포워딩한다.

> 📷 **이미지**: 서버 등록 화면 (`servers-register.png`)

> 📷 **이미지**: Tool 목록 화면 (`tools-schema.png`)

**stdio → HTTP 브릿지**

세상의 MCP 서버 대부분은 stdio 방식이다. 오케스트레이터는 네트워크 너머의 서버를 관리해야 하므로 HTTP가 전제 조건이다. stdio MCP를 HTTP로 노출하기 위해 Node.js 브릿지를 구현했다.

```bash
# Playwright MCP를 HTTP로 노출
PORT=3003 node bridge.js npx -y @playwright/mcp@latest
```

어떤 stdio MCP든 이 브릿지를 통해 HTTP로 노출할 수 있다. 브릿지 코드는 60줄이다.

### 검증 결과

**서버 등록 및 tool 수집**

3개 서버를 순서대로 등록하자 각 서버의 tool 목록이 자동으로 수집됐다.

```
[ToolsListCollector] mcp-tool-server   → tool 수집 완료 (1개)
[ToolsListCollector] everything-server → tool 수집 완료 (17개)
[ToolsListCollector] playwright        → tool 수집 완료 (23개)
```

**Playwright MCP — 브라우저 제어**

Microsoft의 공식 Playwright MCP를 오케스트레이터에 URL 하나로 등록하고, `browser_navigate`와 `browser_take_screenshot`을 실행했다. 오케스트레이터 코드는 한 줄도 바꾸지 않았다.

```
[HealthCheck] active: playwright
[McpProxy] tool=browser_navigate        routed-to=playwright
[McpProxy] tool=browser_take_screenshot routed-to=playwright
```

`browser_navigate`로 Google Chrome을 실제로 열었고, 검색창에 "roblox"를 입력한 상태에서 `browser_take_screenshot`을 실행하자 검색 결과 화면이 PNG로 반환됐다.

> 📷 **이미지**: 오케스트레이터에서 browser_navigate 실행 순간 (`playwright-orchestrator.png`)

> 📷 **이미지**: Playwright가 실제로 띄운 Chrome (`playwright-google.png`)

> 📷 **이미지**: browser_take_screenshot으로 캡쳐한 roblox 검색 결과 (`playwright-screenshot.png`)

**MCP 서버는 누가 호출하는지 모른다.** 오케스트레이터든 Claude Desktop이든 동일한 `POST /mcp` 요청이기 때문이다. 이것이 오케스트레이터 패턴이 성립하는 이유다.

| 항목 | 내용 |
|---|---|
| 오케스트레이터 코드 변경 | 0줄 |
| 브릿지 코드 | Node.js 60줄 |
| 등록 방법 | URL 하나 입력 |
| 결과 | 실제 브라우저 제어 성공 |

---

## Phase 4 — 멀티 서비스 MCP 연동 + 인증 전파

**목표**
실제 서비스와 오케스트레이터를 연결하여 인증 토큰 전파, 크레딧 차감이 MCP 호출과 함께 동작하는 흐름 검증

**산출물**
- DB 영속화 (서버 레지스트리, 툴 매핑)
- PAT 검증 필터 → PO 인증 서버 연동
- tools/call 성공 후 크레딧 차감 → 크레딧 서버 연동
- 자연어 → LLM → 툴 자동 선택 → 멀티 MCP 실행 시나리오 동작 검증

### 구조

```
[PO 사용자]
    자연어 입력
         ↓
[LLM Agent]
    툴 자동 선택
         ↓  POST /mcp  Authorization: Bearer {PAT}
[오케스트레이터]
    ① PAT 검증   → GET /auth/validate   → PO 인증 서버
    ② 툴 라우팅  → body 그대로 포워딩
    ③ 크레딧 차감 → POST /credits/deduct → 크레딧 서버
         ↓
[연구소 MCP 서버]
    실제 서비스 실행
         ↓
    결과 반환 → 사용자
```

### 핵심 검증 항목

**인증 토큰 전파**
- `Authorization: Bearer {PAT}` 헤더로 요청 진입
- 오케스트레이터가 PAT를 인증 서버에 검증 → `userId` 획득
- MCP 서버로 포워딩 시 내부 토큰으로 전달

**크레딧 차감 시점**
- `tools/call` 요청이 들어온 시점이 아닌, **MCP 서버의 성공 응답 수신 후** 차감
- 실패한 요청은 차감하지 않는다
- 크레딧 단가는 크레딧 서버에서 관리. 오케스트레이터는 `toolId`만 전달

**DB 영속화**
- Phase 3까지는 서버 레지스트리를 인메모리(ConcurrentHashMap)로 관리
- Phase 4에서 H2 파일 모드로 전환하여 재시작 후에도 등록 상태 유지
- 추후 RDS 전환 시 `application.yml` datasource 설정만 변경

### 구축 과정

Phase 3의 오케스트레이터는 직접 툴을 선택하는 UI(Agent Demo)를 제공했지만, 자연어를 이해하는 LLM이 없었다. Phase 4에서는 Spring AI `ChatClient`와 동적 `ToolCallback` 등록을 결합하여 LLM이 툴을 자율 선택하는 에이전트 레이어를 추가했다.

| 컴포넌트 | 역할 |
|---|---|
| `AgentService` | 세션 생성, 등록된 전체 툴을 `ToolCallback` 리스트로 변환, `ChatClient`에 주입 |
| `McpToolCallback` | Spring AI `ToolCallback` 구현체. LLM 호출 → MCP 서버 포워딩 → 결과 반환 |
| `AgentResponse` | sessionId, question, answer, tool trace 리스트를 담는 응답 레코드 |
| `AgentController` | `POST /agent/chat` 엔드포인트 |

LLM은 요청마다 등록된 전체 툴 스키마를 받고, 어떤 툴을 어떤 순서로 호출할지 자율 결정한다. 오케스트레이터는 LLM의 tool call을 가로채 각 MCP 서버로 라우팅만 한다.

**중복 툴 처리**

여러 서버가 같은 이름의 툴을 갖는 경우(예: 두 서버 모두 `echo` 보유) Spring AI가 충돌 에러를 낸다. `AgentService`에서 `HashSet`으로 중복 제거하여 먼저 등록된 서버의 툴을 우선한다.

**세션 트레이싱**

요청마다 8자리 UUID를 sessionId로 발급하고 해당 세션의 전체 tool call 로그를 하나의 `trace` 리스트에 수집한다.

```
[Agent][38f4d4db] question: "25에서 800 사이의 랜덤 숫자..."
[Agent][38f4d4db] available tools: 36 -> [random, echo, get-sum, browser_navigate, ...]
[Agent][38f4d4db] tool call  -> random        | server=random | args={"min":25,"max":800}
[Agent][38f4d4db] tool result <- random        | result={"content":[{"text":"86"}]}
[Agent][38f4d4db] tool call  -> get-sum       | server=every  | args={"a":86,"b":50}
[Agent][38f4d4db] tool result <- get-sum       | result={"content":[{"text":"The sum of 86 and 50 is 136."}]}
[Agent][38f4d4db] tool call  -> browser_navigate | server=fly  | args={"url":"https://www.google.com/search?q=136"}
[Agent][38f4d4db] tool result <- browser_navigate | result={"content":[{"text":"### Ran Playwright code..."}]}
[Agent][38f4d4db] answer: "랜덤으로 뽑힌 숫자는 86이며, 50을 더한 결과는 136입니다..."
```

**Agent Chat UI**

`/agent` 경로에 채팅 UI를 추가했다. 자연어 입력 → 응답 표시 → Tool Trace 펼쳐보기 구조로, 각 tool call의 args와 result를 확인하고 전체 trace를 JSON으로 복사할 수 있다.

> 📷 **이미지**: Agent Chat UI — 질문 입력 및 응답 화면 (`agent-chat.png`)

> 📷 **이미지**: Tool Trace 펼친 상태 (`agent-trace.png`)

### 검증 결과

**멀티 서버 자동 체이닝**

질문: `"25에서 800 사이의 랜덤 숫자를 뽑아서 구글에서 검색해줘. 그 숫자에 50을 더한 값을 찾아줘"`

LLM이 3개 서버에 걸친 툴 체이닝을 자율 수행했다.

| 순서 | 툴 | 서버 | 입력 | 출력 |
|---|---|---|---|---|
| 1 | `random` | random | `{"min":25,"max":800}` | `86` |
| 2 | `get-sum` | every | `{"a":86,"b":50}` | `136` |
| 3 | `browser_navigate` | fly | `{"url":"https://www.google.com/search?q=136"}` | 브라우저 오픈 성공 |

오케스트레이터 코드는 변경 없이 서버 3개가 자연어 한 문장으로 연결됐다. LLM은 `add` 툴 대신 같은 기능의 `get-sum`을 스스로 찾아 사용했다.

### 미결 사항 (외부 협의 필요)

| 항목 | 담당 |
|---|---|
| PAT 포맷 및 `/auth/validate` API 스펙 | Woodie |
| `/credits/deduct` API 스펙 | Woodie |
| userId 식별 방식 (PAT에서 추출 여부) | Woodie |
| 크레딧 부족 시 요청 차단 여부 | 전체 |
