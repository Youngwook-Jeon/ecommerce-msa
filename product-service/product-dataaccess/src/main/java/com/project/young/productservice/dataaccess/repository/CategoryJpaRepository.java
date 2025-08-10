package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long idToExclude);

    @Query("SELECT c FROM CategoryEntity c LEFT JOIN FETCH c.parent WHERE c.status = :status")
    List<CategoryEntity> findAllWithParentByStatus(@Param("status") String status);

    @Query("SELECT c FROM CategoryEntity c LEFT JOIN FETCH c.parent")
    List<CategoryEntity> findAllWithParent();

    @Query(value =
            "WITH RECURSIVE category_tree AS (" +
            "    SELECT * FROM categories WHERE id = :categoryId" +
            "    UNION ALL" +
            "    SELECT c.* FROM categories c" +
            "    INNER JOIN category_tree ct ON c.parent_id = ct.id" +
            ")" +
            "SELECT * FROM category_tree",
            nativeQuery = true
    )
    List<CategoryEntity> findSubTreeByIdNative(@Param("categoryId") Long categoryId);

    @Query(value =
            "WITH RECURSIVE category_tree AS (" +
            "    SELECT * FROM categories WHERE id = :categoryId" +
            "    UNION ALL" +
            "    SELECT c.* FROM categories c" +
            "    INNER JOIN category_tree ct ON c.parent_id = ct.id" +
            "    WHERE c.status IN (:statusList)" +
            ")" +
            "SELECT * FROM category_tree",
            nativeQuery = true
    )
    List<CategoryEntity> findSubTreeByIdAndStatusInNative(@Param("categoryId") Long categoryId, @Param("statusList") List<String> statusList);

    @Query(value =
            "WITH RECURSIVE category_ancestors AS (" +
            "    SELECT * FROM categories WHERE id = :categoryId" +
            "    UNION ALL" +
            "    SELECT c.* FROM categories c" +
            "    INNER JOIN category_ancestors ca ON c.id = ca.parent_id" +
            ")" +
            "SELECT * FROM category_ancestors",
            nativeQuery = true
    )
    List<CategoryEntity> findAncestorsByIdNative(@Param("categoryId") Long categoryId);

    @Query(value =
            "WITH RECURSIVE category_depth AS (" +
            "    SELECT id, parent_id, 0 AS depth FROM categories WHERE id = :categoryId" +
            "    UNION ALL" +
            "    SELECT c.id, c.parent_id, cd.depth + 1 FROM categories c" +
            "    INNER JOIN category_depth cd ON c.id = cd.parent_id" +
            ")" +
            "SELECT MAX(depth) FROM category_depth", // The max depth is the distance to the root
            nativeQuery = true
    )
    Integer getDepthByIdNative(@Param("categoryId") Long categoryId);

    @Modifying
    @Query("UPDATE CategoryEntity c SET c.status = :status WHERE c.id IN :ids")
    void updateStatusForIds(@Param("status") String status, @Param("ids") List<Long> ids);
}
