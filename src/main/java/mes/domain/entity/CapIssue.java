package mes.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cap_issue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapIssue extends AbstractAuditModel{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"CapNo\"", length = 50)
    private String capNo; // CAP 번호

    @Column(name = "\"OccurDate\"")
    private String occurDate; // 발생일시

    @Column(name = "\"OccurDepart\"")
    private Integer occurDepart; // 발생부서 ID

    @Column(name = "\"MaterialId\"")
    private Integer materialId; // 품목 ID

    @Column(name = "\"UnitId\"")
    private Integer unitId; // 측정단위 ID

    @Column(length = 30)
    private String status; // 진행상태 (예: 접수, 조치중, 완료)

    @Column(name = "\"ProcessId\"")
    private Integer processId; // 발생공정 ID

    @Column(name = "\"ClaimType\"", length = 50)
    private String claimType; // 클레임 유형

    @Column(length = 50)
    private String writer; // 작성자

    @Column(name = "issue_summary", columnDefinition = "TEXT")
    private String issueSummary; // 문제 요약 설명

    @Column(length = 50)
    private String spjangcd; // 사업장 코드

    @Column
    private Integer company_id; //거래처 아이디

    @Column
    private String process_date; //완료 날짜

    @Column
    private Integer defect_type_id;  //부적합 아이디
}
