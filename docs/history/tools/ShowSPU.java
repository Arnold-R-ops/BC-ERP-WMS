import java.sql.*;

public class ShowSPU {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/wms_db", "postgres", "123465")) {

            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM product_spu WHERE id = 0"
            );

            if (rs.next()) {
                System.out.println("Default SPU (Product Family):");
                System.out.println("  ID: " + rs.getLong("id"));
                System.out.println("  SPU Code: " + rs.getString("spu_code"));
                System.out.println("  SPU Name: " + rs.getString("spu_name"));
                System.out.println("  Category: " + rs.getString("category"));
                System.out.println("  Description: " + rs.getString("description"));
                System.out.println("  Enabled: " + rs.getBoolean("enabled"));
                System.out.println("\nThis default SPU is used for all legacy products");
                System.out.println("that existed before V3.3 migration.");
            }
        }
    }
}
