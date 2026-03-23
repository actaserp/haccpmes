package mes.app.pda.service;

import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DisaptchService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getLotListByBoxBarcode(String barcode){

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("barcode", barcode);

        String sql = """
                SELECT
                ml.id
                ,"LotNumber"
                ,"InputQty"  --들온양
                ,"OutQtySum" --나간양
                ,ml."CurrentStock" --현재고
                ,"MaterialBarcode" --박스바코드
                ,TO_CHAR("InputDateTime", 'YYYY-MM-DD') AS "InputDateTime" --입고시간
                ,"Material_id" as "MaterialId"
                ,ml."StoreHouse_id" as "StoreHouseId"  --창고아이디
                ,sh."Name" as "StoreHouseName"
                ,"Material_id" as "MaterialId"
                ,m."Name" as "MaterialName"
                FROM mat_lot ml
                LEFT JOIN store_house sh on sh.id = ml."StoreHouse_id"
                LEFT JOIN material m on m.id = ml."Material_id"
                where "MaterialBarcode" = :barcode
                """;

        List<Map<String, Object>> rows = sqlRunner.getRows(sql, param);
        return rows;
    }

    public int updateMaterialLotStorehouse(int ml_id, int storehouse_id) {
        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("ml_id", ml_id);
        param.addValue("storehouse_id", storehouse_id);
        String sql = """
        update mat_lot set "StoreHouse_id" =:storehouse_id where id=:ml_id
		""";
        return this.sqlRunner.execute(sql, param);
    }
}
