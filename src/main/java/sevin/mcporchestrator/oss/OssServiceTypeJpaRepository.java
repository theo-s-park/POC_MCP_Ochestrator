package sevin.mcporchestrator.oss;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OssServiceTypeJpaRepository extends JpaRepository<OssServiceTypeEntity, String> {
    List<OssServiceTypeEntity> findByStatus(String status);
}
