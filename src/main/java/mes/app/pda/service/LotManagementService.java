package mes.app.pda.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LotManagementService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getLotList(
            Integer StoreHouseId,
            String materialName,
            String LotNumber,
            String spjangcd,
            boolean checkYn) {

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("StoreHouseId", StoreHouseId);
        param.addValue("materialName", "%" + materialName + "%");
        param.addValue("LotNumber", "%" + LotNumber + "%");
        param.addValue("spjangcd", spjangcd);

        String sql = checkYn ? buildGroupedSql(StoreHouseId) : buildDetailSql(StoreHouseId);

        return sqlRunner.getRows(sql, param);
    }

    private String buildGroupedSql(Integer StoreHouseId) {
        String sql = """
            SELECT m."Code"            AS mat_code
                 , m."Name"            AS mat_name
                 , SUM(ml."InputQty")  AS input_qty
                 , SUM(ml."OutQtySum") AS out_qty
                 , SUM(ml."CurrentStock") AS current_stock
            FROM mat_lot ml
            INNER JOIN material m ON m.id = ml."Material_id"
            WHERE 1=1
              AND m."Name"    ILIKE :materialName
              AND ml.spjangcd = :spjangcd
            """;

        if (StoreHouseId != null) {
            sql += """
              AND ml."StoreHouse_id" = :StoreHouseId
            """;
        }

        sql += """
            GROUP BY m."Code", m."Name"
            ORDER BY mat_name
            """;

        return sql;
    }

    private String buildDetailSql(Integer StoreHouseId) {
        String sql = """
            SELECT ml.id
                 , to_char(ml."InputDateTime", 'yyyy-mm-dd hh24:mi') AS prod_date
                 , ml."LotNumber"      AS lot_num
                 , m."Code"            AS mat_code
                 , m."Name"            AS mat_name
                 , ml."InputQty"       AS input_qty
                 , ml."OutQtySum"      AS out_qty
                 , ml."CurrentStock"   AS current_stock
            FROM mat_lot ml
            INNER JOIN material m ON m.id = ml."Material_id"
            WHERE 1=1
              AND m."Name"      ILIKE :materialName
              AND ml."LotNumber" LIKE :LotNumber
              AND ml.spjangcd   = :spjangcd
            """;

        if (StoreHouseId != null) {
            sql += """
              AND ml."StoreHouse_id" = :StoreHouseId
            """;
        }

        sql += """
            ORDER BY prod_date DESC
            """;

        return sql;
    }

}
