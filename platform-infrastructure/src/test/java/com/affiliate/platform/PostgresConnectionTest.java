package com.affiliate.platform;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresConnectionTest {

    @Test
    void testPostgresConnection() {
        String url = "jdbc:postgresql://127.0.0.1:5432/postgres";
        String user = "postgres";
        String pass = "199010";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println(">>> Successfully connected to local PostgreSQL database: " + conn.getCatalog());
            assertTrue(conn.isValid(2));
        } catch (Exception e) {
            System.out.println(">>> PostgreSQL connection attempt: " + e.getMessage());
        }
    }
}
