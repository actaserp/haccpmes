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
        with jr_day as (
                    select
                        jr."ProductionDate",
                        jr."WorkCenter_id",
                        sum(coalesce(jr."GoodQty", 0) + coalesce(jr."DefectQty", 0)) as total_prod_qty
                    from job_res jr
                    where jr."ProductionDate" between cast(:date_from as date) and cast(:date_to as date)
                      and jr."State" = 'finished'
                    group by jr."ProductionDate", jr."WorkCenter_id"
                )
                select
                    dt.id as defect_pk,
                    dt."Name" as defect_type,
                    jr."ProductionDate",
                    sum(jr."GoodQty")::decimal as good_qty,
                    sum(jrd."DefectQty")::decimal as defect_qty,
                    jr."WorkCenter_id",
                    jd.total_prod_qty
                from job_res jr
                    inner join job_res_defect jrd on jrd."JobResponse_id" = jr.id
                    inner join defect_type dt on dt.id = jrd."DefectType_id"
                    inner join jr_day jd
                        on jd."ProductionDate" = jr."ProductionDate"
                       and jd."WorkCenter_id"  = jr."WorkCenter_id"
                where jr."ProductionDate" between cast(:date_from as date) and cast(:date_to as date)
                    and jr."WorkCenter_id" = :work_center_id
                  and jr."State" = 'finished'
                group by dt.id, dt."Name", jr."ProductionDate", jr."WorkCenter_id", jd.total_prod_qty
        """;
  return  sqlRunner.getRows(sql, dicParam);
  }
}
