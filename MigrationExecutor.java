import java.sql.*;
import java.nio.file.*;

public class MigrationExecutor {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        String url = "jdbc:postgresql://localhost:5432/wms_db";
        String user = "postgres";
        String password = "123465";

        System.out.println("Connecting to database...");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);

            System.out.println("Reading migration script...");
            String sql = Files.readString(Path.of("V3.3_Migration_Complete_Idempotent.sql"));

            System.out.println("Executing migration...\n");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                conn.commit();

                SQLWarning warning = conn.getWarnings();
                while (warning != null) {
                    System.out.println(warning.getMessage());
                    warning = warning.getNextWarning();
                }
            }

            System.out.println("\nMigration completed successfully!");
        }
    }
}
