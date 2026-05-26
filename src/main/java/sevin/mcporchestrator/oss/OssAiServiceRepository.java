package sevin.mcporchestrator.oss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class OssAiServiceRepository {

    private static final Logger log = LoggerFactory.getLogger(OssAiServiceRepository.class);
    private static final long CACHE_TTL_SECONDS = 3600;

    private static final List<OssAiServiceInfo> STUB = List.of(
            new OssAiServiceInfo("GPT3",         "ON", 2,  "AI WRITE_GPT-3.5",    null),
            new OssAiServiceInfo("WRITE_GPT4",   "ON", 5,  "NOVA_AI CHAT_GPT-4",  null),
            new OssAiServiceInfo("WRITE_CLADE3", "ON", 5,  "AI WRITE_CLAUDE-3.5", null)
    );

    private final OssApiClient apiClient;
    private final OssServiceTypeJpaRepository jpaRepo;
    private final AtomicReference<List<OssAiServiceInfo>> apiCache = new AtomicReference<>();
    private volatile Instant cacheExpiry = Instant.EPOCH;

    public OssAiServiceRepository(OssApiClient apiClient, OssServiceTypeJpaRepository jpaRepo) {
        this.apiClient = apiClient;
        this.jpaRepo = jpaRepo;
    }

    public List<OssAiServiceInfo> findAllActive() {
        // 1. H2 DB에 데이터 있으면 우선 사용
        List<OssServiceTypeEntity> dbRows = jpaRepo.findByStatus("ON");
        if (!dbRows.isEmpty()) {
            return dbRows.stream().map(OssServiceTypeEntity::toInfo).toList();
        }

        // 2. API 캐시 유효하면 사용
        List<OssAiServiceInfo> cached = apiCache.get();
        if (cached != null && Instant.now().isBefore(cacheExpiry)) {
            return cached;
        }

        // 3. API 호출
        if (apiClient.isConfigured()) {
            List<OssAiServiceInfo> result = apiClient.fetchAllActive();
            if (result != null) {
                apiCache.set(result);
                cacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
                log.info("[OSS] API cache refreshed, {} services", result.size());
                return result;
            }
            if (cached != null) {
                log.warn("[OSS] API failed — using stale cache");
                return cached;
            }
        }

        log.warn("[OSS] no DB, no API — stub fallback");
        return STUB;
    }

    public Optional<OssAiServiceInfo> findActiveByServiceType(String serviceType) {
        return findAllActive().stream()
                .filter(s -> serviceType.equals(s.serviceType()))
                .findFirst();
    }

    /** OSS API 데이터를 H2 DB에 저장 (기존 데이터 전체 교체) */
    public int importFromApi() {
        if (!apiClient.isConfigured()) {
            throw new IllegalStateException("OSS API cookie not configured");
        }
        List<OssAiServiceInfo> all = apiClient.fetchAllActive();
        if (all == null) throw new IllegalStateException("OSS API call failed");

        Instant now = Instant.now();
        List<OssServiceTypeEntity> entities = all.stream()
                .map(info -> new OssServiceTypeEntity(
                        info.serviceType(), info.status(), info.deductCredit(),
                        info.serviceDesc(), info.inputLimit(), now))
                .toList();

        jpaRepo.deleteAll();
        jpaRepo.saveAll(entities);
        log.info("[OSS] imported {} service types to H2", entities.size());
        return entities.size();
    }
}
