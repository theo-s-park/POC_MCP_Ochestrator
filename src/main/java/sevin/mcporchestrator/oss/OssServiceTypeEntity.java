package sevin.mcporchestrator.oss;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "oss_service_type")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OssServiceTypeEntity {

    @Id
    private String serviceType;
    private String status;
    private int deductCredit;
    private String serviceDesc;
    private Integer inputLimit;
    private Instant importedAt;

    public OssAiServiceInfo toInfo() {
        return new OssAiServiceInfo(serviceType, status, deductCredit, serviceDesc, inputLimit);
    }
}
