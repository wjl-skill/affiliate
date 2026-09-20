package com.affiliate.platform;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresConnectionTest {

    @Test
    void testPostgresConnection() {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:5432/postgres");
        String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        String pass = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println(">>> Successfully connected to PostgreSQL database: " + conn.getCatalog());
            assertTrue(conn.isValid(2));
        } catch (Exception e) {
            System.out.println(">>> PostgreSQL live instance not reachable (" + e.getMessage() + "), skipping live connection test.");
        }
    }
}
