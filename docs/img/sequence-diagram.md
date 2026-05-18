# MCP 관리 서버 시퀀스 다이어그램

---

## Flow 1: MCP 서버 등록 → Backoffice 설정 → WEB 공개

```mermaid
sequenceDiagram
    autonumber
    participant CICD as MCP 서버 CI/CD
    participant HWP  as hwp-converter (MCP 서버 예시)
    participant MCP  as MCP 관리 서버
    participant BO   as Backoffice 관리자
    participant WEB  as WEB 사용자

    rect rgb(235, 251, 238)
        note over CICD,MCP: 📦 서버 등록 요청 (HTTP)
        CICD->>MCP: POST /api/mcp/servers/register<br/>(name, url, description, version)
    end

    rect rgb(255, 243, 191)
        note over MCP,HWP: 🔌 MCP 표준 통신 (JSON-RPC 2.0) — 자동 수집
        MCP->>HWP: tools/list
        HWP-->>MCP: [{ name, description, inputSchema }]
        MCP->>HWP: resources/list
        HWP-->>MCP: [{ uri, name, mimeType }]
        note over MCP: McpApp 자동 생성<br/>(credit=0, isVisible=false)
    end

    rect rgb(235, 251, 238)
        note over CICD,MCP: 📦 등록 완료 응답 (HTTP)
        MCP-->>CICD: { serverId, status: "ACTIVE" }
    end

    rect rgb(243, 240, 255)
        note over MCP,BO: ⚙️ Backoffice 앱 메타 설정
        BO->>MCP: GET /api/mcp/apps
        MCP-->>BO: 앱 목록 반환 (credit=0, isVisible=false)
        BO->>MCP: PATCH /api/mcp/apps/{id}<br/>(credit=5, isVisible=true, displayName="HWP 변환기")
        note over MCP: McpApp 업데이트 저장
        MCP-->>BO: { status: "ok" }
    end

    rect rgb(241, 243, 245)
        note over MCP,WEB: 🌐 WEB 공개 앱 노출
        WEB->>MCP: GET /api/mcp/apps/public
        MCP-->>WEB: 공개 앱 목록 (isVisible=true인 앱만, credit=5 포함)
    end
```

> **범례**: 실선(`->>`) = 요청/호출 · 점선(`-->>`) = 응답/반환

---

## Flow 2: 사용자 Tool 실행 → 크레딧 차감

```mermaid
sequenceDiagram
    autonumber
    participant WEB as WEB 사용자
    participant MCP as MCP 관리 서버
    participant WAS as WAS (User/Credit)
    participant LLM as LLM Agent (Claude)
    participant HWP as hwp-converter

    WEB->>MCP: POST /agent/chat<br/>{ question: "이 HWP 파일 PDF로 변환해줘" }

    rect rgb(255, 227, 227)
        note over MCP,WAS: 💳 사전 토큰 잔액 확인
        MCP->>WAS: 유저 토큰 잔액 확인 요청
        WAS-->>MCP: 잔액 반환 (예: 100 토큰)
        note over MCP: 잔액 >= credit(5) 확인<br/>부족 시 → 403 응답
    end

    rect rgb(227, 250, 252)
        note over MCP,LLM: 🤖 LLM Agent Tool 선택
        MCP->>LLM: 질문 + 등록된 Tool 목록 전달
        note over LLM: Tool 자동 선택<br/>(convert_hwp_to_pdf)
        LLM->>MCP: Tool 호출 요청 (toolName, args)
    end

    rect rgb(255, 243, 191)
        note over MCP,HWP: ⚙️ MCP 표준 Tool 실행
        MCP->>HWP: POST /mcp (JSON-RPC 2.0, tools/call)
        note over HWP: HWP → PDF 변환 처리
        HWP-->>MCP: { content: [{ type: "text", text: "PDF URL..." }] }<br/>← MCP 표준 응답 (변경 불가)
        MCP->>LLM: Tool 결과 전달
        LLM->>MCP: 최종 답변 생성
    end

    rect rgb(255, 227, 227)
        note over MCP,WAS: 💳 크레딧 차감
        MCP->>WAS: 크레딧 차감 요청 (userId, creditUsed=5)
        note over WAS: 토큰 차감 (100 → 95)
        WAS-->>MCP: 차감 완료
    end

    MCP-->>WEB: { sessionId, answer, creditUsed: 5, trace: [...] }<br/>← Wrapper 응답 (MCP 관리 서버 추가)
```

> **범례**: 실선(`->>`) = 요청/호출 · 점선(`-->>`) = 응답/반환  
> `← MCP 표준 응답` = MCP JSON-RPC 2.0 원본 구조 (변경 불가)  
> `← Wrapper 응답` = MCP 관리 서버가 감싸는 상위 객체

---

## Flow 3: OAuth 임시 토큰 교환 → MCP 서버 실행

```mermaid
sequenceDiagram
    autonumber
    participant USER as 사용자 (Client)
    participant OAUTH as OAuth 서버
    participant MCP as MCP 관리 서버
    participant SRV as MCP 서버
    participant CREDIT as 크레딧 서버

    rect rgb(255, 243, 191)
        note over USER,OAUTH: 🔑 임시 토큰 발급
        USER->>OAUTH: 인증 요청 (clientId, scope)
        OAUTH-->>USER: 임시 토큰 발급 (tempToken, expires_in)
    end

    rect rgb(235, 251, 238)
        note over USER,MCP: 📨 관리 서버 요청
        USER->>MCP: POST /agent/chat<br/>{ question, tempToken }
    end

    rect rgb(227, 250, 252)
        note over MCP,OAUTH: 🔄 Access Token 교환
        MCP->>OAUTH: POST /token/exchange { tempToken }
        note over OAUTH: 임시 토큰 검증
        OAUTH-->>MCP: { accessToken, expiresIn }
    end

    rect rgb(255, 227, 227)
        note over MCP,SRV: ⚙️ MCP 서버 Tool 실행
        MCP->>SRV: POST /mcp (JSON-RPC 2.0)<br/>Authorization: Bearer {accessToken}
        note over SRV: Tool 실행
        SRV-->>MCP: { content: [...] }
    end

    rect rgb(243, 240, 255)
        note over SRV,CREDIT: 💳 크레딧 차감 (정책 미정 — MCP 서버 직접 호출 유력)
        SRV->>CREDIT: POST /credits/deduct { userId, toolId }
        CREDIT-->>SRV: { ok }
    end

    rect rgb(235, 251, 238)
        note over MCP,USER: 📬 최종 응답 반환
        MCP-->>USER: { answer, creditUsed, trace }
    end
```

> **크레딧 차감 정책**: 현재 미정. MCP 서버가 크레딧 서버를 직접 호출하는 방식이 유력.

