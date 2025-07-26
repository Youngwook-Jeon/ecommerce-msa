package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
