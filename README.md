# MCP Orchestrator

여러 MCP 서버를 중앙에서 등록·관리하고, 등록된 Tool을 Agent가 자동으로 선택·실행하는 MCP 관리 서버 플랫폼.

## 개요

```
외부 MCP 서버 A ──┐
외부 MCP 서버 B ──┼──▶ MCP Orchestrator ──▶ Agent (LLM) ──▶ 사용자
외부 MCP 서버 C ──┘         │
                        백오피스 / Web UI
```

- MCP 서버를 URL 하나로 등록하면 Tool/Resource를 자동 수집
- 등록된 모든 Tool을 LLM Agent가 자연어로 선택·실행
- 백오피스에서 앱 메타 관리, Web에서 사용자에게 노출

## 빠른 시작

### 로컬 실행

```bash
# 환경변수 설정 (.env.local)
OPENAI_API_KEY=sk-...

# 실행
./gradlew bootRun
```

접속: `http://localhost:8080`

### EC2 배포 (Docker)

```bash
# EC2에서
git clone https://github.com/theo-s-park/POC_MCP_Ochestrator.git mcporchestrator-src
cd mcporchestrator-src
docker build -t mcporchestrator:latest .
docker run -d \
  --name mcporchestrator \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /home/ec2-user/data:/app/data \
  -e OPENAI_API_KEY=sk-... \
  mcporchestrator:latest
```

## UI 페이지

| URL | 설명 |
|-----|------|
| `/` 또는 `/servers` | 등록된 MCP 서버 목록 및 등록 |
| `/tools` | 수집된 Tool 전체 목록 |
| `/agent` | Agent 채팅 (자연어 → Tool 자동 실행) |
| `/web` | 공개 앱 마켓플레이스 (isVisible=true 앱) |
| `/backoffice` | 앱 메타데이터 관리 |
| `/demo` | 데모 |

## 문서

- [시스템 아키텍처 및 통신 구조](./docs/architecture.md)
- [전체 API 명세](./docs/api-spec.md)
- [MCP 서버 등록 및 사용 가이드](./docs/getting-started.md)
- [MCP 통신 규약](./docs/wiki-mcp-spec.md)
