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
    public List<Map<String, Object>> getList(String start, String end, String spjangcd) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("start",    start + " 00:00:00");
        param.addValue("end",      end   + " 23:59:59");
        param.addValue("spjangcd", spjangcd);

        String sql = """
            SELECT
                h.id,
                to_char(h.report_date, 'yyyy-mm-dd') AS report_date,
                h.writer,
                COALESCE(SUM(r.inspection_count), 0) AS total_inspection,
                COALESCE(SUM(r.inspection_wait), 0) AS total_wait,
                COALESCE(SUM(r.delivery_wait), 0) AS total_delivery_wait,
                COALESCE(SUM(r.repair_defect),    0) AS total_defect
            FROM inspection_report_head h
            LEFT JOIN inspection_report_row r ON r.head_id = h.id
            WHERE h.spjangcd = :spjangcd
              AND h._created BETWEEN :start::timestamp AND :end::timestamp
            GROUP BY h.id, h.report_date, h.writer
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
                id,
                to_char(report_date, 'yyyy-mm-dd') AS report_date,
                writer,
                spjangcd
            FROM inspection_report_head
            WHERE id = :id
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

        Map<String, Object> head    = sqlRunner.getRow(headSql,    param);
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

        MapSqlParameterSource headParam = new MapSqlParameterSource();
        headParam.addValue("report_date", reportDateStr);
        headParam.addValue("writer",      writer);
        headParam.addValue("spjangcd",    spjangcd);
        headParam.addValue("user_id",     user.getId());

        if (headId == null) {
            String insertHead = """
                INSERT INTO inspection_report_head
                    (report_date, writer, spjangcd, _creater_id, _modifier_id)
                VALUES
                    (:report_date::date, :writer, :spjangcd, :user_id, :user_id)
                RETURNING id
            """;
            headId = sqlRunner.queryForObject(insertHead, headParam, (rs, rn) -> rs.getInt(1));
        } else {
            headParam.addValue("id", headId);
            String updateHead = """
                UPDATE inspection_report_head SET
                    report_date  = :report_date::date,
                    writer       = :writer,
                    _modified    = now(),
                    _modifier_id = :user_id
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
                rp.addValue("head_id",          headId);
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
                np.addValue("head_id",               headId);
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
    private double toDouble(Object v) {
        if (v == null || v.toString().isBlank()) return 0;
        try { return Double.parseDouble(v.toString().replace(",", "")); }
        catch (Exception e) { return 0; }
    }
}