package mes.app.definition.service;

import lombok.extern.slf4j.Slf4j;
import mes.domain.services.SqlRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    // 파일 저장 루트 경로 (환경에 맞게 수정)
    private static final String UPLOAD_ROOT = "/files/sop/";

    // ── 목록 조회 ──────────────────────────────────────────
    public List<Map<String, Object>> getSopList(String sopName, String spjangcd) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("spjangcd", spjangcd);

        String sql = """
                SELECT id, sop_name, issue_date, rev_date
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
                String uuid = UUID.randomUUID().toString();
                String ext  = getExtension(photo.getOriginalFilename());
                String path = UPLOAD_ROOT + uuid + ext;

                // 기존 파일 삭제 (수정 시)
                if (!isNew) {
                    deleteOldPhoto(Long.parseLong(id), i);
                }

                // 파일 저장
                Path filePath = Paths.get(path);
                Files.createDirectories(filePath.getParent());
                photo.transferTo(filePath.toFile());

                params.put("photo_path_" + i, path);
                params.put("photo_uuid_" + i, uuid);
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
                  sop_name, issue_date, rev_date,
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
                  :sop_name, :issue_date, :rev_date,
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
        p.addValue("id", params.get("id"));

        String sql = """
                UPDATE sop SET
                  sop_name      = :sop_name,
                  issue_date    = :issue_date,
                  rev_date      = :rev_date,
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
                if (path != null && !path.isBlank()) {
                    new File(path).delete();
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
        p.addValue("issue_date", nullIfBlank(params, "issue_date"));
        p.addValue("rev_date",   nullIfBlank(params, "rev_date"));
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
                "SELECT photo_path_" + seq + " FROM sop WHERE id = :id", p);
        if (row != null) {
            String path = (String) row.get("photo_path_" + seq);
            if (path != null && !path.isBlank()) {
                new File(path).delete();
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
}