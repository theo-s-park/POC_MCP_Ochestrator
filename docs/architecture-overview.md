# MCP 오케스트레이터 아키텍처 개요

> 작성일: 2026-05-14  
> 작성자: Theo

---

## 한 줄 정의

> **오케스트레이터는 검증하고, 차감하고, 포워딩한다.**

오케스트레이터가 직접 구현하는 비즈니스 로직은 없다.  
인증은 PO 인증 서버, 크레딧은 크레딧 서버, 실제 서비스는 MCP 서버가 담당한다.  
오케스트레이터는 세 곳을 순서대로 호출하는 라우터다.

---

## 전체 아키텍처

```
[PO 사용자 / LLM / 에이전트]
         │
         │  POST /mcp
         │  Authorization: Bearer {PAT}
         │  {"jsonrpc":"2.0","method":"tools/call","params":{"name":"hwpx_to_pdf",...}}
         ▼
┌─────────────────────────────────────────────────────┐
│                  MCP 오케스트레이터                   │
│                                                     │
│  ① PAT 검증 필터                                    │
│     → POST /auth/validate {PAT}                     │
│     ← 200 OK {userId} / 401 Unauthorized            │
│                                                     │
│  ② 크레딧 차감 (tools/call 성공 후)                  │
│     → POST /credits/deduct {userId, toolId}         │
│     ← 200 OK / 402 Payment Required                 │
│                                                     │
│  ③ 툴 라우터                                        │
│     toolName → 해당 MCP 서버로 포워딩               │
│     (body 변환 없음, 그대로 전달)                    │
│                                                     │
│  ④ MCP 서버 레지스트리                              │
│     - 서버 등록 / 삭제                              │
│     - 헬스체크 폴링 (60초, 3회 실패 → INACTIVE)     │
│     - tools/list 자동 수집                          │
└──────────────────────┬──────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   [문서 변환 MCP]  [HWP MCP]   [AI 이미지 MCP]
   pdf_to_jpg      hwpx_to_pdf  ai_upscale
   pdf_compress    hwp_to_hwpx  ai_bg_remove
   pdf_merge       hwpx_extract ai_wordcloud
   ...             ...          ...
```

---

## 오케스트레이터가 하는 일 (전체 흐름)

```
1. 요청 진입
   Authorization: Bearer {PAT}

2. PAT 검증  [PO 인증 서버 호출]
   → GET /auth/validate
   ← 200 {userId} 또는 401 차단

3. tools/call 라우팅
   toolName 보고 레지스트리에서 서버 URL 조회
   해당 MCP 서버로 body 그대로 포워딩

4. 크레딧 차감  [크레딧 서버 호출]
   MCP 서버 성공 응답 받은 후
   → POST /credits/deduct {userId, toolId}
   ← 200 OK (차감 완료)

5. 응답 반환
   에이전트에게 MCP 서버 결과 그대로 반환
```

---

## 오케스트레이터가 하지 않는 것

| 항목 | 담당 |
|---|---|
| 인증 로직 구현 | PO 인증 서버 (Woodie) |
| 크레딧 단가 결정 | 크레딧 서버 (Woodie) |
| 크레딧 잔액 계산 | 크레딧 서버 (Woodie) |
| 실제 서비스 실행 | 연구소 MCP 서버 |

---

## 연구소 MCP 서버 통신 규약

연구소가 기존 서비스에 MCP를 씌울 때 지켜야 할 최소 요건.  
기존 REST API 건드릴 필요 없음. MCP 어댑터만 추가.

| 항목 | 규격 |
|---|---|
| 엔드포인트 | `POST /mcp` (JSON-RPC 2.0) |
| 헬스체크 | `GET /health` → `{"status":"ok"}` |
| 프로토콜 버전 | `2024-11-05` |
| 전송 방식 | Streamable HTTP |

---

## 서비스 목록 (1, 2순위)

### 1순위 - PolarisOffice Tools

