package mes.app.support.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CapService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getList(String capNo, String spjangcd){


        MapSqlParameterSource paramMap = new MapSqlParameterSource();

        if(capNo == null) capNo = "";

        paramMap.addValue("capNo", "%" + capNo + "%");
        paramMap.addValue("spjangcd", spjangcd);

        String sql = """
                select
                                  ci.id as id
                                 ,ci."CapNo" as cap_no
                                 ,ci."OccurDate" as occur_date
                                 ,ci."OccurDepart" as occur_depart
                                 ,d."Name" as depart_name
                                 ,ci."MaterialId" as material_id
                                 ,m."Name" as material_name
                                 ,ci."UnitId" as unit_id
                                 ,u."Name" as unit_name
                                 ,ci."ProcessId" as process_id
                                 ,p."Name" as process_name
                                 ,ci."ClaimType" as claim_type
                                 ,ci.issue_summary as issue_summary
                                 ,ci.writer as writer
                                 ,CASE
                                                         WHEN ci.status = 'receipt'  THEN '접수'
                                                         WHEN ci.status = 'progress' THEN '진행'
                                                         WHEN ci.status = 'finish'   THEN '완료'
                                                         ELSE ci.status
                                                     END AS status
                                 ,ci.company_id as company_id
                                 ,c."Name" as company_name
                                 ,ci.defect_type_id as defect_type_id
                                 ,df."Name" as defect_type_name
                                 ,ci.process_date as process_date
                                 from cap_issue ci
                                 left join material m on m.id = ci."MaterialId"
                                 left join depart d on d.id = ci."OccurDepart"
                                 left join unit u on u.id = ci."UnitId"
                                 left join process p on p.id = ci."ProcessId"
                                 left join company c on c.id = ci.company_id
                                 left join defect_type df on df.id = ci.defect_type_id
                                 where ci."CapNo" like :capNo
                                 and ci.spjangcd = :spjangcd
                """;
        List<Map<String,Object>> items = this.sqlRunner.getRows(sql, paramMap);
        return items;
    }
}
