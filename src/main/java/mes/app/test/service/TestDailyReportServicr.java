package mes.app.test.service;

import lombok.extern.slf4j.Slf4j;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TestDailyReportServicr {

  @Autowired
  SqlRunner sqlRunner;

  public List<Map<String, Object>> getList(Integer workCenterId, String start ,String end ) {
    MapSqlParameterSource dicParam = new MapSqlParameterSource();
    dicParam.addValue("work_center_id", workCenterId);
    dicParam.addValue("date_from", start);
    dicParam.addValue("date_to", end);
    String sql = """
        WITH base AS (
           SELECT
             jr."WorkOrderNumber",
             jr."ProductionDate",
             jr."WorkCenter_id",
             MAX(jr."GoodQty") AS good_qty,
             SUM(COALESCE(jr."GoodQty", 0) + COALESCE(jr."DefectQty", 0)) AS total_prod_qty,
             jr."id" AS job_res_id
           FROM job_res jr
           WHERE jr."ProductionDate" BETWEEN CAST(:date_from AS date) AND CAST(:date_to AS date)
             AND jr."State" = 'finished'
           GROUP BY jr."WorkOrderNumber", jr."ProductionDate", jr."WorkCenter_id", jr."id"
         )
         SELECT
           base."WorkOrderNumber",
           dt.id AS defect_pk,
           dt."Name" AS defect_type,
           base."ProductionDate",
           base.good_qty,
           SUM(jrd."DefectQty")::decimal AS defect_qty,
           base."WorkCenter_id",
           base.total_prod_qty
         FROM base
         JOIN job_res_defect jrd ON jrd."JobResponse_id" = base.job_res_id
         JOIN defect_type dt ON dt.id = jrd."DefectType_id"
         WHERE base."WorkCenter_id" = :work_center_id
         GROUP BY
           base."WorkOrderNumber",
           dt.id,
           dt."Name",
           base."ProductionDate",
           base."WorkCenter_id",
           base.good_qty,
           base.total_prod_qty
         order by base."ProductionDate"
        """;

//    log.info("검사일보 read SQL: {}", sql);
//    log.info("SQL Parameters: {}", dicParam.getValues());
  return  sqlRunner.getRows(sql, dicParam);
  }

  public Map<String, Object> getDetail(Integer work_center_id, String search_date, Integer defect_pk) {
    MapSqlParameterSource dicParam = new MapSqlParameterSource();
    dicParam.addValue("work_center_id", work_center_id);
    dicParam.addValue("search_date", search_date);
    dicParam.addValue("defect_pk", defect_pk);

    String sql= """
        select
        jr."WorkOrderNumber",
        jr."ProductionDate" ,
        dt.id AS defect_pk,
        dt."Name" AS defect_type,
         SUM(COALESCE(jr."GoodQty", 0) + COALESCE(jr."DefectQty", 0)) AS total_prod_qty,
        jrd."DefectQty" AS defect_qty,
         jr."WorkCenter_id"
        FROM job_res jr
        JOIN job_res_defect jrd ON jrd."JobResponse_id" = jr.id
        JOIN defect_type dt ON dt.id = jrd."DefectType_id"
        where jrd."DefectType_id" = :defect_pk
        and jr."ProductionDate" = cast(:search_date as date)
        and jr."WorkCenter_id" =:work_center_id
        GROUP BY
        jr."WorkCenter_id", jr."WorkOrderNumber",
        jr."ProductionDate", jrd."DefectQty",
        dt.id,  dt."Name"
        """;

//    log.info("검사일보 detail SQL: {}", sql);
//    log.info("SQL Parameters: {}", dicParam.getValues());
    return sqlRunner.getRow(sql, dicParam);
  }
}
