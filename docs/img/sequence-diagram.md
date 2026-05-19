# MCP 관리 서버 시퀀스 다이어그램

> **색상 범례**  
> 🟡 노란 배경 = **JSON-RPC 2.0** 통신  
> 🔵 파란 배경 = **REST HTTP** 통신  
> 🟢 초록 배경 = 일반 HTTP  
> 🟣 보라 배경 = 내부 처리

---

## Flow 1: MCP 서버 등록 → Backoffice 설정 → WEB 공개

```mermaid
sequenceDiagram
    autonumber
    participant CICD as MCP 서버 CI/CD
    participant SRV  as MCP/WEBAPP 서버
    participant MCP  as MCP 관리 서버
    participant BO   as Backoffice 관리자
    participant WEB  as WEB 사용자

    rect rgb(235, 251, 238)
        note over CICD,MCP: 📦 REST — 서버 등록 요청
        CICD->>MCP: [REST] POST /api/mcp/servers/register<br/>(name, url, version, type: "MCP" | "WEBAPP")
        MCP-->>CICD: { serverId, status: "ACTIVE" }
    end

    alt type = MCP
        rect rgb(255, 243, 191)
            note over MCP,SRV: 🔌 JSON-RPC 2.0 — Tool/Resource 자동 수집
            MCP->>SRV: [JSON-RPC] POST /mcp → tools/list
            SRV-->>MCP: { tools: [{ name, description, inputSchema }] }
            MCP->>SRV: [JSON-RPC] POST /mcp → resources/list
            SRV-->>MCP: { resources: [{ uri, name, mimeType }] }
        end
    else type = WEBAPP
        rect rgb(227, 250, 252)
            note over MCP,SRV: 🔌 REST — Tool/Resource 자동 수집
            MCP->>SRV: [REST] GET /tools
            SRV-->>MCP: { tools: [{ name, description, inputSchema }] }
            MCP->>SRV: [REST] GET /resources
            SRV-->>MCP: { resources: [{ uri, name, mimeType }] }
        end
    end

    rect rgb(243, 240, 255)
        note over MCP: 📝 McpApp 자동 생성 (isVisible=false)
    end

    rect rgb(243, 240, 255)
        note over MCP,BO: ⚙️ REST — Backoffice 앱 메타 설정
        BO->>MCP: [REST] GET /api/mcp/apps
        MCP-->>BO: 앱 목록
        BO->>MCP: [REST] PATCH /api/mcp/apps/{id}<br/>(isVisible=true, displayName=...)
        MCP-->>BO: { status: "ok" }
    end

    rect rgb(235, 251, 238)
        note over MCP,WEB: 🌐 REST — WEB 공개 앱 노출
        WEB->>MCP: [REST] GET /api/mcp/apps/public
        MCP-->>WEB: 공개 앱 목록 (isVisible=true)
    end
```

---

## Flow 2: 사용자 Tool 실행 (Agent 경유)

```mermaid
sequenceDiagram
    autonumber
    participant WEB as WEB 사용자
    participant MCP as MCP 관리 서버
    participant LLM as LLM Agent (Claude)
    participant HWP as hwp-converter (MCP 타입)

    rect rgb(235, 251, 238)
        WEB->>MCP: [REST] POST /agent/chat<br/>{ question: "HWP 파일 PDF로 변환해줘" }
    end

    rect rgb(227, 250, 252)
        note over MCP,LLM: 🤖 REST — LLM Agent Tool 선택
        MCP->>LLM: [REST] 질문 + Tool 목록 전달
        LLM-->>MCP: Tool 호출 결정 (convert_hwp_to_pdf)
    end

    rect rgb(255, 243, 191)
        note over MCP,HWP: ⚙️ JSON-RPC 2.0 — Tool 실행
        MCP->>HWP: [JSON-RPC] POST /mcp<br/>method: tools/call<br/>params.arguments = { fileUrl, credit: {...} }
        HWP-->>MCP: { result: { content: [...] } }
    end

    rect rgb(227, 250, 252)
        MCP->>LLM: [REST] Tool 결과 전달
        LLM-->>MCP: 최종 답변 생성
    end

    rect rgb(235, 251, 238)
        MCP-->>WEB: [REST] { sessionId, answer, creditUsed, trace }
    end
```

---

## Flow 3: PO Web 직접 실행 → credit 주입 → MCP/WEBAPP 서버 차감

```mermaid
sequenceDiagram
    autonumber
    participant USER   as 사용자 (PO Web)
    participant OAUTH  as OAuth 서버
    participant MCP    as MCP 관리 서버
    participant OSS    as OSS (tbAIServiceInfo)
    participant SRV    as MCP/WEBAPP 서버
    participant CREDIT as 크레딧 서버

    rect rgb(235, 251, 238)
        note over USER,OAUTH: 🔑 REST — 인증
        USER->>OAUTH: [REST] 인증 요청
        OAUTH-->>USER: Bearer token 발급
    end

    rect rgb(227, 250, 252)
        note over USER,MCP: 📋 REST — serviceType 목록 조회
        USER->>MCP: [REST] GET /api/oss/service-types
        MCP-->>USER: [{ type, deductCredit, serviceDesc }]
        note over USER: serviceType 선택 후 실행 요청
        USER->>MCP: [REST] POST /api/web/execute<br/>{ appId, toolName, arguments, serviceType }<br/>Authorization: Bearer {token}
    end

    rect rgb(243, 240, 255)
        note over MCP,OSS: 💳 내부 조회 — credit 주입
        MCP->>OSS: findActiveByType(serviceType)
        OSS-->>MCP: { serviceType, deductCredit }
        note over MCP: arguments.credit = { serviceType, deductCredit } 자동 주입
    end

    alt type = MCP
        rect rgb(255, 243, 191)
            note over MCP,SRV: ⚙️ JSON-RPC 2.0 — Tool 실행
            MCP->>SRV: [JSON-RPC] POST /mcp<br/>method: tools/call<br/>Authorization: Bearer {token}<br/>params.arguments.credit = { serviceType, deductCredit }
            note over SRV: 비즈니스 로직 실행 (성공)
            SRV->>CREDIT: [REST] POST /credits/deduct<br/>Authorization: Bearer {token}<br/>{ serviceType, deductCredit }
            CREDIT-->>SRV: { ok }
            SRV-->>MCP: { result: { content: [...] } }
        end
    else type = WEBAPP
        rect rgb(227, 250, 252)
            note over MCP,SRV: ⚙️ REST — Tool 실행
            MCP->>SRV: [REST] POST /tools/call<br/>Authorization: Bearer {token}<br/>arguments.credit = { serviceType, deductCredit }
            note over SRV: 비즈니스 로직 실행 (성공)
            SRV->>CREDIT: [REST] POST /credits/deduct<br/>Authorization: Bearer {token}<br/>{ serviceType, deductCredit }
            CREDIT-->>SRV: { ok }
            SRV-->>MCP: { content: [...] }
        end
    end

    rect rgb(235, 251, 238)
        note over MCP,USER: 📬 REST — 최종 응답
        MCP-->>USER: { appId, toolName, result }
    end
```

> **JSON-RPC 2.0**: 단일 엔드포인트 `POST /mcp`에 `method` 필드로 라우팅  
> **REST**: 엔드포인트 자체가 메서드 역할 (`GET /tools`, `POST /tools/call`)  
> **`arguments.credit`**: MCP 관리 서버가 자동 주입 — MCP/WEBAPP 서버 모두 동일하게 수신
