package mes.app.pda.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LotHistoryService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getLotHistory(
            String lotNumber,
            String boxBarcode,
            String startDate,
            String endDate,
            String spjangcd) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("spjangcd", spjangcd);
        param.addValue("startDate", startDate);
        param.addValue("endDate", endDate);

        String sql = """
                SELECT
                    ml.id                                               AS lot_id
                  , ml."LotNumber"                                      AS lot_number
                  , m."Name"                                            AS mat_name
                  , m."Code"                                            AS mat_code
                  , sh."Name"                                           AS store_house_name
                  , ml."MaterialBarcode"                                AS box_barcode
                  , to_char(ml."InputDateTime", 'yyyy-mm-dd hh24:mi')  AS input_date
                  , ml."InputQty"                                       AS input_qty
                  , ml."OutQtySum"                                      AS out_qty_sum
                  , ml."CurrentStock"                                   AS current_stock
                FROM mat_lot ml
                INNER JOIN material m     ON m.id  = ml."Material_id"
                LEFT  JOIN store_house sh ON sh.id = ml."StoreHouse_id"
                WHERE 1=1
                AND ml.spjangcd = :spjangcd
                AND DATE(ml."InputDateTime") >= :startDate::date
                AND DATE(ml."InputDateTime") <= :endDate::date
                """;

        if (lotNumber != null && !lotNumber.isEmpty()) {
            sql += """
            AND ml."LotNumber" ILIKE :lotNumber
            """;  // ✅ 닫는 따옴표 다음 줄에
            param.addValue("lotNumber", "%" + lotNumber + "%");
        }

        if (boxBarcode != null && !boxBarcode.isEmpty()) {
            sql += """
            AND ml."MaterialBarcode" ILIKE :boxBarcode
            """;  // ✅
            param.addValue("boxBarcode", "%" + boxBarcode + "%");
        }

        sql += """
        ORDER BY ml."InputDateTime" DESC
        """;  // ✅

        return sqlRunner.getRows(sql, param);
    }

    public List<Map<String, Object>> getLotConsHistory(Integer lotId, String spjangcd) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("lotId", lotId);
        param.addValue("spjangcd", spjangcd);

        String sql = """
                SELECT
                                                     mlc.id                                                  AS cons_id
                                                   , to_char(mlc."OutputDateTime", 'yyyy-mm-dd hh24:mi')    AS consume_date
                                                   , mlc."OutputQty"                                         AS consume_qty
                                                   , mlc."Description"                                       AS description
                                                   , mlc."SourceTableName"                                   AS source_table_name
                                                   , SUM(mlc."OutputQty") OVER (
                                                         ORDER BY mlc."OutputDateTime" DESC
                                                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                                                     ) AS cumulative_out  -- 누적 출고량
                                                   , ml."CurrentStock" + SUM(mlc."OutputQty") OVER (
                                                         ORDER BY mlc."OutputDateTime" DESC
                                                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                                                     ) AS prev_stock  -- 이전 재고 (역산)
                                                   , ml."CurrentStock" + SUM(mlc."OutputQty") OVER (
                                                         ORDER BY mlc."OutputDateTime" DESC
                                                         ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                                                     ) AS after_stock  -- 소비 후 재고 (역산)
                                                 FROM mat_lot_cons mlc
                                                 INNER JOIN mat_lot ml ON ml.id = mlc."MaterialLot_id"
                                                 WHERE mlc."MaterialLot_id" = :lotId
                                                 AND mlc.spjangcd = :spjangcd
                                                 ORDER BY mlc."OutputDateTime" ASC
                """;

        return sqlRunner.getRows(sql, param);
    }
}
