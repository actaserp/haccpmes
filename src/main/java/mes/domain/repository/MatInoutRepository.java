package mes.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import mes.domain.entity.MaterialInout;

@Repository
public interface MatInoutRepository extends JpaRepository<MaterialInout, Integer> {
	
	MaterialInout getMatInoutById(Integer id);

	List<MaterialInout> findBySourceTableNameAndSourceDataPkAndInOutAndInputType(String string, int id, String string2,
			String string3);

	List<MaterialInout> findBySourceTableNameAndSourceDataPkAndInOutAndOutputType(String string, int id, String string2,
			String string3);

	MaterialInout findBySourceTableNameAndSourceDataPkAndInOutAndInputTypeAndMaterialId(String string, int id,
			String string2, String string3, Integer materialId);

	MaterialInout findBySourceTableNameAndSourceDataPkAndInOutAndOutputTypeAndMaterialId(String string, int id,
			String string2, String string3, Integer consumeMatPk);

	void deleteBySourceTableNameAndSourceDataPkAndInOutAndInputType(String string, int id, String string2,
			String string3);

	void deleteBySourceTableNameAndSourceDataPkAndInOutAndOutputType(String string, int id, String string2,
			String string3);

    @Modifying
    @Query("UPDATE MaterialInout m " +
            "SET m.inputQty = :qty " +
            "WHERE m.sourceTableName = :tableName " +
            "  AND m.sourceDataPk = :dataPk " +
            "  AND m.inOut = :inOut")
    void updateQtyBySource(
            @Param("tableName") String tableName,
            @Param("dataPk") Integer dataPk,
            @Param("inOut") String inOut,
            @Param("qty") Float qty
    );
}
