package mes.app.pda.service;

import io.micrometer.core.instrument.util.StringUtils;
import mes.app.shipment.service.ShipmentDoBService;
import mes.domain.entity.*;
import mes.domain.model.AjaxResult;
import mes.domain.repository.*;
import mes.domain.services.DateUtil;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ShipmentApiService {

    @Autowired
    SqlRunner sqlRunner;

	@Autowired
	MatLotRepository matLotRepository;

	@Autowired
	MatProcInputRepository matProcInputRepository;

	@Autowired
	MatProcInputReqRepository matProcInputReqRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private MaterialRepository materialRepository;

	@Autowired
	TransactionTemplate transactionTemplate;
    @Autowired
    private ShipmentDoBService shipmentDoBService;
    @Autowired
    private MatLotConsRepository matLotConsRepository;

	@Autowired
	private ShipmentSubApiService shipmentSubApiService;


	public List<Map<String, Object>> ApigetShipmentOrderList(String date_from, String date_to, String state, Integer comp_pk, Integer mat_grp_pk, Integer mat_pk, String keyword) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("date_from", Date.valueOf(date_from));
		paramMap.addValue("date_to", Date.valueOf(date_to));
		paramMap.addValue("state", state);
		paramMap.addValue("comp_pk", comp_pk);
		paramMap.addValue("mat_grp_pk", mat_grp_pk);
		paramMap.addValue("mat_pk", mat_pk);
		paramMap.addValue("keyword", keyword);

		String sql = """
				with sg as (
				SELECT\s
				    s."ShipmentHead_id",
				    SUM(s."Qty") AS total_qty,
				    MIN(m."Name") ||\s
				      CASE\s
				        WHEN COUNT(DISTINCT m."Name") > 1\s
				        THEN ' 외 ' || (COUNT(DISTINCT m."Name") - 1) || '건'
				        ELSE ''
				      END AS item_names
				FROM shipment s
				JOIN material m ON m.id = s."Material_id"
				GROUP BY s."ShipmentHead_id"
				)
				    
				select sh.id
				, sh."Company_id" as company_id
				, c."Name" as company_name
				, sh."ShipDate" as ship_date
				, sh."TotalQty" as total_qty
				, sh."TotalPrice" as total_price
				, sh."TotalVat" as total_vat
				, sh."Description" as description
				, sh."State" as state
				, fn_code_name('shipment_state', sh."State") as state_name
				, to_char(coalesce(sh."OrderDate",sh."_created") ,'yyyy-mm-dd') as order_date
				, sh."StatementIssuedYN" as issue_yn
				, sh."StatementNumber" as stmt_number\s
				, sh."IssueDate" as issue_date
				, sg."item_names" as material_name_summary
				from shipment_head sh\s
				join company c on c.id = sh."Company_id"
				join sg on sg."ShipmentHead_id" = sh.id
                where sh."ShipDate"  between :date_from and :date_to
				         """;
		if (comp_pk != null) {
			sql += " and sh.\"Company_id\" = :comp_pk ";
		}

		if (StringUtils.isEmpty(state) == false) {
			sql += "  and sh.\"State\" = :state ";
		}

		if (mat_pk != null || mat_grp_pk != null || StringUtils.isEmpty(keyword) == false) {
			sql += """
					and exists ( select 1
            		    from shipment s 
                        inner join material m on m.id = s."Material_id" 
                        left join mat_grp mg on mg.id = m."MaterialGroup_id"
                        where s."ShipmentHead_id" = sh.id 
					""";

			if (mat_pk != null) {
				sql += " and s.\"Material_id\" = :mat_pk ";
			}

			if (mat_grp_pk != null) {
				sql += " and mg.id = :mat_grp_pk ";
			}

			if (StringUtils.isEmpty(keyword) == false) {
				sql += """
						 and ( m."Name" ilike concat('%%', :keyword,'%%')
						       or m."Code" ilike concat('%%', :keyword,'%%'))
						""";
			}

			sql += """
					)
					       order by sh."ShipDate", c."Name", sh.id
					""";
		}

		List<Map<String, Object>> items = this.sqlRunner.getRows(sql, paramMap);

		return items;
    }



	public List<Map<String, Object>> getShipmentList (Integer shipment_header_id) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("shipment_header_id", shipment_header_id);

		String sql = """
	       select
	        s."ShipmentHead_id" as sh_id
	        , s.id as shipment_id
	        , sh."State"
	        , s."Material_id"
	        , mg."Name" as mat_grp_name
	        , m."Name" as mat_name
	        , m."Code" as mat_code
	        , m."id" as item_code
	        , s."UnitPrice" as unit_price
	        , s."Price" as price
	        , s."Vat" as vat
	        , (s."Price" + s."Vat") as total_price
	        , m."VatExemptionYN" as vat_ex_yn
	        , u."Name" as unit_name 
	        , s."OrderQty"
	        , s."Qty"
	        , s."Description" as description
	        , m."Standard1" as stan_dard --규격
	        , (select coalesce(sum(mlc."OutputQty" ), 0) as lot_qty from mat_lot_cons mlc where mlc."SourceDataPk" = s.id and mlc."SourceTableName"='shipment') as lot_qty
	        from shipment s 
	            inner join shipment_head sh on sh.id = s."ShipmentHead_id" 
	            inner join material m on m.id = s."Material_id" 
	            left join mat_grp mg on mg.id = m."MaterialGroup_id" 
	            left join unit u on u.id = m."Unit_id" 
	        where sh.id = :shipment_header_id	
		        		 """;

		List<Map<String, Object>> items = this.sqlRunner.getRows(sql, paramMap);

		return items;
	}

	public List<Map<String, Object>> getByLotNumber (String LotNum) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("lotNumber", LotNum);

		String sql = """
				SELECT ml.id,
				               ml.*,
				               m."Name" as material_name,
				               m.id as item_code,
				               m."Code" as material_code
				        FROM mat_lot ml
				        JOIN material m
				          ON ml."Material_id" = m.id
				        WHERE ml."LotNumber" = :lotNumber
				""";

		List<Map<String, Object>> items = this.sqlRunner.getRows(sql, paramMap);

		return items;
	}

	public List<Map<String, Object>> getShipmentHeadList(String dateFrom, String dateTo,
														 //String compPk, String matGrpPk, String matPk,
														 String keyword, String state) {

		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("dateFrom", dateFrom);
		paramMap.addValue("dateTo", dateTo);
//		paramMap.addValue("compPk", compPk);
//		paramMap.addValue("matGrpPk", matGrpPk);
//		paramMap.addValue("matPk", matPk);
		paramMap.addValue("keyword", keyword);
		//String state = "shipped";
		paramMap.addValue("state", state);

		String sql = """
				select sh.id
		        , sh."Company_id" as company_id
                , c."Name" as company_name
		        , sh."ShipDate" as ship_date
		        , sh."TotalQty" as total_qty
	            , sh."TotalPrice" as total_price
	            , sh."TotalVat" as total_vat
	            , sh."Description" as description
                , sh."State" as state
                , case 
                	when sh."State" = 'ordered'
                		then '미출고'
                	when sh."State" = 'shipped'	
                		then '출고'
                	else ''
                  end as state_name		
                --fn_code_name('shipment_state', sh."State") as state_name
                , to_char(coalesce(sh."OrderDate",sh."_created") ,'yyyy-mm-dd') as order_date
                , sh."StatementIssuedYN" as issue_yn
                , sh."StatementNumber" as stmt_number
                , sh."IssueDate" as issue_date
                from shipment_head sh
                join company c on c.id = sh."Company_id"
                where sh."ShipDate"  between cast(:dateFrom as date) and cast(:dateTo as date)
				""";

//		if (StringUtils.isEmpty(compPk)==false)  sql += " and sh.\"Company_id\" = cast(:compPk as Integer) ";
		if (StringUtils.isEmpty(state)==false)  sql += " and sh.\"State\" = :state ";
		if (//StringUtils.isEmpty(matPk)==false || StringUtils.isEmpty(matGrpPk)==false ||
			StringUtils.isEmpty(keyword)==false) {
			sql += """
					and exists ( select 1
        		    from shipment s
                    inner join material m on m.id = s."Material_id"
                    left join mat_grp mg on mg.id = m."MaterialGroup_id"
                    where s."ShipmentHead_id" = sh.id
					""";
//			if (StringUtils.isEmpty(matPk)==false)  sql += " and s.\"Material_id\"  = cast(:matPk as Integer) ";
//			if (StringUtils.isEmpty(matGrpPk)==false)  sql += " and mg.id  = cast(:matGrpPk as Integer) ";
			if (StringUtils.isEmpty(keyword)==false)  sql += " and ( m.\"Name\" ilike concat('%%',:keyword,'%%') or m.\"Code\" ilike concat('%%',:keyword,'%%')) )";


		}
		sql += """ 
		 		order by sh."ShipDate" desc
		 		""";
		List<Map<String,Object>> items = this.sqlRunner.getRows(sql, paramMap);

		return items;
	}

	public List<Map<String, Object>> getShipmentDetailItemList(String headId, Integer company_id){
		MapSqlParameterSource paramMap = new MapSqlParameterSource();
		paramMap.addValue("headId", headId);
		paramMap.addValue("companyId", company_id);

		String sql = """
				select s.id as ship_pk
				, s."Material_id" as mat_pk
				, mg."Name" as mat_grp_name
				, m."Code" as mat_code
				, m."Name" as mat_name
	            , m."UnitPrice" as mat_unit_price
	            , coalesce((s."OrderQty" * m."UnitPrice"), 0) as order_mat_price
				, u."Name" as unit_name
				, s."OrderQty" as order_qty
				, s."Qty" as ship_qty
				, s."Description" as description
				, sh."Company_id" as company_id
				, m.id as material_id
				,case
					when s."SourceTableName" = 'product' then mcu."UnitPrice"
					else su."UnitPrice"
				end as unit_price
				
				,case
					when s."SourceTableName" = 'product' then 'N'
					else su."InVatYN"
				end as invatyn
			
				,TRUNC((
				                  CASE
				                    WHEN s."SourceTableName" = 'product' THEN mcu."UnitPrice" * s."Qty"
				                    WHEN su."InVatYN" = 'Y' THEN (su."UnitPrice" * (10.0 / 11)) * s."Qty"
				                    ELSE su."UnitPrice" * s."Qty"
				                  END
				                )::numeric, 2) AS price
				,case
					when s."SourceTableName" = 'product' then (mcu."UnitPrice" * s."Qty") * 0.1
					when su."InVatYN" = 'Y' then (su."UnitPrice" - (su."UnitPrice" * (10.0/11))) * s."Qty"
					else (su."UnitPrice" * s."Qty") * 0.1
				end as vat
	            , m."VatExemptionYN" as vat_exempt_yn
	            , s."SourceDataPk" as src_data_pk
	            , s."SourceTableName" as src_table_name
				from shipment  s
				inner join material m on m.id = s."Material_id" 
				inner join mat_grp mg on mg.id = m."MaterialGroup_id"
				left join unit u on u.id = m."Unit_id" 
	            inner join shipment_head sh on sh.id = s."ShipmentHead_id"  
	            left join (	
				             			select distinct on ("Material_id") "Material_id", "UnitPrice"
				             			from mat_comp_uprice
				         				WHERE "Type" = '02'
				             				AND "Company_id" = :companyId
				             				AND "ApplyEndDate" > CURRENT_DATE
				             			order by "Material_id", "ApplyStartDate" desc
				         				) mcu on mcu."Material_id" = s."Material_id" and s."SourceTableName" = 'product'
				left join suju su on su.id = s."SourceDataPk"	
				where s."ShipmentHead_id" = cast(:headId as Integer)
	            order by m."Code", m."Name"
				""";
		List<Map<String, Object>> items = this.sqlRunner.getRows(sql, paramMap);

		return items;
	}

	//출하동작 , 코드는 길지만 읽어보면 별거없음. 그냥 출하 + lot 소모 합쳐놓은거
	@Transactional
	public AjaxResult ShipmenSaveActionByLot(List<Shipment> shipmentList, Integer sh_id, List<Map<String, Object>>  BarcodeList, String sourceData, Authentication auth){

		/** init variables **/
		AjaxResult result = new AjaxResult();

		User user = (User)auth.getPrincipal();

		Timestamp now = DateUtil.getNowTimeStamp();

		//region 유효성 체크
		if (shipmentList != null){
			for (int i = 0; i < shipmentList.size(); i++) {
				Integer materialId = shipmentList.get(i).getMaterialId();
				Material material = materialRepository.getMaterialById(materialId);

				if (material != null) {
					Float currentStock = material.getCurrentStock() != null ? material.getCurrentStock() : 0;
					Double shipQty = shipmentList.get(i).getQty() != null ? shipmentList.get(i).getQty() : 0;

					Float parsedShipQty = shipQty.floatValue();

					if (Float.compare(currentStock, parsedShipQty) < 0) {
						result.success = false;
						result.message = "재고 수량이 부족합니다.";
						return result;
					}

				}
			}
		}else{
			result.success = false;
			result.message = "출하상세정보가 없습니다.";
			return result;
		}
		//endregion


		List<Shipment> smList = shipmentRepository.findByShipmentHeadId(sh_id);

		//region : 출하처리 로직
		if (smList != null) {

			for (int i = 0; i < smList.size(); i++) {
				Shipment sm = smList.get(i);

				if (sm != null) {
					Double orderQty = sm.getOrderQty();
					sm.set_status("a");
					sm.set_audit(user);
					sm.setQty(orderQty); //나중에 과출하가 가능해지면 주석 ㄱㄱ

					this.shipmentRepository.save(sm);

				}
			}
		}

		//TODO: 확인필요
		//shipmentSubApiService.updateShipmentStateComplete_pda(sh_id, "", sourceData); //하나의 트랜잭션으로 묶어줘야 정상실행된다. 그래서 서비스클래스 또 따로분리 , //또한 한 클래스 안에서 트랜잭션은 안걸리므로 자기자신을 주입받아야함.

		shipmentSubApiService.updateSujuShipmentState_pda(sh_id);
		//endregion


		//region: mat_lot_cons , lot 소모 처리 관련 로직
		for(int i=0; i < BarcodeList.size(); i++){
			Integer ml_id = (Integer) BarcodeList.get(i).get("ml_id");
			Float quantity = Float.valueOf( BarcodeList.get(i).get("assignedQty").toString());

			Optional<MaterialLot> mat_lot = matLotRepository.findById(ml_id);

			if(mat_lot.isPresent()){
				Float currentStock = mat_lot.get().getCurrentStock();
				if(quantity > currentStock){
					result.success = false;
					result.message = "[" + mat_lot.get().getLotNumber() + "] : " +"현재고 보다 많은 량을 출하 할 수 없습니다.";
					return  result;
				}

			}else{
				result.success = false;
				result.message = "해당 LOT에 대한정보가 없습니다.";
				return result;
			}

			Integer shipmentId = Integer.parseInt(BarcodeList.get(i).get("shipment_id").toString());
			MatLotCons matlotcons = new MatLotCons();
			matlotcons.setMaterialLotId(ml_id);
			matlotcons.setSourceDataPk(shipmentId);
			matlotcons.setSourceTableName("shipment");

			matlotcons.setOutputDateTime(now);
			matlotcons.setOutputQty(quantity);
			matlotcons.set_audit(user);

			matLotConsRepository.save(matlotcons);

			//shipmentSubApiService.updateShipmentStateComplete_pda(sh_id, "", sourceData);
			shipmentSubApiService.updateShipmentQantityByLotConsume_pda(sh_id, shipmentId, sourceData);
			shipmentSubApiService.updateShipmentStateComplete_pda(sh_id, "", sourceData);


		}
		//endregion

		return result;
	}

}
