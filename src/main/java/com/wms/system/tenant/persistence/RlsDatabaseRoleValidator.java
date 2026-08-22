package com.wms.system.tenant.persistence;

import com.wms.system.tenant.config.TenancyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Refuses production startup when PostgreSQL credentials can bypass RLS. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RlsDatabaseRoleValidator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final TenancyProperties tenancyProperties;

    @Override
    public void run(ApplicationArguments args) {
        DatabaseRole role = jdbcTemplate.queryForObject("""
            SELECT rolname, rolsuper, rolbypassrls
            FROM pg_roles
            WHERE rolname = current_user
            """, (resultSet, rowNumber) -> new DatabaseRole(
                resultSet.getString("rolname"),
                resultSet.getBoolean("rolsuper"),
                resultSet.getBoolean("rolbypassrls")
            ));

        if (role == null) {
            throw new IllegalStateException("Current PostgreSQL role could not be inspected");
        }
        boolean unsafe = role.superuser() || role.bypassRls();
        if (unsafe && tenancyProperties.isRequireSafeDatabaseRole()) {
            throw new IllegalStateException(
                "PostgreSQL application role must be NOSUPERUSER and NOBYPASSRLS");
        }
        if (unsafe) {
            log.warn("PostgreSQL role {} can bypass RLS; allowed only for local migration compatibility",
                role.name());
        } else {
            log.info("PostgreSQL role {} is compatible with enforced row-level security", role.name());
        }
    }

    record DatabaseRole(String name, boolean superuser, boolean bypassRls) {
    }
}
