# Phase 2 - 서버/호스트 분리 및 MCP 프로토콜 경유 검증

## 목표

MCP Server 프로젝트를 분리하고, MCP Client로 내부/외부 MCP 서버 모두에 연결하여 `LLM → MCP Client → HTTP → MCP Server → tool 실행` 흐름을 검증한다.

---

## 구조 변경

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

---

## 구축 과정

`mcp-tool-server`를 별도 Spring Boot 프로젝트로 분리하고 RandomTool을 이관했다. `mcporchestrator`에서는 직접 등록했던 `ToolCallbackProvider` 빈을 제거하고 MCP Client 설정으로 전환했다.

```yaml
spring.ai.mcp.client.enabled=true
spring.ai.mcp.client.streamable-http.connections.tool-server.url=http://localhost:8081
```

추가로 외부 공개 MCP 서버에도 연결하여, 내 서버와 외부 서버의 tool을 LLM이 동시에 인지하고 사용할 수 있는지 검증했다. Spring AI MCP Client는 연결된 모든 서버의 tool 목록을 자동으로 수집하여 LLM에 전달한다.

---

## 검증 결과

### 내부 MCP 서버 호출

```
[Agent] 질문 수신: pick a random number between 1 and 100
[MCP Client] tools/call → http://localhost:8081/mcp
[mcp-tool-server] [Tool:random] LLM이 도구 호출 → minVal=1, maxVal=100
[mcp-tool-server] [Tool:random] 도구 실행 결과 → 42
[Agent] 최종 응답: The random number between 1 and 100 is 42.
```

tool 실행 로그가 **`mcp-tool-server` 프로세스에 찍힌다**. LLM의 tool 호출이 MCP 프로토콜(JSON-RPC 2.0)을 경유하여 별도 서버에서 실행됨을 확인했다.

---

### 외부 MCP 서버 연결

공식 MCP 테스트 서버(`@modelcontextprotocol/server-everything`)를 외부 MCP 서버로 연결하여 검증했다.

```yaml
spring.ai.mcp.client.stdio.connections.everything-server.command=npx
spring.ai.mcp.client.stdio.connections.everything-server.args=-y,@modelcontextprotocol/server-everything
```

mcporchestrator 기동 시 MCP Client가 연결된 모든 서버에서 tool 목록을 자동 수집한다.

```
[MCP Client] 연결된 서버: mcp-tool-server (localhost:8081)
[MCP Client] 수집된 tool: random

[MCP Client] 연결된 서버: everything-server
[MCP Client] 수집된 tool: echo, add, longRunningOperation, printEnv ...
```

외부 서버 tool 호출 검증:

```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "echo the message hello mcp"}'
```

```
[Agent] 질문 수신: echo the message hello mcp
[MCP Client] tools/call → everything-server / echo
[everything-server] tool 실행 결과 → "hello mcp"
[Agent] 최종 응답: The echo result is "hello mcp".
```

LLM이 질문 내용을 분석하여 `mcp-tool-server`의 `random`과 `everything-server`의 `echo` 중 적절한 tool을 자율적으로 선택했다.

---

## Phase 1과의 차이

| | Phase 1 | Phase 2 |
|---|---|---|
| tool 실행 위치 | mcporchestrator 인-프로세스 | mcp-tool-server 별도 프로세스 |
| 통신 방식 | 직접 메서드 호출 | MCP JSON-RPC 2.0 (HTTP) |
| MCP 프로토콜 경유 | ❌ | ✅ |
| 외부 MCP 서버 연결 | ❌ | ✅ |

---

## 산출물

- 분리된 `mcp-tool-server` (Spring Boot MCP Server)
- 내부/외부 MCP 서버 tool을 통합 호출하는 `mcporchestrator` (Spring Boot MCP Client + LLM)

이로써 **"LLM → MCP Client → HTTP → MCP Server"** 흐름이 실제로 동작함을 검증했다. 다음 단계는 여러 MCP 서버를 단일 엔드포인트로 묶는 오케스트레이터 구축이다.

---

## 다음 단계

→ [Phase 3](./phase3.md): MCP 오케스트레이터 구축
