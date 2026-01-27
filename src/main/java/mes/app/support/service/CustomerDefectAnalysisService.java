package mes.app.support.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomerDefectAnalysisService {


    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getList(String startDt,
                                             String endDt,
                                             String company_name,
                                             String material_name,
                                             Integer defect_type_id,
                                             String progress,
                                             String spjangcd

    ){

        MapSqlParameterSource dicParam = new MapSqlParameterSource();
        dicParam.addValue("startDt", startDt);
        dicParam.addValue("endDt", endDt);
        dicParam.addValue("company_name", "%" + company_name + "%");
        dicParam.addValue("material_name", "%" + material_name + "%");
        dicParam.addValue("defect_type_id", defect_type_id);
        dicParam.addValue("progress", progress);
        dicParam.addValue("spjangcd", spjangcd);


        String sql = """
                WITH RawData AS (
                     -- 1. 기존 상세 데이터를 가져옵니다 (is_footer = 0)
                     SELECT
                         ci.id as id
                         ,ci."CapNo" as cap_no
                         ,ci."OccurDate" as occur_date
                         ,ci.process_date as process_date
                         ,ci."MaterialId" as material_id
                         ,m."Name" as material_name
                         ,CASE
                             WHEN ci.status = 'receipt'  THEN '접수'
                             WHEN ci.status = 'progress' THEN '진행'
                             WHEN ci.status = 'finish'   THEN '완료'
                             ELSE ci.status
                         END AS status
                         ,CASE
                             WHEN ci.status = 'finish'
                             THEN TO_DATE(ci.process_date, 'YYYY-MM-DD') - TO_DATE(ci."OccurDate", 'YYYY-MM-DD')
                             ELSE NULL
                         END AS process_days
                         ,c."Name" as company_name
                         ,ci.company_id as company_id
                         ,df."Name" as defect_type_name
                         ,0 AS is_footer -- 일반 데이터 행 구분자
                     FROM cap_issue ci
                     LEFT JOIN material m ON m.id = ci."MaterialId"
                     LEFT JOIN company c ON c.id = ci.company_id
                     LEFT JOIN defect_type df ON df.id = ci.defect_type_id
                     -- WHERE 절이 필요하면 여기에 추가
                     WHERE 1=1
                     AND ci.spjangcd = :spjangcd
                     AND ci."OccurDate" >= :startDt
                     AND ci."OccurDate" <= :endDt
                """;

        if(company_name != null && !company_name.isEmpty()){
            sql += """
                    AND c."Name" like :company_name
                    """;
        }

        if(material_name != null && !material_name.isEmpty()){
            sql += """
                    AND m."Name" ILIKE :material_name
                    """;
        }

        if(defect_type_id != null){
            sql += """
                    AND ci.defect_type_id = :defect_type_id
                    """;
        }

        if(progress != null && !progress.isEmpty()){
            sql += """
                    AND ci.status = :progress
                    """;
        }

        sql += """
                 )
                 -- 2. 실제 데이터 행들
                 SELECT * FROM RawData
                
                 UNION ALL
                
                 -- 3. 고객사별 평균 행을 강제로 생성 (is_footer = 1)
                 SELECT\s
                     NULL as id,
                     NULL as cap_no,
                     NULL as occur_date,
                     NULL as process_date,
                     NULL as material_id,
                     'FOOTER_ROW' as material_name, -- 프론트에서 이 값을 보고 문구를 갈아끼웁니다
                     '평균' as status,
                     AVG(process_days) as process_days, -- 여기서 그룹별 평균 계산
                     company_name,
                     company_id,
                     NULL as defect_type_name,
                     1 AS is_footer -- 소계(푸터) 행 구분자
                 FROM RawData
                 GROUP BY company_id, company_name
                
                 -- 4. 정렬이 가장 중요합니다!
                 -- 고객사별로 묶고 -> 데이터(0)가 먼저 나오고 -> 소계(1)가 그 아래에 오도록 함
                 ORDER BY company_name, is_footer ASC, occur_date DESC;
                """;


        List<Map<String,Object>> items = this.sqlRunner.getRows(sql, dicParam);

        return items;
    }
}


