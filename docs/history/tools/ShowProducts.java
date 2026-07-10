import java.sql.*;

public class ShowProducts {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/wms_db", "postgres", "123465")) {

            Statement stmt = conn.createStatement();

            // 查询 products 表的所有数据
            System.out.println("Products table content:");
            System.out.println("=".repeat(80));

            ResultSet rs = stmt.executeQuery("SELECT * FROM products");
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // 打印列名
            for (int i = 1; i <= columnCount; i++) {
                System.out.printf("%-20s", meta.getColumnName(i));
            }
            System.out.println("\n" + "=".repeat(80));

            // 打印数据
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    if (value != null && value.length() > 18) {
                        value = value.substring(0, 15) + "...";
                    }
                    System.out.printf("%-20s", value == null ? "NULL" : value);
                }
                System.out.println();
            }

            System.out.println("\n" + "=".repeat(80));

            // 统计信息
            rs = stmt.executeQuery("SELECT COUNT(*) as total FROM products");
            if (rs.next()) {
                System.out.println("Total products: " + rs.getInt("total"));
            }
        }
    }
}
