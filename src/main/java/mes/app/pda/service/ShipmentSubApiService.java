package mes.app.pda.service;

import lombok.RequiredArgsConstructor;
import mes.domain.services.SqlRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShipmentSubApiService {


    private final SqlRunner sqlRunner;


	private final ApplicationContext context;


	public void updateShipmentStateComplete_pda (Integer sh_id, String description, String sourceData) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("sh_id", sh_id);
		paramMap.addValue("description", description);

		String sql = """
				with A as(
				select
		        sh.id as sh_id
		        , count(s.id) as s_count
		        , sum(s."Price") as "TotalPrice"
		        , sum(s."Vat") as "TotalVat"
		        , sum(s."Qty") as "TotalQty"
		        from shipment s
		        inner join shipment_head sh on sh.id=s."ShipmentHead_id"
		        where sh.id=:sh_id
		        group by sh.id
		        )
		        update
		        shipment_head
		        set
		        "TotalQty" = A."TotalQty"
		        ,"TotalVat" = A."TotalVat"
		        , "TotalPrice" = A."TotalPrice"
		        , "State" = 'shipped'
		        ,"Description" = :description
		        from A
		        where id=A.sh_id
				""";

		this.sqlRunner.execute(sql, paramMap);
	}

	// 관련 수주를 찾아서 수주의 출하 상태를 변경한다.
	public void updateSujuShipmentState_pda (Integer sh_id) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("sh_id", sh_id);

		String sql = """
		        with A as(
		        select
		        s.id as shipment_id
		        ,sh.id as sh_id
		        , rd."DataPk1" as suju_id
		        , sj."State"
		        , sj."ShipmentState"
		        from shipment s 
		        inner join shipment_head sh on sh.id=s."ShipmentHead_id"
		        inner join rela_data rd on rd."TableName1" ='suju' and rd."TableName2" ='shipment' and rd."DataPk2" =s.id
		        inner join suju sj on sj.id = rd."DataPk1" 
		        where sh.id = :sh_id
		        )
		        update suju set "ShipmentState" ='shipped'
		        from A where A.suju_id = id
				""";

		this.sqlRunner.execute(sql, paramMap);
	}

	public void updateShipmentQantityByLotConsume_pda (Integer sh_id, Integer shipment_id, String sourceData) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("sh_id", sh_id);
		paramMap.addValue("shipment_id", shipment_id);
		//paramMap.addValue("sourceData", sourceData);

		String sql = """
				with A as(
	            select
	            s.id, coalesce(sum(mlc."OutputQty"),0) as qty
	            from shipment s
	            inner join shipment_head sh on sh.id = s."ShipmentHead_id" 
	            left join mat_lot_cons mlc on mlc."SourceTableName" ='shipment' and mlc."SourceDataPk" = s.id
	            where 1=1 
	            and sh.id = :sh_id
				""";

		if (shipment_id != null) {
			sql += " and s.id = :shipment_id ";
		}


		if(sourceData.equals("rela_data")){
			sql += """
				group by s.id),
				UPC as (
	            select
	            s.id
	            , s."Material_id"
	            , sh."Company_id"
	            , mcu."UnitPrice"
	            , m."VatExemptionYN"
	            from A
	            inner join shipment s on s.id = A.id
	            inner join shipment_head sh on sh.id = s."ShipmentHead_id"
	            inner join material m on m.id = s."Material_id" 
	            left join mat_comp_uprice mcu on mcu."Material_id"=s."Material_id" and mcu."Company_id"=sh."Company_id" and mcu."ApplyStartDate" <=now() and mcu."ApplyEndDate" > now()
	            where sh.id = :sh_id 
	        ), B as(        
	           select 
	           s.id
	           , A.qty
	           , UPC."UnitPrice" 
	           , (A.qty * UPC."UnitPrice") as "Price"
	           , case when UPC."VatExemptionYN"='Y' then 0 else (A.qty * UPC."UnitPrice"*0.1) end  as "Vat" 
	           , s."Material_id"
	           , UPC."Company_id"
	           , suju."InVatYN" as invat
	           from shipment s 
	           	
	           	inner join suju suju
				on suju.id = s."SourceDataPk"
				and s."SourceTableName" = 'rela_data'
				
	           	 inner join shipment_head sh2 on sh2.id = s."ShipmentHead_id"
	             inner join A on A.id = s.id             
	             inner join UPC on UPC.id = s.id
	             )
	        update shipment set 
	         "Qty" = B.qty 
	         , "UnitPrice" = B."UnitPrice"
	         , "Price" = CASE
	         		WHEN B.invat = 'Y' THEN ROUND((B."Price" / 1.1)::numeric, 2)
	         		ELSE B."Price"
	         		END
	         , "Vat" = CASE
			    WHEN B.invat = 'Y' THEN ROUND(((B."Price" / 1.1) * 0.1)::numeric, 2)
			    ELSE B."Vat"
			END
	        from B
	        where shipment.id = B.id
	        """;
		}else if(sourceData.equals("product")){
			sql += """
					group by s.id),
				UPC as (
	            select
	            s.id
	            , s."Material_id"
	            , sh."Company_id"
	            , mcu."UnitPrice"
	            , m."VatExemptionYN"
	            from A
	            inner join shipment s on s.id = A.id
	            inner join shipment_head sh on sh.id = s."ShipmentHead_id"
	            inner join material m on m.id = s."Material_id" 
	            left join mat_comp_uprice mcu on mcu."Material_id"=s."Material_id" and mcu."Company_id"=sh."Company_id" and mcu."ApplyStartDate" <=now() and mcu."ApplyEndDate" > now()
	            where sh.id = :sh_id 
	        ), B as(        
	           select 
	           s.id
	           , A.qty
	           , UPC."UnitPrice" 
	           , (A.qty * UPC."UnitPrice") as "Price"
	           , case when UPC."VatExemptionYN"='Y' then 0 else (A.qty * UPC."UnitPrice"*0.1) end  as "Vat" 
	           , s."Material_id"
	           , UPC."Company_id"
	           from shipment s 
	           
	           	 inner join shipment_head sh2 on sh2.id = s."ShipmentHead_id"
	             inner join A on A.id = s.id             
	             inner join UPC on UPC.id = s.id
	             )
	        update shipment set 
	         "Qty" = B.qty 
	         , "UnitPrice" = B."UnitPrice"
	         , "Price" = B."Price"
	         , "Vat" = B."Vat"
			from B
	        where shipment.id = B.id
					""";

		}else{
			throw new RuntimeException("SourceDataTable의 값이 올바르지 않습니다.");
		}


		this.sqlRunner.execute(sql, paramMap);
	}
}
