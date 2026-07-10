import java.sql.*;

public class CheckLocationsTable {
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/wms_db", "postgres", "123465")) {

            System.out.println("Checking locations table structure:\n");

            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, "locations", null);

            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String columnType = rs.getString("TYPE_NAME");
                System.out.println(columnName + " - " + columnType);
            }

            System.out.println("\n\nSample data from locations:");
            Statement stmt = conn.createStatement();
            ResultSet data = stmt.executeQuery("SELECT * FROM locations LIMIT 3");
            ResultSetMetaData rsmd = data.getMetaData();

            while (data.next()) {
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    System.out.print(rsmd.getColumnName(i) + "=" + data.getString(i) + " ");
                }
                System.out.println();
            }
        }
    }
}
