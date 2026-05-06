# MCP 학습 과제

## 배경

추후 MCP 기반 연동 작업을 수행하기 위해 MCP 프로토콜을 서버/클라이언트 양쪽 모두 직접 구현해보는 학습 과제를 진행한다.

사전에 `demoagent` POC를 통해 "LLM이 JSON으로 도구 호출 지시 → 독립 HTTP 서버 호출 → 결과 반환" 흐름을 검증하였으며, 이 구조를 MCP 프로토콜로 전환하는 것을 목표로 한다.

---

## 단계별 과제

### [Phase 1](./phase1.md) ✅
**MCP 서버 구현 + LLM Tool Calling 검증**
- Spring AI 기반 MCP 서버 구성 및 MCP Inspector 검증
- OpenAI LLM 연결 및 자연어 기반 tool 호출 흐름 확인
- 산출물: `@Tool` 어노테이션 기반 MCP 서버 + AgentService

### Phase 2
**서버/호스트 분리 → 진짜 MCP 프로토콜 경유**
- MCP Server 프로젝트 분리
- MCP Client로 외부 서버에 연결
- LLM → MCP Client → HTTP → MCP Server → tool 실행 흐름 검증
- 산출물: 분리된 MCP Server + MCP Host(Client + LLM)

### Phase 3
**MCP 오케스트레이터 구축**
- 여러 MCP 서버(내 서버 + 외부 공개 서버) 동시 연결
- LLM이 여러 서버의 tool을 섞어서 사용하는 복합 시나리오 검증
- 산출물: 2개 이상 MCP 서버를 단일 엔드포인트로 묶는 오케스트레이터

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Java 25 |
| 프레임워크 | Spring Boot 4.x |
| MCP 지원 | Spring AI MCP Server / Client 2.0.0-M3 |
| Transport | Streamable HTTP |
| LLM | OpenAI GPT-4o-mini |
| 테스트 도구 | MCP Inspector |
