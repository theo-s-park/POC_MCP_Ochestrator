# MCP Hub 연동 가이드

이 문서는 MCP Hub에 연동할 앱을 만들 때 프롬프트에 포함할 내용을 정리한 것입니다.

---

## MCP Hub란?

MCP Hub는 여러 MCP 서버와 웹앱을 등록·관리하고, PO Web 사용자가 MCP 도구(PO Tools)를 통해 기능을 사용할 수 있도록 중개하는 오케스트레이터입니다.

- **등록 대상**: MCP 서버 (tools/list, tools/call 구현체) 또는 일반 웹앱 (WEBAPP)
- **통신 방식**: HTTP JSON-RPC 2.0
- **Hub 주소**: `http://<HUB_HOST>:8080`

---

## 등록 유형

| type    | 설명 | Hub가 하는 일 |
|---------|------|--------------|
| `MCP`   | MCP 프로토콜을 구현한 서버 | tools/list 수집, LLM Agent에 tool 노출 |
| `WEBAPP`| 일반 HTTP 웹앱 | 헬스체크만, tool 없음 |

---

## MCP 서버로 만들기

### Python (FastMCP 권장)

```python
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("my-server")

@mcp.tool()
def my_tool(param: str) -> str:
    """도구 설명을 여기에 작성하세요."""
    return f"결과: {param}"

if __name__ == "__main__":
    mcp.run(transport="streamable-http", host="0.0.0.0", port=8081)
```

FastMCP는 자동으로 `POST /mcp` 엔드포인트를 노출하고 MCP 프로토콜(initialize, tools/list, tools/call 등)을 처리합니다.

설치:
```bash
pip install mcp fastmcp
```

### Node.js

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

const server = new McpServer({ name: "my-server", version: "1.0.0" });

server.tool("my_tool", { param: z.string() }, async ({ param }) => ({
  content: [{ type: "text", text: `결과: ${param}` }]
}));

const transport = new StreamableHTTPServerTransport({ port: 8081 });
await server.connect(transport);
```

### Spring Boot

```java
// build.gradle
implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
```

```yaml
# application.yml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: my-server
        version: 1.0.0
```

```java
@Service
public class MyToolService {
    @Tool(description = "도구 설명")
    public String myTool(String param) {
        return "결과: " + param;
    }
}
```

---

## 필수 구현 엔드포인트

MCP 서버는 반드시 아래 JSON-RPC 2.0 메서드를 `POST /mcp`에서 처리해야 합니다.

| 메서드 | 필수 여부 | 설명 |
|--------|-----------|------|
| `initialize` | 필수 | 서버 정보 및 capabilities 반환 |
| `ping` | 필수 | 헬스체크용 |
| `tools/list` | 필수 | 사용 가능한 tool 목록 반환 |
| `tools/call` | 필수 | tool 실행 |
| `resources/list` | 선택 | 리소스 목록 |
| `resources/read` | 선택 | 리소스 내용 반환 |

---

## MCP Hub에 등록하기

앱 배포 후 아래 API를 호출해 Hub에 등록합니다.

### MCP 서버 등록

```bash
curl -X POST http://<HUB_HOST>:8080/api/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-server",
    "url": "http://<APP_HOST>:<PORT>",
    "description": "서버 설명",
    "version": "1.0.0",
    "type": "MCP"
  }'
```

응답:
```json
{ "serverId": "uuid", "status": "ACTIVE" }
```

### 일반 웹앱 등록

```bash
curl -X POST http://<HUB_HOST>:8080/api/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-webapp",
    "url": "http://<APP_HOST>:<PORT>",
    "description": "웹앱 설명",
    "type": "WEBAPP"
  }'
```

---

## CI/CD 연동 예시

GitHub Actions:

```yaml
- name: Register to MCP Hub
  run: |
    curl -X POST ${{ secrets.MCP_HUB_URL }}/api/mcp/servers/register \
      -H "Content-Type: application/json" \
      -d '{
        "name": "${{ env.APP_NAME }}",
        "url": "${{ env.APP_URL }}",
        "description": "${{ env.APP_DESCRIPTION }}",
        "version": "${{ github.sha }}",
        "type": "MCP"
      }'
```

---

## 바이브코딩 프롬프트 예시

아래 내용을 AI 코딩 도구에 붙여넣으면 MCP Hub에 바로 연동 가능한 앱을 생성할 수 있습니다.

```
다음 조건으로 Python MCP 서버를 만들어줘:

1. FastMCP 라이브러리 사용
2. POST /mcp 엔드포인트로 MCP 프로토콜 노출 (streamable-http transport)
3. 포트: 8081
4. 구현할 도구:
   - [여기에 원하는 도구 설명]

5. 배포 후 아래 API로 MCP Hub에 자동 등록하는 스크립트(register.sh)도 만들어줘:
   POST http://<HUB_HOST>:8080/api/mcp/servers/register
   { "name": "<서버이름>", "url": "http://<배포주소>:8081", "type": "MCP" }
```

---

## 주의사항

- `url`은 베이스 URL만 입력 (`/mcp` 경로는 자동 추가됨)
- MCP 서버는 등록 시점에 ACTIVE 상태여야 tools/list가 수집됨
- 서버가 내려가도 등록은 유지되며, 재기동 후 헬스체크 통과 시 자동으로 ACTIVE 복귀
- WEBAPP 타입은 tool이 수집되지 않으며 LLM Agent에 노출되지 않음
