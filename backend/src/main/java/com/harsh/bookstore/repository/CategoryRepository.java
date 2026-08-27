package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CategoryRepository — data access for the Category entity.
 *
 * findBySlugIgnoreCase:
 *   Spring Data derives the SQL from the method name.
 *   "IgnoreCase" generates WHERE LOWER(slug) = LOWER(?1),
 *   so "Fiction", "fiction", "FICTION" all match the same row.
 *
 * findAllWithBookCount:
 *   A JPQL query that returns every Category alongside a count of
 *   how many Books reference it. Uses LEFT JOIN so categories with
 *   zero books still appear (count = 0). Ordered alphabetically.
 *   Returns Object[] rows: [Category, Long bookCount].
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlugIgnoreCase(String slug);

    @Query("SELECT c, COUNT(b.id) FROM Category c " +
           "LEFT JOIN Book b ON b.category = c " +
           "GROUP BY c " +
           "ORDER BY c.name ASC")
    List<Object[]> findAllWithBookCount();
}
