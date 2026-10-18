/**
 * JalDrishti Adapter — Persistence (PostgreSQL + jOOQ + Flyway).
 *
 * <p>All SQL access is via jOOQ — no JPA/Hibernate. Flyway migrations in
 * {@code db/migration/}. All tables include a {@code run_id} column;
 * live data uses {@code run_id = 'live'}.
 */
package org.jaldrishti.adapter.persistence;
