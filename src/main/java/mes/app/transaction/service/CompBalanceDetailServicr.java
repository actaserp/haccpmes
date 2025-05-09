package mes.app.transaction.service;

import lombok.extern.slf4j.Slf4j;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CompBalanceDetailServicr {

  @Autowired
  SqlRunner sqlRunner;

  public List<Map<String, Object>> getList(Timestamp start, Timestamp end, String company, String spjangcd) {

    if (company == null || company.trim().isEmpty()) {
      //log.warn("⚠️ 거래처 코드가 비어 있습니다. 빈 리스트를 반환합니다.");
      return Collections.emptyList(); // 빈 결과 반환
    }

    MapSqlParameterSource paramMap = new MapSqlParameterSource();
    paramMap.addValue("start", start);
    paramMap.addValue("end", end);
    paramMap.addValue("company", Integer.parseInt(company));
    paramMap.addValue("spjangcd", spjangcd);


    // Timestamp → LocalDate
    LocalDate startDate = start.toLocalDateTime().toLocalDate();

    // 전월 구하기
    LocalDate prevMonth = startDate.minusMonths(1);

    // yyyymm
    String prevYm = prevMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

    // 기준일 (예: 20250301)
    String baseDate = prevMonth.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // SQL 파라미터 등록
    paramMap.addValue("prevYm", prevYm);
    paramMap.addValue("baseDate", baseDate);


    String sql = """
        WITH lasttbl AS (
            SELECT cltcd, yearamt, MAX(yyyymm) AS yyyymm
            FROM tb_yearamt
            WHERE yyyymm < :prevYm
              AND ioflag = '0'
              AND cltcd = :company
              AND spjangcd = :spjangcd
            GROUP BY cltcd, yearamt
        ),
        saletbl AS (
            SELECT s.cltcd, SUM(s.totalamt) AS totsale
            FROM tb_salesment s
            WHERE s.misdate BETWEEN :start AND :end
              AND s.cltcd = :company
              AND s.spjangcd = :spjangcd
            GROUP BY s.cltcd
        ),
        incomtbl AS (
            SELECT s.cltcd, SUM(s.accout) AS totaccout
            FROM tb_banktransit s
            WHERE s.trdate BETWEEN :start AND :end
              AND s.cltcd = :company
              AND s.spjangcd = :spjangcd
            GROUP BY s.cltcd
        ),
        union_data_raw AS (
            -- 전잔액
            SELECT
                y.id,
                y."Name" AS comp_name,
                TO_DATE(:baseDate, 'YYYYMMDD') AS date,
                '전잔액' AS summary,
                SUM(h.yearamt) + SUM(P.totsale) - SUM(Q.totaccout) AS amount,
                NULL::text AS itemnm,
                NULL::text AS misgubun,
                NULL::text AS iotype,
                NULL::text AS banknm,
                NULL::text AS accnum,
                NULL::text AS eumnum,
                NULL::text AS eumtodt,
                NULL::text AS tradenm,
                NULL::numeric AS accin,
                NULL::numeric AS accout,
                NULL::text AS memo,
                0 AS remaksseq
            FROM company y
            JOIN lasttbl h ON y.id = h.cltcd
            JOIN saletbl P ON y.id = P.cltcd
            JOIN incomtbl Q ON y.id = Q.cltcd
            GROUP BY y.id, y."Name"
            UNION ALL
            -- 매출
            SELECT
                s.cltcd,
                c."Name" AS comp_name,
                TO_DATE(s.misdate, 'YYYYMMDD'),
                '매출',
                s.totalamt,
                CONCAT(
                    MAX(CASE WHEN d.misseq::int = 1 THEN d.itemnm END),
                    CASE WHEN COUNT(DISTINCT d.itemnm) > 1 THEN ' 외 ' || (COUNT(DISTINCT d.itemnm) - 1) || '건' ELSE '' END
                ) AS itemnm,
                s.misgubun,
                NULL::text AS iotype,
                NULL::text AS banknm,
                NULL::text AS accnum,
                NULL::text AS eumnum,
                NULL::text AS eumtodt,
                NULL::text AS tradenm,
                NULL::numeric AS accin,
                NULL::numeric AS accout,
                NULL::text AS memo,
                1 AS remaksseq
            FROM tb_salesment s
            LEFT JOIN tb_salesdetail d ON s.misdate = d.misdate AND s.misnum = d.misnum AND s.spjangcd = d.spjangcd
            JOIN company c ON c.id = s.cltcd AND c.spjangcd = s.spjangcd
            WHERE s.misdate BETWEEN :start AND :end
              AND s.spjangcd = :spjangcd
              AND s.cltcd = :company
            GROUP BY s.cltcd, c."Name", s.misdate, s.totalamt, s.misgubun
            UNION ALL
            -- 입금
            SELECT
                b.cltcd,
                c."Name" AS comp_name,
                TO_DATE(b.trdate, 'YYYYMMDD'),
                '입금액',
                NULL::numeric AS amount,
                NULL::text AS itemnm,
                NULL::text AS misgubun,
                sc."Value" AS iotype,
                b.banknm,
                b.accnum,
                b.eumnum,
                TO_CHAR(TO_DATE(NULLIF(b.eumtodt, ''), 'YYYYMMDD'), 'YYYY-MM-DD') AS eumtodt,
                tt.tradenm,
                b.accin,
                b.accout,
                b.memo,
                2 AS remaksseq
            FROM tb_banktransit b
            JOIN company c ON c.id = b.cltcd AND c.spjangcd = b.spjangcd
            LEFT JOIN sys_code sc ON sc."Code" = b.iotype
            LEFT JOIN tb_trade tt ON tt.trid = b.trid AND tt.spjangcd = b.spjangcd
            WHERE TO_DATE(b.trdate, 'YYYYMMDD') BETWEEN :start AND :end
              AND b.cltcd = :company
              AND b.spjangcd = :spjangcd
        ),
        union_data AS (
            SELECT *,
                   ROW_NUMBER() OVER (PARTITION BY id, date ORDER BY remaksseq) AS rn
            FROM union_data_raw
        )
        SELECT
            x.id AS cltid,
            x.comp_name,
            x.date,
            x.summary,
            x.amount,
            x.itemnm,
            x.misgubun,
            x.iotype,
            x.banknm,
            x.accnum,
            x.eumnum,
            x.eumtodt,
            x.tradenm,
            x.accin,
            x.accout,
            x.memo,
            SUM(
                 CASE
                     WHEN x.summary = '입금액' THEN -1 * COALESCE(x.accin, 0)
                     ELSE 0
                 END
             ) OVER (
                 PARTITION BY x.id
                 ORDER BY x.date, x.remaksseq
                 ROWS UNBOUNDED PRECEDING
             ) AS balance
        FROM union_data x
        ORDER BY x.id, x.date, x.remaksseq
        """;
    List<Map<String, Object>> items = this.sqlRunner.getRows(sql, paramMap);
//    log.info("거래처별잔액명세서(입금) read SQL: {}", sql);
//    log.info("SQL Parameters: {}", paramMap.getValues());
    return items;
  }

}
