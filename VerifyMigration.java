import java.sql.*;

public class VerifyMigration {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/wms_db", "postgres", "123465")) {

            Statement stmt = conn.createStatement();

            // Check product_spu table
            System.out.println("=".repeat(50));
            System.out.println("1. product_spu table verification:");
            System.out.println("=".repeat(50));

            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) as total, " +
                "COUNT(*) FILTER (WHERE id = 0) as default_spu " +
                "FROM product_spu"
            );
            if (rs.next()) {
                System.out.println("Total SPUs: " + rs.getInt("total"));
                System.out.println("Default SPU (ID=0): " + rs.getInt("default_spu"));
            }

            // Check products table
            System.out.println("\n" + "=".repeat(50));
            System.out.println("2. products table verification:");
            System.out.println("=".repeat(50));

            rs = stmt.executeQuery(
                "SELECT COUNT(*) as total, " +
                "COUNT(spu_id) as with_spu_id, " +
                "COUNT(sku_name) as with_sku_name, " +
                "COUNT(*) FILTER (WHERE spu_id = 0) as using_default_spu " +
                "FROM products"
            );
            if (rs.next()) {
                System.out.println("Total products: " + rs.getInt("total"));
                System.out.println("Products with spu_id: " + rs.getInt("with_spu_id"));
                System.out.println("Products with sku_name: " + rs.getInt("with_sku_name"));
                System.out.println("Products using default SPU: " + rs.getInt("using_default_spu"));
            }

            // Check inventory_batch table
            System.out.println("\n" + "=".repeat(50));
            System.out.println("3. inventory_batch table verification:");
            System.out.println("=".repeat(50));

            rs = stmt.executeQuery(
                "SELECT COUNT(*) as total, " +
                "COUNT(location_code) as with_location_code " +
                "FROM inventory_batch"
            );
            if (rs.next()) {
                System.out.println("Total batches: " + rs.getInt("total"));
                System.out.println("Batches with location_code: " + rs.getInt("with_location_code"));
            }

            // Check constraints
            System.out.println("\n" + "=".repeat(50));
            System.out.println("4. Constraints and indexes:");
            System.out.println("=".repeat(50));

            rs = stmt.executeQuery(
                "SELECT conname FROM pg_constraint " +
                "WHERE conname IN ('fk_product_spu', 'product_spu_spu_code_key')"
            );
            System.out.println("Foreign key and unique constraints:");
            while (rs.next()) {
                System.out.println("  - " + rs.getString("conname"));
            }

            rs = stmt.executeQuery(
                "SELECT indexname FROM pg_indexes " +
                "WHERE tablename IN ('products', 'inventory_batch', 'product_spu') " +
                "AND indexname LIKE 'idx_%' " +
                "ORDER BY indexname"
            );
            System.out.println("\nIndexes:");
            while (rs.next()) {
                System.out.println("  - " + rs.getString("indexname"));
            }

            System.out.println("\n" + "=".repeat(50));
            System.out.println("Migration verification completed!");
            System.out.println("=".repeat(50));
        }
    }
}
