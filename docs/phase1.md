# Phase 1 - MCP 서버 구현 및 LLM Tool Calling 검증

## 목표

Spring AI 기반 MCP 서버를 구성하고, LLM이 자연어 질문으로 tool을 자율 호출하는 흐름을 검증한다.

---

## 구축 과정

첫 번째 단계로 Spring Boot 4 + Spring AI 2.0.0-M3 기반의 MCP 서버를 구성하고, demoagent POC에서 HTTP 서버로 구현했던 난수 생성 도구를 `@Tool` 어노테이션으로 재구현했다. `ToolCallbackProvider` 빈을 등록함으로써 Spring AI가 해당 메서드를 MCP 도구로 인식하도록 했고, STREAMABLE transport로 `http://localhost:8080/mcp` 엔드포인트를 노출했다. 서버를 기동한 뒤 MCP Inspector에서 Streamable HTTP transport로 연결하여 tool list 조회 및 tool call 실행을 확인했다.

이어서 OpenAI(GPT-4o-mini)를 LLM으로 연결하고, 자연어 질문을 받아 응답을 반환하는 `AgentController` / `AgentService`를 구현했다. `ChatClient`에 `ToolCallbackProvider`를 등록하여 LLM이 tool 목록을 인지하도록 했고, "1에서 100 사이 난수를 뽑아줘"라는 자연어 질문을 보내자 LLM이 스스로 `random` tool 호출이 필요하다고 판단하고 `minVal=1, maxVal=100` 인자를 구성해 실행한 뒤 결과를 자연어로 반환하는 것을 로그로 확인했다.

다만 이 시점에서 tool 실행은 MCP 프로토콜을 경유하지 않고 Spring AI의 `DefaultToolCallingManager`가 인-프로세스로 직접 호출하는 방식이다. MCP 서버는 Inspector 연결용으로 노출되어 있을 뿐, 에이전트 흐름과는 분리된 상태다. 즉 이 단계에서 검증한 것은 **"LLM이 tool 호출 여부를 스스로 판단하고 실행할 수 있다"** 는 것이며, MCP 프로토콜이 실제로 개입하는 흐름은 서버와 호스트를 물리적으로 분리하는 다음 단계에서 검증한다.

---

## 검증 결과

```
[Agent] 질문 수신: pick a random number between 1 and 100
DefaultToolCallingManager: Executing tool call: random
[Tool:random] LLM이 도구 호출 → minVal=1, maxVal=100
[Tool:random] 도구 실행 결과 → 87
[Agent] 최종 응답: The random number between 1 and 100 is 87.
```

---

## 산출물

| 파일 | 역할 |
|------|------|
| `tool/RandomTool.java` | `@Tool` 어노테이션 기반 난수 도구 |
| `agent/AgentService.java` | ChatClient + ToolCallbackProvider 연결 |
| `agent/AgentController.java` | POST /agent/chat 엔드포인트 |
| `application.properties` | MCP 서버 설정 + OpenAI 설정 |

---

## 다음 단계

→ [Phase 2](./phase2.md): 서버/호스트 분리 후 MCP 프로토콜 실제 경유 검증
