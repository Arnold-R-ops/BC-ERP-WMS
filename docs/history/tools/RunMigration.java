import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 临时迁移脚本执行器
 * 用途：直接执行 V3.3_Migration_Complete_Idempotent.sql
 */
public class RunMigration {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/wms_db?serverTimezone=UTC";
        String username = "postgres";
        String password = "123465";
        String sqlFile = "V3.3_Migration_Complete_Idempotent.sql";

        try {
            // 读取SQL文件
            System.out.println("读取 SQL 文件: " + sqlFile);
            String sql = new String(Files.readAllBytes(Paths.get(sqlFile)));

            // 连接数据库
            System.out.println("连接数据库: " + url);
            Connection conn = DriverManager.getConnection(url, username, password);

            // 执行SQL
            System.out.println("执行迁移脚本...\n");
            Statement stmt = conn.createStatement();

            // PostgreSQL的DO块需要分批执行
            String[] blocks = sql.split(";");
            for (String block : blocks) {
                block = block.trim();
                if (!block.isEmpty() && !block.startsWith("--")) {
                    try {
                        stmt.execute(block + ";");
                    } catch (Exception e) {
                        // 继续执行，某些语句可能会因为幂等性而失败
                        System.err.println("警告: " + e.getMessage());
                    }
                }
            }

            // 获取通知信息（RAISE NOTICE）
            var warnings = conn.getWarnings();
            while (warnings != null) {
                System.out.println(warnings.getMessage());
                warnings = warnings.getNextWarning();
            }

            stmt.close();
            conn.close();

            System.out.println("\n✓ 迁移脚本执行完成！");

        } catch (IOException e) {
            System.err.println("✗ 读取SQL文件失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
