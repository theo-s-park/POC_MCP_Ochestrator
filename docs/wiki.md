# MCP 오케스트레이터

## 한 줄 요약

> PO 사용자가 자연어로 요청하면, LLM이 적합한 툴을 선택하고, 오케스트레이터가 해당 서비스를 인증/크레딧 처리 후 실행한다.

---

## 무엇을 해결하는가

연구소의 각 서비스(PDF 변환, HWP 변환, AI 이미지 등)를 MCP 표준으로 감싸면, 오케스트레이터 하나에 등록하는 것만으로 PO 사용자에게 제공할 수 있다.

- 연구소는 자기 서비스를 MCP로 씌우기만 하면 된다
- 인증, 크레딧 차감은 오케스트레이터가 처리한다
- 새 서비스가 생기면 배포 파이프라인에서 등록만 하면 된다

---

## 아키텍처

> 이미지 첨부 예정

**전체 흐름**

```
PO 사용자 (자연어)
    ↓
LLM Agent — 툴 자동 선택 (Claude API / OpenAI / Spring AI)
    ↓  POST /mcp  Authorization: Bearer {PAT}
오케스트레이터
    ├─ ① PAT 검증        → PO 인증 서버
    ├─ ② 툴 라우팅       → 해당 MCP 서버로 포워딩
    └─ ③ 크레딧 차감     → 크레딧 서버 (성공 응답 후)
    ↓
연구소 MCP 서버 (실제 서비스 실행)
    ↓
결과 반환 → 사용자
```

**오케스트레이터가 직접 구현하는 비즈니스 로직은 없다.**  
인증, 크레딧, 서비스 실행 모두 외부 서버를 호출하는 것뿐이다.

---

## 서비스 목록

### 1순위 — PolarisOffice Tools

| 툴 ID | 설명 |
|---|---|
| `pdf_to_jpg` | PDF → 이미지 변환 |
| `pdf_compress` | PDF 압축 |
| `pdf_merge` | PDF 병합 |
| `pdf_extract` | PDF 내용 추출 |
| `hwp_to_hwpx` | HWP → HWPX 변환 |
| `hwpx_to_hwp` | HWPX → HWP 변환 |
| `hwpx_to_pdf` | HWPX → PDF 변환 |
| `hwpx_extract` | HWPX 내용 추출 |
| `word_to_pdf` | WORD → PDF 변환 |
| `word_extract` | WORD 내용 추출 |

### 2순위 — Polaris Office Tools+

| 툴 ID | 설명 |
|---|---|
| `ai_upscale` | AI 이미지 업스케일 |
| `ai_bg_remove` | AI 배경 제거 |
| `ai_wordcloud` | AI 워드클라우드 |
| `qr_generate` | QR 코드 생성 |
| `web_to_image` | 웹페이지 → 이미지 |
| `image_to_svg` | 이미지 → SVG 변환 |

---

## 각 팀의 역할

### 오케스트레이터 (Theo)

- MCP 서버 등록 / 삭제 / 헬스체크 관리
- toolName → 서버 라우팅
- PAT 검증 필터 (인증 서버 API 호출)
- 크레딧 차감 처리 (크레딧 서버 API 호출)
- DB 영속화 (서버 레지스트리, 툴 매핑)

### 인증 / 크레딧 서버 (Woodie)

| API | 역할 |
|---|---|
| `GET /auth/validate` | PAT 유효성 검증, userId 반환 |
| `POST /credits/deduct` | userId + toolId 기반 크레딧 차감 |
| `POST /credits/tools/register` | 새 toolId 단가 등록 |

### 연구소 MCP 서버

각 서비스에 MCP 어댑터만 추가하면 된다. 기존 REST API는 건드리지 않는다.

| 항목 | 규격 |
|---|---|
| 엔드포인트 | `POST /mcp` (JSON-RPC 2.0) |
| 헬스체크 | `GET /health` → `{"status":"ok"}` |
| 프로토콜 버전 | `2024-11-05` |
| 전송 방식 | Streamable HTTP |

---

## 새 MCP 서버 추가 절차

배포 파이프라인 성공 시 두 곳에 자동 등록.

```
빌드 + 배포 성공
    ↓
POST /servers/register  →  오케스트레이터
    (tools/list 자동 수집, DB 저장)
    ↓
POST /credits/tools/register  →  크레딧 서버
    (toolId별 단가 등록)
```

연구소 입장에서는 배포 워크플로우에 두 줄 추가가 전부다.

---

## 미결 사항

| 항목 | 담당 | 상태 |
|---|---|---|
| PAT 포맷 및 검증 API 스펙 | Woodie | 협의 필요 |
| 크레딧 차감 API 스펙 | Woodie | 협의 필요 |
| toolId별 단가 등록 방식 | Woodie | 협의 필요 |
| 크레딧 부족 시 동작 (요청 차단 여부) | 전체 | 협의 필요 |

---

## 현재 구현 상태

| 항목 | 상태 |
|---|---|
| MCP 프로토콜 (JSON-RPC 2.0, Streamable HTTP) | ✅ |
| 서버 등록 / 삭제 / 헬스체크 | ✅ |
| toolName 기반 라우팅 | ✅ |
| tools/list 자동 수집 | ✅ |
| 외부 MCP 연동 검증 (Playwright MCP) | ✅ |
| stdio → HTTP 브릿지 | ✅ |
| DB 영속화 | 구현 예정 |
| PAT 검증 연동 | API 스펙 확정 후 |
| 크레딧 차감 연동 | API 스펙 확정 후 |
| LLM 자동 툴 선택 | 구현 예정 |
