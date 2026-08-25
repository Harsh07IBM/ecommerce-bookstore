package com.harsh.bookstore.config;

/*
 * ------------------------------------------------------------------
 * IMPORTS
 * ------------------------------------------------------------------
 * Grouped by origin (a common Java convention):
 *   - Jackson (for turning JSON text into Java objects)
 *   - Our own classes
 *   - SLF4J logger (Spring's standard logging facade)
 *   - Spring annotations
 *   - Java standard library
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.repository.BookRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;


/**
 * BookSeedLoader — populates the `book` table from data/seed/books.json
 * the first time the app runs against an empty database.
 *
 * WHAT THIS CLASS IS (in plain English):
 *   A "startup task". Spring runs its `run(...)` method EXACTLY ONCE
 *   after the application context has finished starting, but BEFORE the
 *   web server begins accepting HTTP requests. Perfect place to prepare
 *   the DB with initial data.
 *
 *   Concretely, on every app start this class:
 *     1. Checks if any books already exist. If yes → skip everything (idempotent).
 *     2. Opens the seed JSON file at the path configured in
 *        `application.properties` as `bookstore.seed.file`.
 *     3. Uses Jackson to turn every JSON entry into a Book object.
 *     4. Calls bookRepository.saveAll(...) — Hibernate emits an INSERT
 *        per book.
 *     5. Logs how many were seeded.
 *
 *   If the seed file is missing, it logs a warning and starts with an
 *   empty catalogue. It never crashes the app because of a missing file.
 *
 * WHY IDEMPOTENCY MATTERS:
 *   Every app restart runs this method. If we blindly inserted the 113
 *   books each time, we'd end up with 226, 339, ... after each restart.
 *   The `count() > 0` check makes seeding a no-op after the first run,
 *   which is what "idempotent" means: safe to run any number of times.
 *
 *   For our in-memory H2 database (which is wiped on every restart), the
 *   count is always 0 at startup, so the loader always seeds. But this
 *   pattern is essential the moment we move to a persistent DB.
 */
@Component
public class BookSeedLoader implements CommandLineRunner {

    /*
     * SLF4J logger. This is the standard logging library in the Java
     * world; Spring Boot wires it to Logback under the hood so
     * `log.info(...)` writes to the console with our chosen format.
     *
     * Making it `private static final` is convention — one logger per
     * class, shared across all instances.
     */
    private static final Logger log = LoggerFactory.getLogger(BookSeedLoader.class);


    /**
     * The BookRepository. We save books through this. It's `final` because
     * we set it once in the constructor and never reassign — immutable
     * dependencies are a good habit.
     */
    private final BookRepository bookRepository;


    /**
     * The seed file path, read from application.properties.
     *
     * @Value("${bookstore.seed.file}") tells Spring: "when you construct
     * this bean, look up the property 'bookstore.seed.file' in
     * application.properties (or environment vars, YAML, etc.) and inject
     * that value here". The ${...} placeholder is Spring's syntax.
     *
     * If the property is missing, Spring throws a startup error — a
     * feature, not a bug. It prevents silent misconfiguration.
     */
    @Value("${bookstore.seed.file}")
    private String seedFilePath;


    /**
     * Constructor injection.
     *
     * When Spring needs to create a BookSeedLoader (because it saw the
     * @Component annotation), it looks at this constructor and asks:
     * "What arguments do I need? Ah — a BookRepository. Do I have one of
     * those in the context? Yes! Let me pass it in."
     *
     * This is "dependency injection" — we never do `new BookRepository(...)`
     * ourselves. Spring finds it for us because BookRepository extends
     * JpaRepository, which Spring already registered as a bean.
     *
     * Why constructor injection (as opposed to @Autowired on a field)?
     *   - Makes the dependency explicit and documented
     *   - Makes the field `final` (immutable)
     *   - Trivial to test — you just pass a fake BookRepository in
     *   - Fails fast: Spring can't accidentally create this class without
     *     a repository
     */
    public BookSeedLoader(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }


    /**
     * The one and only method required by the CommandLineRunner interface.
     * Spring calls this after startup, once per app lifecycle.
     *
     * `throws Exception` because Jackson's readValue can throw IOException
     * and JsonProcessingException. If either happens, Spring aborts
     * startup with a clear stack trace — better than silently continuing
     * with a broken catalogue.
     */
    @Override
    public void run(String... args) throws Exception {

        // ---- Step 1: idempotency check ----
        //
        // If the DB already has books, do nothing. This makes the loader
        // safe to run on every startup, no matter what state the DB is in.
        long existingCount = bookRepository.count();
        if (existingCount > 0) {
            log.info("Books already present ({}) — skipping seed", existingCount);
            return;
        }

        // ---- Step 2: locate the seed file ----
        File file = new File(seedFilePath);
        if (!file.exists()) {
            // Absolute path helps the reader (or you, debugging later)
            // see exactly WHERE we looked.
            log.warn(
                "Seed file not found at {} — starting with an empty catalogue",
                file.getAbsolutePath()
            );
            return;
        }

        // ---- Step 3: parse the JSON ----
        //
        // ObjectMapper is Jackson's main class for turning JSON into
        // Java objects (and vice versa).
        //
        // We configure it to IGNORE unknown JSON fields, so if the seed
        // script ever adds a field that Book doesn't have (e.g. a debug
        // marker), the loader keeps working instead of throwing.
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // `new TypeReference<List<Book>>() {}` is Jackson's way of telling
        // the ObjectMapper: "the JSON is an ARRAY, and each element
        // should become a Book". Without this TypeReference, generics
        // get erased at compile time and Jackson wouldn't know what
        // element type to build.
        //
        // The `{}` at the end creates an anonymous subclass — that's what
        // makes generic-type reflection possible in Java. Odd-looking
        // idiom, but standard Jackson.
        List<Book> books = mapper.readValue(
            file,
            new TypeReference<List<Book>>() {}
        );

        // ---- Step 4: bulk insert ----
        //
        // saveAll is a single JPA call that emits INSERTs for every book.
        // Hibernate can batch these into fewer round-trips to the DB —
        // faster than looping and calling save() 113 times.
        bookRepository.saveAll(books);

        log.info(
            "Seeded {} books from {}",
            books.size(),
            file.getAbsolutePath()
        );
    }
}
