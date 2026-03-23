package mes.app.pda.service;

import io.micrometer.core.instrument.util.StringUtils;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
public class InventoryManagementApiService {

    @Autowired
    SqlRunner sqlRunner;

    public List<Map<String, Object>> getMatInoutList(String srchStartDt,
                                                     String srchEndDt,
                                                     String inout,
                                                     String housePk,
                                                     String keyword,
                                                     String spjangcd
                                                     ){
        srchStartDt = convertToDateFormat(srchStartDt);
        srchEndDt   = convertToDateFormat(srchEndDt);

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("srchStartDt", srchStartDt);
        param.addValue("srchEndDt", srchEndDt);
        param.addValue("inout", inout);
        param.addValue("housePk", housePk);
        param.addValue("keyword", keyword);
        param.addValue("spjangcd", spjangcd);

        String sql = """
                select distinct mi.id as mio_pk
                    , fn_code_name('inout_type', mi."InOut") as inout
                    , mi."Material_id" as material_id
                    , mi."InputType"  as input_type
                    , mi."OutputType" as output_type
                    , case when mi."InOut" = 'in' then fn_code_name('input_type', mi."InputType") 
	                    when mi."InOut" = 'return' then fn_code_name('return_type', mi."InputType")
	                    end as inout_type
                    , to_char(mi."InoutDate",'yyyy-mm-dd ') as inout_date
                    , to_char(mi."InoutTime", 'hh24:mi') as inout_time
                    , sh."Name" as "store_house_name"
                    , m."Code" as "material_code"
                    , m."Name" as "material_name"
                    , m."CurrentStock" as "current_stock"
                    , m."ValidDays" as "valid_days"
                    , m."LotSize" as "lot_size"
                    , m."PackingUnitQty" as "packing_unit_qty"
                    , mi."StoreHouse_id" as "storehouse_id"
                    , mih2."CurrentStock" as "house_stock"
                    , m."SafetyStock" as "safety_stock"
                    , coalesce(mi."InputQty", 0) as "input_qty"
                    , coalesce(mi."OutputQty", 0) as "output_qty"
                    , u2."Name" as "unit_name"
                    , mi."Description" as "description"
                    , fn_code_name('mat_type', mg."MaterialType") as material_type
                    , COALESCE(lot_summary.total_qty, 0) as input_lot_qty
                    , COALESCE(lot_summary.lot_cnt, 0) as lot_count
                    , (select count(ml."LotNumber") as lot_count 
                        from mat_lot ml 
                        where ml."SourceTableName" ='mat_inout' 
                        and ml."SourceDataPk" = mi.id
                        )  as lot_count 
                    , coalesce(mi."PotentialInputQty",0) as "potentialInputQty"
                    , fn_code_name('inout_state', mi."State" ) as "inout_state"
                    , var."StateName" as "state_name"
                    , tir."JudgeCode" as judge_code
                    , m."LotUseYN" as lot_use
                    , c."Name" as "company_name"
                    from mat_inout mi 
                    inner join material m on mi."Material_id" = m.id
                    
                    left join (
                    	SELECT
                    		  "SourceDataPk"
                    		, "SourceTableName"
                    		, SUM("InputQty") as total_qty
                    		, COUNT("LotNumber") as lot_cnt
                    	FROM mat_lot
                    	WHERE "SourceTableName" = 'mat_inout'
                    	GROUP BY "SourceDataPk", "SourceTableName"
                    ) lot_summary ON lot_summary."SourceDataPk" = mi.id
                    
                    left join mat_grp mg on mg.id = m."MaterialGroup_id"
                    inner join store_house sh on mi."StoreHouse_id" = sh.id
                    left join unit u2 on m."Unit_id" = u2.id 
                    --left join mat_order mo on mi."MaterialOrder_id" = mo.id 
                    --and m.id = mo."Material_id" 
                    left join mat_in_house mih2 on mih2."Material_id"  = m.id
                    and mih2."StoreHouse_id" = mi."StoreHouse_id"
                    left join rela_data rd on mi.id = rd."DataPk2" and rd."RelationName" = 'mat_inout_test_result' and rd."TableName2"  = 'mat_inout'
                    left join bundle_head bh on bh.id = rd."DataPk1" and rd."RelationName" = 'mat_inout_test_result' and rd."TableName1"  = 'bundle_head'
                    left join v_appr_result var on var."SourceDataPk" = bh.id and var."SourceTableName" ='bundle_head'
                    left join test_result tr on tr."SourceDataPk"  = mi.id and tr."SourceTableName" = 'mat_inout'
                    left join test_item_result tir on tr.id = tir."TestResult_id"
                    left join company c on c.id= mi."Company_id"
                    where 1 = 1
                    and m."Useyn" = '0'
                    AND mi."InOut" IN ('in', 'return')
						AND (
							:inout IS NULL
							OR :inout = ''
							OR mi."InOut" = :inout
						)
					--and sh."HouseType" = 'material'
                    and mi."InoutDate" between cast(:srchStartDt as date) and cast(:srchEndDt as date)
                    and mi.spjangcd = :spjangcd
                    and mi."InputType" = 'order_in'
                """;

        if(StringUtils.isEmpty(housePk)==false) sql +=" and sh.id = cast(:housePk as Integer) ";
        if (StringUtils.isEmpty(keyword)==false) sql +=" and upper(m.\"Name\") like concat('%%',upper(:keyword),'%%') ";

        sql += " order by inout_date desc, inout_time desc, mi.id desc ";

        List<Map<String, Object>> result = sqlRunner.getRows(sql, param);

        return result;
    }

    public List<Map<String, Object>> getMatInoutDetail(Integer mat_id, Integer mio_pk, String spjangcd){

        MapSqlParameterSource param = new MapSqlParameterSource();


        param.addValue("mat_id", mat_id);
        param.addValue("mio_pk", mio_pk );
        param.addValue("spjangcd", spjangcd);


        String sql = """
                select
                	ml.id, "LotNumber" as lot_number,
                	"InputQty" as input_qty,
                	"StoreHouse_id" as "StoreHouseId",
                 	sh."Name" as "StoreHouseName",
                	"MaterialBarcode" as box_barcode
                	from mat_lot ml
                	left join store_house sh on sh.id = ml."StoreHouse_id"
                	where "Material_id" = :mat_id
                	and "SourceTableName" = 'mat_inout'
                	and "SourceDataPk" = :mio_pk
                	and ml.spjangcd = :spjangcd;
                """;


        List<Map<String, Object>> list = sqlRunner.getRows(sql, param);

        return list;
    }

    /**
     * 날짜 문자열을 yyyy-MM-dd 형식으로 변환
     * 이미 yyyy-MM-dd 형식이면 그대로 반환
     */
    private String convertToDateFormat(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return dateStr;

        // 이미 yyyy-MM-dd 형식이면 그대로
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) return dateStr;

        // 시도할 포맷 목록
        List<String> patterns = List.of(
                "yyyyMMdd",
                "yyyy/MM/dd",
                "yyyy.MM.dd",
                "dd-MM-yyyy",
                "dd/MM/yyyy",
                "MM/dd/yyyy"
        );

        for (String pattern : patterns) {
            try {
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException ignored) {

            }
        }

        throw new IllegalArgumentException("날짜 형식을 변환할 수 없습니다: " + dateStr);
    }
}
