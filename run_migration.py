"""
V3.3 迁移脚本执行器
使用 psycopg2 执行 PostgreSQL 迁移脚本
"""
import psycopg2
from psycopg2 import sql

# 数据库连接配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'wms_db',
    'user': 'postgres',
    'password': '123465'
}

SQL_FILE = 'V3.3_Migration_Complete_Idempotent.sql'

def execute_migration():
    try:
        # 读取SQL文件
        print(f"📖 读取 SQL 文件: {SQL_FILE}")
        with open(SQL_FILE, 'r', encoding='utf-8') as f:
            sql_script = f.read()

        # 连接数据库
        print(f"🔌 连接数据库: {DB_CONFIG['database']}")
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = True  # 自动提交，支持DDL
        cursor = conn.cursor()

        # 执行SQL脚本
        print("⚙️  执行迁移脚本...\n")
        cursor.execute(sql_script)

        # 获取所有通知（RAISE NOTICE）
        notices = conn.notices
        for notice in notices:
            print(notice.strip())

        # 验证查询
        print("\n📊 执行最终验证...")
        cursor.execute("""
            SELECT
                'product_spu' AS table_name,
                COUNT(*) AS row_count
            FROM product_spu
            UNION ALL
            SELECT
                'products (spu_id not null)' AS table_name,
                COUNT(*) AS row_count
            FROM products
            WHERE spu_id IS NOT NULL
            UNION ALL
            SELECT
                'inventory_batch (location_code not null)' AS table_name,
                COUNT(*) AS row_count
            FROM inventory_batch
            WHERE location_code IS NOT NULL;
        """)

        results = cursor.fetchall()
        print("\n数据统计:")
        for row in results:
            print(f"  {row[0]}: {row[1]} 行")

        cursor.close()
        conn.close()

        print("\n✅ 迁移脚本执行完成！")
        return True

    except FileNotFoundError:
        print(f"❌ 错误: 找不到文件 {SQL_FILE}")
        return False
    except psycopg2.Error as e:
        print(f"❌ 数据库错误: {e}")
        return False
    except Exception as e:
        print(f"❌ 执行失败: {e}")
        return False

if __name__ == '__main__':
    success = execute_migration()
    exit(0 if success else 1)
