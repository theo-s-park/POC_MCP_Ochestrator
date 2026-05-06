# mcporchestrator

MCP 프로토콜 학습 및 오케스트레이터 구축 프로젝트.

## 문서

- [학습 과제 개요 및 단계별 계획](./docs/overview.md)
- [Phase 1 - MCP 서버 구현 및 LLM Tool Calling 검증](./docs/phase1.md)
- [Phase 4 - 멀티 서비스 MCP 연동 및 인증 전파](./docs/phase4.md)

## 실행

```bash
source .env.local && ./gradlew bootRun
```

## 테스트

```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "pick a random number between 1 and 100"}'
```
