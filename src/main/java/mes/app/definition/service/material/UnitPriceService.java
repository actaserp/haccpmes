package mes.app.definition.service.material;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.thymeleaf.util.MapUtils;

import mes.domain.services.CommonUtil;
import mes.domain.services.SqlRunner;

@Service
public class UnitPriceService {

	@Autowired
	SqlRunner sqlRunner;
	
	public List<Map<String, Object>> getPriceListByMat(int matPk){
		MapSqlParameterSource dicParam = new MapSqlParameterSource();        
        dicParam.addValue("mat_pk", matPk);
        
        String sql = """
			with A as 
            (
            select mcu.id 
            , mcu."Company_id"
            , mcu."UnitPrice"
            , mcu."FormerUnitPrice"
            , mcu."ApplyStartDate"
            , mcu."ApplyEndDate"
            , mcu."ChangeDate"
            , mcu."ChangerName"
            , mcu."Material_id"
            , row_number() over (partition by mcu."Company_id", mcu."Type" order by mcu."ApplyStartDate" desc) as g_idx
            , now() between mcu."ApplyStartDate" and mcu."ApplyEndDate" as current_check
            , now() < mcu."ApplyStartDate" as future_check
            , mcu."Type" as type
            from mat_comp_uprice mcu 
            where mcu."Material_id" = :mat_pk
            )
            select A.id
            , A."Company_id"
            , c."Name" as "CompanyName"
            , A."UnitPrice" 
            , A."FormerUnitPrice" 
            , A."ApplyStartDate"::date 
            , A."ApplyEndDate"::date 
            , A."ChangeDate"::date 
            , A."Material_id"
            , A."ChangerName" 
            , A.type
            from A 
            inner join company c on c.id = A."Company_id"
            where ( A.current_check = true or A.future_check = true or A.g_idx = 1)
            order by c."Name", A."ApplyStartDate"
        """;
        	
        
        List<Map<String, Object>> items = this.sqlRunner.getRows(sql, dicParam);
        return items;
	}
	
	public List<Map<String, Object>> getPriceHistoryByMat(int matPk, int comPk){
		MapSqlParameterSource dicParam = new MapSqlParameterSource();        
        dicParam.addValue("mat_pk", matPk);
		dicParam.addValue("com_pk", comPk);
        
        String sql = """
			select mcu.id 
            , mcu."Company_id"
            , c."Name" as "CompanyName"
            , mcu."UnitPrice" 
            , mcu."FormerUnitPrice" 
            , mcu."ApplyStartDate"::date 
            , mcu."ApplyEndDate"::date 
            , mcu."ChangeDate"::date 
            , mcu."ChangerName"
            , mcu."Type" as type
            from mat_comp_uprice mcu 
            inner join company c on c.id = mcu."Company_id"
            where 1=1
            and mcu."Material_id" = :mat_pk
            and mcu."Company_id" = :com_pk
            order by c."Name", mcu."ApplyStartDate" desc
        """;
        	
        
        List<Map<String, Object>> items = this.sqlRunner.getRows(sql, dicParam);
        return items;
	}
	
	public Map<String, Object> getPriceDetail(int pricePk){
		MapSqlParameterSource dicParam = new MapSqlParameterSource();        
        dicParam.addValue("price_pk", pricePk);
        
        String sql = """
			select mcu.id as price_id
            , m."MaterialGroup_id"
            , mg."MaterialType"
            , mcu."Material_id" 
            , mcu."Company_id" 
            , mcu."UnitPrice" as "UnitPrices"
            , mcu."PartPrices" as "partPrices"
            , mcu."ProcPrices" as "procPrices"
            , "FormerUnitPrice"
            , mcu."ApplyStartDate" as "ApplyStartDate"
            , mcu."ApplyEndDate" as "ApplyEndDate"
            , mcu."Type" as type
            from mat_comp_uprice mcu 
            inner join material m on m.id = mcu."Material_id" 
            inner join mat_grp mg on m."MaterialGroup_id" = mg.id
            where 1 = 1
            and mcu.id = :price_pk
        """;
        	
        
        Map<String, Object> item = this.sqlRunner.getRow(sql, dicParam);
        return item;
	}

