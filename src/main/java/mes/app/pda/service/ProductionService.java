package mes.app.pda.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class ProductionService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getProductionList(String dateFrom, String dateTo, String matType, String spjangcd) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("dateFrom", dateFrom);
        param.addValue("dateTo", dateTo);
        param.addValue("matType", matType);
        param.addValue("spjangcd", spjangcd);

        String sql = """
                SELECT 
                    jr.id
                    , jr."WorkOrderNumber" as order_num
                    , to_char(jr."ProductionDate", 'yyyy-mm-dd') as prod_date
                    , fn_code_name('job_state', jr."State") as job_state
                    , jr."State" as state
                    , m."Code" as mat_code
                    , m."Name" as mat_name
                    , fn_code_name('mat_type', mg."MaterialType") as mat_type
                FROM job_res jr
                LEFT JOIN material m ON m.id = jr."Material_id"
                LEFT JOIN mat_grp mg ON mg.id = m."MaterialGroup_id"
                WHERE jr."ProductionDate" BETWEEN cast(:dateFrom as date) AND cast(:dateTo as date)
                AND jr."Routing_id" IS NULL
                AND jr.spjangcd = :spjangcd
                """;

        if (StringUtils.hasText(matType)) {
            sql += " AND mg.\"MaterialType\" = :matType ";
        }

        sql += " ORDER BY jr.\"ProductionDate\" DESC, jr.\"WorkOrderNumber\", jr.id ";

        return sqlRunner.getRows(sql, param);
    }


    public Map<String, Object> getProductionDetail(Integer jrPk) {
        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("jrPk", jrPk);

        String sql = """
                SELECT jr.id
                         , jr."WorkOrderNumber" as order_num
                         , jr."LotNumber" as lot_num
                         , fn_code_name('job_state', jr."State") as job_state
                         , m."Code" as mat_code, m."Name" as mat_name
                         , m.id as material_id
                         , u."Name" as unit
                         , jr."OrderQty" as order_qty
                         , coalesce(jr."GoodQty", 0) as good_qty
                         , coalesce(jr."DefectQty", 0) as defect_qty
                         , to_char(jr."ProductionDate", 'yyyy-mm-dd') as prod_date
                         , to_char(jr."StartTime", 'hh24:mi') as start_time
                         , jr."EndDate" as end_date
                         , to_char(jr."EndTime", 'hh24:mi') as end_time
                         , sh."Name" as shift_name
                         , wc.id as workcenter_id   -- ★ 이 부분이 데이터에 안 넘어오고 있습니다! 다시 확인!
                         , wc."Name" as workcenter_name
                         , e.id as equipment_id     -- ★ 설비 ID도 수정을 위해 필요합니다.
                         , e."Name" as equipment_name
                         , jr."Description" as description\s
                      FROM job_res jr
              LEFT JOIN material m ON m.id = jr."Material_id"
              LEFT JOIN unit u ON u.id = m."Unit_id"
              LEFT JOIN work_center wc ON wc.id = jr."WorkCenter_id"
              LEFT JOIN equ e ON e.id = jr."Equipment_id"
              LEFT JOIN shift sh ON sh."Code" = jr."ShiftCode"
             WHERE jr.id = :jrPk
            """;
        return sqlRunner.getRow(sql, param);
    }
}