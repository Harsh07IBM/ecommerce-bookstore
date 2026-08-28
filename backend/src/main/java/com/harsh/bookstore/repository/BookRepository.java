package com.harsh.bookstore.repository;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


/**
 * BookRepository — the "data access layer" for the Book entity.
 *
 * WHAT THIS INTERFACE IS (in plain English):
 *   This isn't a class we implement ourselves. It's an INTERFACE that
 *   Spring Data JPA fills in for us AT RUNTIME. When Spring Boot starts
 *   up, it looks at every interface that extends JpaRepository and
 *   generates a working implementation for us behind the scenes.
 *
 *   The generated implementation gives us all these methods for free
 *   without writing a single line of SQL:
 *
 *     .findAll()                → return every book in the DB as a List
 *     .findAll(Pageable)        → return one PAGE of books (used for pagination)
 *     .findById(Long id)        → return Optional<Book>, empty if not found
 *     .save(Book book)          → INSERT or UPDATE, whichever fits
 *     .saveAll(Iterable<Book>)  → bulk save (used by the seed loader in Phase 5)
 *     .count()                  → how many books exist in total
 *     .deleteById(Long id)      → delete a book by id
 *     .existsById(Long id)      → boolean check
 *     ...and roughly 20 more.
 *
 *   This is "Spring Data JPA magic" — the framework generates code for us
 *   so we don't have to write and maintain plain-old-boring CRUD SQL for
 *   every entity in the system.
 *
 * WHY THIS INTERFACE IS EMPTY:
 *   Everything FEAT-01 needs (list books, find by id, save, count) is
 *   already inherited from JpaRepository. Later features that need
 *   custom queries (search, filter) will add methods HERE — using
 *   naming conventions like `findByTitleContainingIgnoreCase(String q)`.
 *   Spring generates the SQL for those methods too, based on their
 *   names.
 *
 * GENERIC PARAMETERS:
 *   JpaRepository&lt;Book, Long&gt; — the diamond brackets contain two types:
 *     Book  = the entity type this repository manages
 *     Long  = the type of that entity's @Id field
 *
 *   Spring uses these to know which class to query for, and what type the
 *   `findById(...)` parameter should accept.
 *
 * WHY @Repository IS OPTIONAL HERE:
 *   Spring auto-detects and registers any interface extending JpaRepository
 *   as a bean, whether or not it carries @Repository. The annotation is
 *   just documentation ("yes, this is a repository component") plus a
 *   slight technical benefit — it lets Spring translate database-specific
 *   exceptions into generic DataAccessException.
 */
@Repository
public interface BookRepository
        extends JpaRepository<Book, Long>,
                JpaSpecificationExecutor<Book> {

    /**
     * FEAT-02: returns a paginated page of books belonging to a specific category.
     * Spring Data generates: SELECT * FROM book WHERE category_id = ? ORDER BY ... LIMIT ? OFFSET ?
     */
    Page<Book> findByCategory(Category category, Pageable pageable);

    /**
     * FEAT-15: returns books in the same category, excluding a specific book.
     * Spring Data generates: SELECT * FROM book WHERE category_id = ? AND id <> ? ORDER BY title ASC LIMIT ?
     */
    List<Book> findByCategoryAndIdNot(Category category, Long excludeId, Pageable pageable);
}
