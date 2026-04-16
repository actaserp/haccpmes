package mes.app.definition.service;

import lombok.extern.slf4j.Slf4j;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class SOPService {

    @Autowired
    SqlRunner sqlRunner;

    // application.properties에서 경로 주입
    @Value("${file_upload_path}")
    private String fileUploadPath;

    // sop 저장 경로 동적 생성
    private String getSopUploadPath() {
        return fileUploadPath + "sop" + File.separator;
    }

    // ── 목록 조회 ──────────────────────────────────────────
    public List<Map<String, Object>> getSopList(String sopName, String spjangcd) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("spjangcd", spjangcd);

        String sql = """
                SELECT id, sop_name, _created, _updated
                FROM sop
                WHERE spjangcd = :spjangcd
                """;

        if (sopName != null && !sopName.isBlank()) {
            sql += " AND sop_name LIKE :sop_name";
            params.addValue("sop_name", "%" + sopName.trim() + "%");
        }

        sql += " ORDER BY id DESC";

        return sqlRunner.getRows(sql, params);
    }

    // ── 단건 상세 조회 ─────────────────────────────────────
    public Map<String, Object> getSopDetail(Long id) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);

        String sql = """
                SELECT *
                FROM sop
                WHERE id = :id
                """;

        return sqlRunner.getRow(sql, params);
    }

    // ── 저장 (신규/수정) ────────────────────────────────────
    @Transactional
    public void saveSop(Map<String, Object> params, MultipartFile[] photos) throws IOException {

        String id = (String) params.get("id");
        boolean isNew = (id == null || id.isBlank());

        // 사진 처리 (1~8번)
        for (int i = 1; i <= 8; i++) {
            MultipartFile photo = photos[i - 1];
            if (photo != null && !photo.isEmpty()) {

                // 기존 파일 삭제 (수정 시)
                if (!isNew) {
                    deleteOldPhoto(Long.parseLong(id), i);
                }

                // UUID로 파일명 생성
                String uuid = UUID.randomUUID().toString();
                String ext  = getExtension(photo.getOriginalFilename());
                String savePath = getSopUploadPath();          // c:\temp\mes21\sop\
                String fileName = uuid + ext;                  // uuid.jpg

                // 디렉토리 없으면 생성
                File dir = new File(savePath);
                if (!dir.exists()) dir.mkdirs();

                // 파일 저장
                photo.transferTo(new File(savePath + fileName));

                params.put("photo_path_" + i, savePath);      // c:\temp\mes21\sop\
                params.put("photo_uuid_" + i, uuid + ext);    // uuid.jpg (확장자 포함)
            }
        }

        if (isNew) {
            insert(params);
        } else {
            update(params);
        }
    }

    // ── INSERT ─────────────────────────────────────────────
    private void insert(Map<String, Object> params) {

        MapSqlParameterSource p = buildSqlParams(params);

        String sql = """
                INSERT INTO sop (
                  sop_name,
                  work_method_1, inspection_1, photo_path_1, photo_uuid_1,
                  work_method_2, inspection_2, photo_path_2, photo_uuid_2,
                  work_method_3, inspection_3, photo_path_3, photo_uuid_3,
                  work_method_4, inspection_4, photo_path_4, photo_uuid_4,
                  work_method_5, inspection_5, photo_path_5, photo_uuid_5,
                  work_method_6, inspection_6, photo_path_6, photo_uuid_6,
                  work_method_7, inspection_7, photo_path_7, photo_uuid_7,
                  work_method_8, inspection_8, photo_path_8, photo_uuid_8,
                  remarks, ppe, spjangcd, created_by, _created, _updated
                ) VALUES (
                  :sop_name,
                  :work_method_1, :inspection_1, :photo_path_1, :photo_uuid_1,
                  :work_method_2, :inspection_2, :photo_path_2, :photo_uuid_2,
                  :work_method_3, :inspection_3, :photo_path_3, :photo_uuid_3,
                  :work_method_4, :inspection_4, :photo_path_4, :photo_uuid_4,
                  :work_method_5, :inspection_5, :photo_path_5, :photo_uuid_5,
                  :work_method_6, :inspection_6, :photo_path_6, :photo_uuid_6,
                  :work_method_7, :inspection_7, :photo_path_7, :photo_uuid_7,
                  :work_method_8, :inspection_8, :photo_path_8, :photo_uuid_8,
                  :remarks, :ppe, :spjangcd, :created_by, NOW(), NOW()
                )
                """;

        sqlRunner.execute(sql, p);
    }

    // ── UPDATE ─────────────────────────────────────────────
    private void update(Map<String, Object> params) {

        MapSqlParameterSource p = buildSqlParams(params);
        p.addValue("id", Long.parseLong((String) params.get("id")));

        String sql = """
                UPDATE sop SET
                  sop_name      = :sop_name,
                  work_method_1 = :work_method_1, inspection_1 = :inspection_1,
                  photo_path_1  = COALESCE(:photo_path_1, photo_path_1),
                  photo_uuid_1  = COALESCE(:photo_uuid_1, photo_uuid_1),
                  work_method_2 = :work_method_2, inspection_2 = :inspection_2,
                  photo_path_2  = COALESCE(:photo_path_2, photo_path_2),
                  photo_uuid_2  = COALESCE(:photo_uuid_2, photo_uuid_2),
                  work_method_3 = :work_method_3, inspection_3 = :inspection_3,
                  photo_path_3  = COALESCE(:photo_path_3, photo_path_3),
                  photo_uuid_3  = COALESCE(:photo_uuid_3, photo_uuid_3),
                  work_method_4 = :work_method_4, inspection_4 = :inspection_4,
                  photo_path_4  = COALESCE(:photo_path_4, photo_path_4),
                  photo_uuid_4  = COALESCE(:photo_uuid_4, photo_uuid_4),
                  work_method_5 = :work_method_5, inspection_5 = :inspection_5,
                  photo_path_5  = COALESCE(:photo_path_5, photo_path_5),
                  photo_uuid_5  = COALESCE(:photo_uuid_5, photo_uuid_5),
                  work_method_6 = :work_method_6, inspection_6 = :inspection_6,
                  photo_path_6  = COALESCE(:photo_path_6, photo_path_6),
                  photo_uuid_6  = COALESCE(:photo_uuid_6, photo_uuid_6),
                  work_method_7 = :work_method_7, inspection_7 = :inspection_7,
                  photo_path_7  = COALESCE(:photo_path_7, photo_path_7),
                  photo_uuid_7  = COALESCE(:photo_uuid_7, photo_uuid_7),
                  work_method_8 = :work_method_8, inspection_8 = :inspection_8,
                  photo_path_8  = COALESCE(:photo_path_8, photo_path_8),
                  photo_uuid_8  = COALESCE(:photo_uuid_8, photo_uuid_8),
                  remarks       = :remarks,
                  ppe           = :ppe,
                  _updated      = NOW()
                WHERE id = :id
                """;

        sqlRunner.execute(sql, p);
    }

    // ── 삭제 ───────────────────────────────────────────────
    @Transactional
    public void deleteSop(Long id) {

        // 연결된 사진 파일 먼저 삭제
        Map<String, Object> row = getSopDetail(id);
        if (row != null) {
            for (int i = 1; i <= 8; i++) {
                String path = (String) row.get("photo_path_" + i);
                String uuid = (String) row.get("photo_uuid_" + i);
                if (path != null && uuid != null) {
                    new File(path + uuid).delete();  // 실제 파일 경로
                }
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);
        sqlRunner.execute("DELETE FROM sop WHERE id = :id", params);
    }

    // ── 공통: SQL 파라미터 빌드 ────────────────────────────
    private MapSqlParameterSource buildSqlParams(Map<String, Object> params) {

        MapSqlParameterSource p = new MapSqlParameterSource();

        p.addValue("sop_name",  params.get("sop_name"));
//        p.addValue("issue_date", nullIfBlank(params, "issue_date"));
//        p.addValue("rev_date",   nullIfBlank(params, "rev_date"));
        p.addValue("remarks",    params.get("remarks"));
        p.addValue("ppe",        params.get("ppe"));
        p.addValue("spjangcd",   params.get("spjangcd"));
        p.addValue("created_by", params.get("created_by"));

        for (int i = 1; i <= 8; i++) {
            p.addValue("work_method_" + i, params.get("work_method_" + i));
            p.addValue("inspection_"  + i, params.get("inspection_"  + i));
            // 새 사진이 없으면 null → UPDATE 시 COALESCE로 기존 값 유지
            p.addValue("photo_path_" + i, params.get("photo_path_" + i));
            p.addValue("photo_uuid_" + i, params.get("photo_uuid_" + i));
        }

        return p;
    }

    // ── 기존 사진 파일 삭제 ────────────────────────────────
    private void deleteOldPhoto(Long id, int seq) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        Map<String, Object> row = sqlRunner.getRow(
                "SELECT photo_path_" + seq + ", photo_uuid_" + seq + " FROM sop WHERE id = :id", p);
        if (row != null) {
            String path = (String) row.get("photo_path_" + seq);
            String uuid = (String) row.get("photo_uuid_" + seq);
            if (path != null && uuid != null) {
                new File(path + uuid).delete();
            }
        }
    }

    // ── 유틸 ───────────────────────────────────────────────
    private Object nullIfBlank(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null || val.toString().isBlank()) return null;
        return val;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }

    // 작업표준서 문서 경로
    private static final String SOP_TEMPLATE_PATH = "C:\\Temp\\mes21\\문서\\SOPDemo.xlsx";

    public byte[] generateSopExcel(Long id) throws IOException {

        // DB에서 데이터 조회
        Map<String, Object> data = getSopDetail(id);
        if (data == null) throw new IllegalArgumentException("SOP 데이터를 찾을 수 없습니다.");

        // 템플릿 파일 로드
        try (FileInputStream fis = new FileInputStream(SOP_TEMPLATE_PATH);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            // ── 기본 정보 ──────────────────────────────────────
            setCellValue(sheet, 3, 6, data.get("sop_name"));  // G4: 품명

            // 제정일자 → _created, 개정일자 → _updated
            setCellValue(sheet, 2, 24, data.get("_created")); // Y3: 제정일자
            setCellValue(sheet, 3, 24, data.get("_updated")); // Y4: 개정일자

            // ── 작업항목 셀 매핑 ───────────────────────────────
            // [항목번호] → {작업방법 row, 작업방법 col, 검사항목 row, 검사항목 col, 사진영역}
            int[][] itemMap = {
                    // {작업방법 row, 작업방법 col, 검사항목 row, 검사항목 col}  (0-index)
                    {15,  5, 17,  5},   // 1번: F16, F18  (col 5)
                    {15, 18, 17, 18},   // 2번: S16, S18  (col 18) ← 수정
                    {15, 31, 17, 31},   // 3번: AF16, AF18 (col 31) ← 수정
                    {15, 44, 17, 44},   // 4번: AS16, AS18 (col 44) ← 수정
                    {29,  5, 31,  5},   // 5번: F30, F32  (col 5)
                    {29, 18, 31, 18},   // 6번: S30, S32  (col 18) ← 수정
                    {29, 31, 31, 31},   // 7번: AF30, AF32 (col 31) ← 수정
                    {29, 44, 31, 44},   // 8번: AS30, AS32 (col 44) ← 수정
            };

            // 사진 앵커 위치 [항목번호] → {row1, col1, row2, col2} (0-index)
            int[][] photoMap = {
                    {6, 1,  14, 13},  // 1번: B7:N15
                    {6, 14, 14, 26},  // 2번: O7:AA15
                    {6, 27, 14, 39},  // 3번: AB7:AN15
                    {6, 40, 14, 52},  // 4번: AO7:BA15
                    {20, 1,  28, 13}, // 5번: B21:N29
                    {20, 14, 28, 26}, // 6번: O21:AA29
                    {20, 27, 28, 39}, // 7번: AB21:AN29
                    {20, 40, 28, 52}, // 8번: AO21:BA29
            };

            Drawing<?> drawing = sheet.createDrawingPatriarch();

            for (int i = 0; i < 8; i++) {
                int seq = i + 1;

                // 작업방법 / 검사항목 텍스트
                setCellValue(sheet, itemMap[i][0], itemMap[i][1], data.get("work_method_" + seq));
                setCellValue(sheet, itemMap[i][2], itemMap[i][3], data.get("inspection_"  + seq));

                // 사진 삽입
                String photoPath = (String) data.get("photo_path_" + seq);
                String photoUuid = (String) data.get("photo_uuid_" + seq);
                if (photoPath != null && photoUuid != null) {
                    File photoFile = new File(photoPath + photoUuid);
                    if (photoFile.exists()) {
                        byte[] imgBytes = Files.readAllBytes(photoFile.toPath());
                        int pictureType = photoUuid.toLowerCase().endsWith(".png")
                                ? Workbook.PICTURE_TYPE_PNG
                                : Workbook.PICTURE_TYPE_JPEG;
                        int picIdx = wb.addPicture(imgBytes, pictureType);

                        ClientAnchor anchor = drawing.createAnchor(
                                0, 0, 0, 0,
                                photoMap[i][1], photoMap[i][0],  // col1, row1
                                photoMap[i][3], photoMap[i][2]   // col2, row2
                        );
                        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                        drawing.createPicture(anchor, picIdx);
                    }
                }
            }

            // ── 비고 / 보호구 ──────────────────────────────────
            setCellValue(sheet, 33, 5,  data.get("remarks")); // F34: 비고
            setCellValue(sheet, 33, 31, data.get("ppe"));     // AF34: 보호구

            // ── 바이트 배열로 변환 ─────────────────────────────
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    // 셀 값 세팅 헬퍼
    private void setCellValue(Sheet sheet, int rowIdx, int colIdx, Object value) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);

        Cell cell = row.getCell(colIdx);
        if (cell == null) cell = row.createCell(colIdx);

        if (value == null) {
            cell.setBlank();
        } else if (value instanceof java.sql.Timestamp) {
            // Timestamp → "yyyy-MM-dd" 형식으로 변환
            cell.setCellValue(((java.sql.Timestamp) value).toLocalDateTime().toLocalDate().toString());
        } else if (value instanceof java.sql.Date) {
            cell.setCellValue(((java.sql.Date) value).toLocalDate().toString());
        } else if (value instanceof java.time.LocalDate) {
            cell.setCellValue(value.toString());
        } else {
            String str = value.toString();
            if (str.isBlank()) {
                cell.setBlank();
            } else {
                cell.setCellValue(str);
            }
        }
    }
}