| 툴 ID | 설명 |
|---|---|
| pdf_to_jpg | PDF → 이미지 변환 |
| pdf_compress | PDF 압축 |
| pdf_merge | PDF 병합 |
| pdf_extract | PDF 내용 추출 |
| hwp_to_hwpx | HWP → HWPX 변환 |
| hwpx_to_hwp | HWPX → HWP 변환 |
| hwpx_to_pdf | HWPX → PDF 변환 |
| hwpx_extract | HWPX 내용 추출 |
| word_to_pdf | WORD → PDF 변환 |
| word_extract | WORD 내용 추출 |

### 2순위 - Polaris Office Tools+

| 툴 ID | 설명 |
|---|---|
| ai_upscale | AI 이미지 업스케일 |
| ai_bg_remove | AI 배경 제거 |
| ai_wordcloud | AI 워드클라우드 |
| qr_generate | QR 코드 생성 |
| web_to_image | 웹페이지 → 이미지 |
| image_to_svg | 이미지 → SVG 변환 |

---

## 새 MCP 서버 배포 워크플로우

새 MCP 서버가 생길 때마다 배포 파이프라인에서 자동 처리.

```
[GitHub Actions / CI]
    빌드 + 배포 성공
         ↓
    POST /servers/register          ← 오케스트레이터 등록
    {
      "name": "hwp-mcp",
      "url": "https://hwp-mcp.internal"
    }
         ↓
    오케스트레이터 → tools/list 자동 수집
    → toolName 라우팅 맵 자동 갱신
         ↓
    POST /credits/tools/register    ← 크레딧 서버 등록
    {toolId별 단가 정보}            (Woodie 영역)
```

연구소 입장에서는 배포 워크플로우에 두 줄 추가가 전부.

---

## 현재 구현 상태

| 항목 | 상태 | 비고 |
|---|---|---|
| MCP 프로토콜 (JSON-RPC 2.0) | ✅ 완료 | |
| 툴 라우팅 (toolName → 서버) | ✅ 완료 | |
| 서버 등록 / 삭제 API | ✅ 완료 | |
| 헬스체크 폴링 | ✅ 완료 | 60초, 3회 실패 → INACTIVE |
| tools/list 자동 수집 | ✅ 완료 | |
| 외부 MCP 연동 | ✅ 완료 | Playwright MCP 검증 |
| stdio → HTTP 브릿지 | ✅ 완료 | Node.js 60줄 |
| 인증 필터 메커니즘 | ✅ 완료 | PAT 스펙 확정 시 교체 |
| 관리 UI | ✅ 완료 | 서버 관리, 툴 탐색, Agent Demo |
| **DB 영속화** | ❌ 미구현 | H2 파일 모드 예정 |
| **크레딧 차감 연동** | ❌ 미구현 | 크레딧 API 스펙 확정 후 |
| **PAT 검증 연동** | ❌ 미구현 | PAT 포맷 확정 후 |
| **LLM 자동 툴 선택** | ❌ 미구현 | 자연어 → 툴 선택 |

---

## 앞으로 구현할 것 (Theo 영역)

### Phase 4 - DB 영속화
- `ConcurrentHashMap` → JPA Entity
- H2 파일 모드 (→ 추후 RDS 교체)
- 재시작해도 등록된 서버 유지

### Phase 5 - 인증 + 크레딧 연동
- PAT 검증 필터 (Woodie API 연동)
- tools/call 성공 후 크레딧 차감 (Woodie API 연동)
- 크레딧 부족 시 402 응답 처리

### Phase 6 - LLM 연동
- 자연어 → LLM → 툴 선택 → 실행
- 멀티 MCP 서버 툴 조합 시나리오

---

## 미결 사항 (Woodie 협의 필요)

| 항목 | 상태 |
|---|---|
| PAT 포맷 및 검증 API 스펙 | 협의 필요 |
| 크레딧 차감 API 스펙 | 협의 필요 |
| 크레딧 서버의 toolId 등록 방식 | 협의 필요 |
| 크레딧 부족 시 동작 (차단 여부) | 협의 필요 |
