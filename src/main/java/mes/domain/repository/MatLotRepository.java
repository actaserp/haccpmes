package mes.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import mes.domain.entity.MaterialLot;

@Repository
public interface MatLotRepository extends JpaRepository<MaterialLot, Integer>{

	MaterialLot getMatLotById(Integer id);

	MaterialLot findBySourceTableNameAndSourceDataPkAndLotNumber(String string, int id, String lotNumber);

	MaterialLot findBySourceDataPk(int id);

	MaterialLot getByLotNumber(String lotNumber);

	boolean existsByStoreHouseId(Integer storeHouseId);

    @Query("SELECT COALESCE(SUM(m.inputQty), 0.0) FROM MaterialLot m " +
            "WHERE m.materialId = :materialId " +
            "AND m.sourceTableName = :sourceTableName " +
            "AND m.sourceDataPk = :sourceDataPk " +
            "AND m.spjangcd = :spjangcd")
    Float sumInputQtyByConditions(
            Integer materialId,
            String sourceTableName,
            Integer sourceDataPk,
            String spjangcd
    );

    boolean existsByMaterialBarCodeAndMaterialIdNot(String barcode, Integer materialId);

}