	public int saveCompanyUnitPrice(Map<String, Object> data) {

		// ---------------------------
		// 1. 기본 파라미터 파싱
		// ---------------------------
		Integer materialId = CommonUtil.tryIntNull(data.get("Material_id"));
		Integer companyId  = CommonUtil.tryIntNull(data.get("Company_id"));
		String type        = CommonUtil.tryString(data.get("type"));

		// UnitPrice / UnitPrices 중 있는 값 우선 사용
		BigDecimal buyUnitPrice  = toBD2(data.get("UnitPrice"));
		BigDecimal sellUnitPrice = toBD2(data.get("UnitPrices"));

		BigDecimal unitPrice = null;

		// 첫 번째 우선순위: UnitPrices → UnitPrice
		if (sellUnitPrice != null) {
			unitPrice = sellUnitPrice;
		} else if (buyUnitPrice != null) {
			unitPrice = buyUnitPrice;
		}

		// fallback: type 기반
		if (unitPrice == null) {
			if ("01".equals(type)) unitPrice = buyUnitPrice;
			else if ("02".equals(type)) unitPrice = sellUnitPrice;
		}

		if (unitPrice == null) {
			throw new IllegalArgumentException("단가(UnitPrice, UnitPrices)가 없습니다.");
		}

		BigDecimal partPrices  = toBD2(data.get("partPrices"));
		BigDecimal procPrices  = toBD2(data.get("procPrices"));

		// 시작일: yyyy-MM-ddTHH:mm:ss
		String applyStartDateStr = CommonUtil.tryString(data.get("ApplyStartDate"));
		LocalDateTime startLdt = LocalDateTime.parse(applyStartDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		Timestamp applyStartDate = Timestamp.valueOf(startLdt);

		Integer userId = CommonUtil.tryIntNull(data.get("user_id"));
		String changerName = CommonUtil.tryString(data.get("ChangerName"));

		MapSqlParameterSource dic = new MapSqlParameterSource()
				.addValue("materialId", materialId)
				.addValue("companyId", companyId)
				.addValue("type", type)
				.addValue("applyStartDate", applyStartDate)
				.addValue("unitPrice", unitPrice)
				.addValue("partPrices", partPrices)
				.addValue("procPrices", procPrices)
				.addValue("changerName", changerName)
				.addValue("userId", userId);


		// -----------------------------------------------------
		// 2. 기존 적용 구간(rowA) 찾기: applyStartDate가 포함된 row
		// -----------------------------------------------------
		String findOldSql = """
        select id, "UnitPrice", "ApplyStartDate", "ApplyEndDate"
        from mat_comp_uprice
        where "Material_id" = :materialId
          and "Company_id" = :companyId
          and "Type" = :type
          and :applyStartDate between "ApplyStartDate" and "ApplyEndDate"
        limit 1
    """;

		Map<String, Object> oldRow = sqlRunner.getRow(findOldSql, dic);


		// -----------------------------------------------------
		// 3. 마지막 row(rowB) 찾기: ApplyEndDate = 2100-12-31
		// -----------------------------------------------------
		String lastRowSql = """
        select id, "ApplyStartDate"
        from mat_comp_uprice
        where "Material_id" = :materialId
          and "Company_id" = :companyId
          and "Type" = :type
          and "ApplyEndDate" = '2100-12-31'
        limit 1
    """;

		Map<String, Object> lastRow = sqlRunner.getRow(lastRowSql, dic);

		Timestamp lastStart = lastRow != null
				? (Timestamp) lastRow.get("ApplyStartDate")
				: null;


		// -----------------------------------------------------
		// 4. formerUnitPrice 설정
		// -----------------------------------------------------
		BigDecimal formerUnitPrice = oldRow != null
				? toBD2(oldRow.get("UnitPrice"))
				: null;


		// -----------------------------------------------------
		// 5. 기존 row 종료일 변경
		// -----------------------------------------------------
		if (oldRow != null) {
			Timestamp oldEndDate = Timestamp.valueOf(startLdt.minusDays(1));

			dic.addValue("oldId", oldRow.get("id"));
			dic.addValue("oldEndDate", oldEndDate);

			sqlRunner.execute("""
            update mat_comp_uprice
            set "ApplyEndDate" = :oldEndDate
            where id = :oldId
        """, dic);
		}


		// -----------------------------------------------------
		// 6. 신규 row는 항상 2100-12-31로 삽입
		// -----------------------------------------------------
		Timestamp applyEndDate2 =
				Timestamp.valueOf(LocalDateTime.of(2100, 12, 31, 0, 0));

		dic.addValue("applyEndDate2", applyEndDate2);
		dic.addValue("formerUnitPrice", formerUnitPrice);


		// -----------------------------------------------------
		// 7. 신규 단가 INSERT (_created / _creater_id 포함)
		// -----------------------------------------------------
		String insertSql = """
        INSERT INTO mat_comp_uprice
        ("_created", "_creater_id",
         "Material_id", "Company_id",
         "ApplyStartDate", "ApplyEndDate",
         "UnitPrice", "FormerUnitPrice",
         "ChangeDate", "ChangerName",
         "Type", "PartPrices", "ProcPrices")
        VALUES (
         now(), :userId,
         :materialId, :companyId,
         :applyStartDate, :applyEndDate2,
         :unitPrice, :formerUnitPrice,
         now(), :changerName,
         :type, :partPrices, :procPrices
        )
    """;

		return sqlRunner.execute(insertSql, dic);
	}


	// BigDecimal 소수 둘째자리 반올림
	private BigDecimal toBD2(Object v) {
		if (v == null) return null;
		try {
			return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
		} catch (Exception e) {
			return null;
		}
	}

	public int updateCompanyUnitPrice(MultiValueMap<String, Object> data){
		Integer priceId = CommonUtil.tryIntNull(data.getFirst("price_id"));
		Timestamp applyStartDate = CommonUtil.tryTimestamp(data.getFirst("ApplyStartDate"));

		// 화면 Form에서 구분:
		// 매입 → UnitPrice
		// 매출 → UnitPrices
		BigDecimal buyUnitPrice   = round2BD(data.getFirst("UnitPrice"));   // 매입
		BigDecimal sellUnitPrice  = round2BD(data.getFirst("UnitPrices"));  // 매출

		BigDecimal partPrices = round2BD(data.getFirst("partPrices"));
		BigDecimal procPrices = round2BD(data.getFirst("procPrices"));
		String type = CommonUtil.tryString(data.getFirst("type"));
		String changerName = CommonUtil.tryString(data.getFirst("ChangerName"));
		Integer userId = CommonUtil.tryIntNull(data.getFirst("user_id").toString());

		// type 에 맞게 DB에 들어갈 최종 단가 결정
		BigDecimal finalUnitPrice = null;

		if ("01".equals(type)) {
			// 매입 → UnitPrice 필드 사용
			finalUnitPrice = buyUnitPrice;
		} else {
			// 매출 → UnitPrices 필드 사용
			finalUnitPrice = sellUnitPrice;
		}

		MapSqlParameterSource dicParam = new MapSqlParameterSource();
		dicParam.addValue("priceId", priceId);
		dicParam.addValue("applyStartDate", applyStartDate, java.sql.Types.TIMESTAMP);
		dicParam.addValue("unitPrice", finalUnitPrice);
		dicParam.addValue("partPrices", partPrices);
		dicParam.addValue("procPrices", procPrices);
		dicParam.addValue("changerName", changerName);
		dicParam.addValue("userId", userId);

		String sql;

		if ("01".equals(type)) {
			sql = """
            update mat_comp_uprice
            set
                "UnitPrice" = :unitPrice,
                "PartPrices" = null,
                "ProcPrices" = null,
                "ApplyStartDate" = :applyStartDate,
                "ChangeDate" = now(),
                "ChangerName" = :changerName
            where id = :priceId
        """;
		} else { // "02" 매출
			sql = """
            update mat_comp_uprice
            set
                "UnitPrice" = :unitPrice,
                "PartPrices" = :partPrices,
                "ProcPrices" = :procPrices,
                "ApplyStartDate" = :applyStartDate,
                "ChangeDate" = now(),
                "ChangerName" = :changerName
            where id = :priceId
        """;
		}

		return this.sqlRunner.execute(sql, dicParam);
	}

	private BigDecimal round2BD(Object value) {
		if (value == null) return null;
		try {
			return new BigDecimal(value.toString())
					.setScale(2, RoundingMode.HALF_UP);
		} catch (Exception e) {
			return null;
		}
	}


	public int deleteCompanyUnitPrice(int priceId) {

		// 1️⃣ 삭제 대상 row 조회
		MapSqlParameterSource dic = new MapSqlParameterSource()
				.addValue("priceId", priceId);

		String sql = """
        select id,
               "Material_id",
               "Company_id",
               "Type",
               "ApplyStartDate",
               "ApplyEndDate"
        from mat_comp_uprice
        where id = :priceId
    """;

		Map<String, Object> del = sqlRunner.getRow(sql, dic);
		if (del == null) return 0;

		Integer materialId = CommonUtil.tryIntNull(del.get("Material_id"));
		Integer companyId  = CommonUtil.tryIntNull(del.get("Company_id"));
		String type        = CommonUtil.tryString(del.get("Type"));
		Timestamp delStart = (Timestamp) del.get("ApplyStartDate");
		Timestamp delEnd   = (Timestamp) del.get("ApplyEndDate");

		dic.addValue("materialId", materialId);
		dic.addValue("companyId",  companyId);
		dic.addValue("type",       type);
		dic.addValue("delStart",   delStart);

		// ---------------------------------------------
		// 🔥 2️⃣ 먼저 이전/다음 row 조회 (type 포함!)
		// ---------------------------------------------

		// 이전 row (삭제된 row보다 시작일이 작은 것 중 가장 최신)
		sql = """
        select id, "ApplyStartDate", "ApplyEndDate"
        from mat_comp_uprice
        where "Material_id" = :materialId
          and "Company_id"  = :companyId
          and "Type"        = :type
          and "ApplyStartDate" < :delStart
        order by "ApplyStartDate" desc
        limit 1
    """;
		Map<String, Object> prev = sqlRunner.getRow(sql, dic);

		// 다음 row (삭제된 row보다 시작일이 큰 것 중 가장 빠른 것)
		sql = """
        select id, "ApplyStartDate", "ApplyEndDate"
        from mat_comp_uprice
        where "Material_id" = :materialId
          and "Company_id"  = :companyId
          and "Type"        = :type
          and "ApplyStartDate" > :delStart
        order by "ApplyStartDate" asc
        limit 1
    """;
		Map<String, Object> next = sqlRunner.getRow(sql, dic);

		// "마지막 row" 여부는 → next 가 없으면 마지막
		boolean isLastRow = (next == null);

		// 3️⃣ 삭제
		sqlRunner.execute("delete from mat_comp_uprice where id = :priceId", dic);

		// 4️⃣ material.UnitPrice 초기화 (필요 없다면 이 부분은 제거 가능)
		sqlRunner.execute("""
        update material
        set "UnitPrice" = null
        where id = :materialId
    """, dic);

		// ---------------------------------------------
		// 🔥 5️⃣ 마지막 row 삭제 → 이전 row를 2100-12-31로 승격
		// ---------------------------------------------
		if (isLastRow) {
			if (prev != null) {
				dic.addValue("prevId", prev.get("id"));
				sqlRunner.execute("""
                update mat_comp_uprice
                set "ApplyEndDate" = '2100-12-31'
                where id = :prevId
            """, dic);
			}
			return 1;
		}

		// ---------------------------------------------
		// 🔥 6️⃣ 중간 row 삭제 → 이전 row 종료일 = 다음 row 시작 -1
		// ---------------------------------------------
		if (prev != null && next != null) {
			Timestamp nextStart = (Timestamp) next.get("ApplyStartDate");

			LocalDateTime endLdt = nextStart.toLocalDateTime().minusDays(1);
			Timestamp newEnd = Timestamp.valueOf(endLdt);

			dic.addValue("prevId", prev.get("id"));
			dic.addValue("newEnd", newEnd);

			sqlRunner.execute("""
            update mat_comp_uprice
            set "ApplyEndDate" = :newEnd
            where id = :prevId
        """, dic);
		}

		// 7️⃣ 첫 row 삭제 등 나머지는 그냥 삭제만
		return 1;
	}


}
