package mes.app.test.service;

import mes.domain.entity.User;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InspectionReportService {

    @Autowired
    SqlRunner sqlRunner;

    // 목록 조회
    public List<Map<String, Object>> getList(String start, String end, String spjangcd, Integer workcenterId) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("start",         start);
        param.addValue("end",           end);
        param.addValue("spjangcd",      spjangcd);
        param.addValue("workcenter_id", workcenterId);

        String sql = """
            SELECT
                h.id,
                to_char(h.report_date, 'yyyy-mm-dd') AS report_date,
                h.writer,
                h.workcenter_id,
                w."Name" AS workcenter_name,
                COALESCE(SUM(r.inspection_count), 0) AS total_inspection,
                COALESCE(SUM(r.inspection_wait),  0) AS total_wait,
                COALESCE(SUM(r.delivery_wait),    0) AS total_delivery_wait,
                COALESCE(SUM(r.repair_defect),    0) AS total_defect
            FROM inspection_report_head h
            LEFT JOIN inspection_report_row r ON r.head_id = h.id
            LEFT JOIN work_center w           ON w.id = h.workcenter_id
            WHERE h.spjangcd = :spjangcd
              AND h.report_date BETWEEN :start::date AND :end::date
              AND (:workcenter_id::int IS NULL OR h.workcenter_id = :workcenter_id::int)
            GROUP BY h.id, h.report_date, h.writer, h.workcenter_id, w."Name"
            ORDER BY h.report_date DESC, h.id DESC
        """;

        return sqlRunner.getRows(sql, param);
    }

    // 상세 조회
    public Map<String, Object> getDetail(int id) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("id", id);

        String headSql = """
            SELECT
                h.id,
                to_char(h.report_date, 'yyyy-mm-dd') AS report_date,
                h.writer,
                h.spjangcd,
                h.workcenter_id,
                w."Name" AS workcenter_name
            FROM inspection_report_head h
            LEFT JOIN work_center w ON w.id = h.workcenter_id
            WHERE h.id = :id
        """;

        String rowSql = """
            SELECT
                id,
                head_id,
                row_order,
                gubun,
                production_model,
                pcb_no,
                inspection_count,
                inspection_wait,
                delivery_wait,
                repair_defect,
                remarks
            FROM inspection_report_row
            WHERE head_id = :id
            ORDER BY row_order
        """;

        String noWorkSql = """
            SELECT
                id,
                head_id,
                row_order,
                left_gubun,
                left_stop_time,
                left_man_hour,
                left_no_work_content,
                right_gubun,
                right_stop_time,
                right_man_hour,
                right_no_work_content
            FROM inspection_report_no_work
            WHERE head_id = :id
            ORDER BY row_order
        """;

        Map<String, Object> head         = sqlRunner.getRow(headSql,    param);
        List<Map<String, Object>> rows   = sqlRunner.getRows(rowSql,    param);
        List<Map<String, Object>> noWork = sqlRunner.getRows(noWorkSql, param);

        head.put("rows",   rows);
        head.put("noWork", noWork);

        return head;
    }

    // 저장 (등록 / 수정)
    public void save(Map<String, Object> payload, User user) {

        Integer headId = null;
        Object idObj = payload.get("id");
        if (idObj != null && !idObj.toString().isBlank()) {
            headId = Integer.parseInt(idObj.toString());
        }

        String reportDateStr = (String) payload.get("report_date");
        String writer        = (String) payload.get("writer");
        String spjangcd      = (String) payload.get("spjangcd");
        Integer workcenterId = toIntOrNull(payload.get("workcenter_id"));

        MapSqlParameterSource headParam = new MapSqlParameterSource();
        headParam.addValue("report_date",   reportDateStr);
        headParam.addValue("writer",        writer);
        headParam.addValue("spjangcd",      spjangcd);
        headParam.addValue("user_id",       user.getId());
        headParam.addValue("workcenter_id", workcenterId);

        if (headId == null) {
            String insertHead = """
                INSERT INTO inspection_report_head
                    (report_date, writer, spjangcd, workcenter_id, _creater_id, _modifier_id)
                VALUES
                    (:report_date::date, :writer, :spjangcd, :workcenter_id, :user_id, :user_id)
                RETURNING id
            """;
            headId = sqlRunner.queryForObject(insertHead, headParam, (rs, rn) -> rs.getInt(1));
        } else {
            headParam.addValue("id", headId);
            String updateHead = """
                UPDATE inspection_report_head SET
                    report_date   = :report_date::date,
                    writer        = :writer,
                    workcenter_id = :workcenter_id,
                    _modified     = now(),
                    _modifier_id  = :user_id
                WHERE id = :id
            """;
            sqlRunner.execute(updateHead, headParam);
        }

        // 기존 로우 삭제 후 재삽입
        MapSqlParameterSource delParam = new MapSqlParameterSource();
        delParam.addValue("head_id", headId);
        sqlRunner.execute("DELETE FROM inspection_report_row     WHERE head_id = :head_id", delParam);
        sqlRunner.execute("DELETE FROM inspection_report_no_work WHERE head_id = :head_id", delParam);

        // 검사 행 저장
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                MapSqlParameterSource rp = new MapSqlParameterSource();
                rp.addValue("head_id",           headId);
                rp.addValue("row_order",         i);
                rp.addValue("gubun",             nullStr(row.get("gubun")));
                rp.addValue("production_model",  nullStr(row.get("production_model")));
                rp.addValue("pcb_no",            nullStr(row.get("pcb_no")));
                rp.addValue("inspection_count",  toInt(row.get("inspection_count")));
                rp.addValue("inspection_wait",   toInt(row.get("inspection_wait")));
                rp.addValue("delivery_wait",     toInt(row.get("delivery_wait")));
                rp.addValue("repair_defect",     toInt(row.get("repair_defect")));
                rp.addValue("remarks",           nullStr(row.get("remarks")));

                String insertRow = """
                    INSERT INTO inspection_report_row
                        (head_id, row_order, gubun, production_model, pcb_no,
                         inspection_count, inspection_wait, delivery_wait, repair_defect, remarks)
                    VALUES
                        (:head_id, :row_order, :gubun, :production_model, :pcb_no,
                         :inspection_count, :inspection_wait, :delivery_wait, :repair_defect, :remarks)
                """;
                sqlRunner.execute(insertRow, rp);
            }
        }

        // 무작업 내용 저장 (좌/우 2열 구조)
        List<Map<String, Object>> noWork = (List<Map<String, Object>>) payload.get("noWork");
        if (noWork != null) {
            for (int i = 0; i < noWork.size(); i++) {
                Map<String, Object> nw = noWork.get(i);
                MapSqlParameterSource np = new MapSqlParameterSource();
                np.addValue("head_id",                headId);
                np.addValue("row_order",              i);
                np.addValue("left_gubun",             nullStr(nw.get("left_gubun")));
                np.addValue("left_stop_time",         nullStr(nw.get("left_stop_time")));
                np.addValue("left_man_hour",          toDouble(nw.get("left_man_hour")));
                np.addValue("left_no_work_content",   nullStr(nw.get("left_no_work_content")));
                np.addValue("right_gubun",            nullStr(nw.get("right_gubun")));
                np.addValue("right_stop_time",        nullStr(nw.get("right_stop_time")));
                np.addValue("right_man_hour",         toDouble(nw.get("right_man_hour")));
                np.addValue("right_no_work_content",  nullStr(nw.get("right_no_work_content")));

                String insertNoWork = """
                    INSERT INTO inspection_report_no_work
                        (head_id, row_order,
                         left_gubun, left_stop_time, left_man_hour, left_no_work_content,
                         right_gubun, right_stop_time, right_man_hour, right_no_work_content)
                    VALUES
                        (:head_id, :row_order,
                         :left_gubun, :left_stop_time, :left_man_hour, :left_no_work_content,
                         :right_gubun, :right_stop_time, :right_man_hour, :right_no_work_content)
                """;
                sqlRunner.execute(insertNoWork, np);
            }
        }
    }

    // 삭제
    public void delete(int id) {
        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("id", id);
        sqlRunner.execute("DELETE FROM inspection_report_head WHERE id = :id", param);
    }

    // 헬퍼
    private String nullStr(Object v) {
        return v == null ? null : v.toString();
    }
    private int toInt(Object v) {
        if (v == null || v.toString().isBlank()) return 0;
        try { return Integer.parseInt(v.toString().replace(",", "")); }
        catch (Exception e) { return 0; }
    }
    /** 미선택(빈 값)은 null 로 저장 */
    private Integer toIntOrNull(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try { return Integer.parseInt(v.toString().trim()); }
        catch (Exception e) { return null; }
    }
    private double toDouble(Object v) {
        if (v == null || v.toString().isBlank()) return 0;
        try { return Double.parseDouble(v.toString().replace(",", "")); }
        catch (Exception e) { return 0; }
    }
}