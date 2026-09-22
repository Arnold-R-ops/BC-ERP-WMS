package com.wms.browseracceptance;
import java.util.*;
/** No database: exercise the same generated isolation parser used by migration and runtime. */
public final class PlatformB1Contract {
    static int passed;
    static Properties valid(){var p=new Properties();p.setProperty("p3.b4.database-authorized","true");p.setProperty("p3.b4.migration-authorized","true");p.setProperty("p3.test.jdbcUrl",PlatformB1Isolation.url(PlatformB1Isolation.MAIN));p.setProperty("p3.b4.app-password","synthetic-contract-placeholder");p.setProperty("p3.b4.migrator-password","synthetic-contract-placeholder");p.setProperty("p3.b4.cluster-id","123456789012345678");return p;}
    static void check(boolean v){if(!v)throw new AssertionError("B1_CONTRACT");passed++;}
    static void refused(Runnable r){try{r.run();}catch(IllegalStateException ex){passed++;return;}throw new AssertionError("B1_EXPECTED_REFUSAL");}
    public static void main(String[] args){
        check(PlatformB1Isolation.url(PlatformB1Isolation.MAIN).equals("jdbc:postgresql://127.0.0.1:55459/p3_platform_browser_b4"));
        check(PlatformB1Isolation.settings(valid(),false,PlatformB1Isolation.MAIN).role().equals("p3_b4_app"));
        check(PlatformB1Isolation.settings(valid(),true,PlatformB1Isolation.MAIN).role().equals("p3_b4_migrator"));
        refused(()->PlatformB1Isolation.url("postgres"));
        refused(()->PlatformB1Isolation.url("p3_m1_platform_release_r11"));
        for(String key:List.of("p3.test.jdbcUrl","spring.datasource.url","spring.flyway.url")){var p=valid();p.setProperty(key,"jdbc:postgresql://127.0.0.1:5432/development");refused(()->PlatformB1Isolation.settings(p,false,PlatformB1Isolation.MAIN));}
        for(String key:List.of("p3.b4.database-authorized","p3.b4.app-password","p3.b4.cluster-id")){var p=valid();p.remove(key);refused(()->PlatformB1Isolation.settings(p,false,PlatformB1Isolation.MAIN));}
        var role=valid();role.setProperty("spring.datasource.username","postgres");refused(()->PlatformB1Isolation.settings(role,false,PlatformB1Isolation.MAIN));
        var jndi=valid();jndi.setProperty("spring.datasource.jndi-name","wrong");refused(()->PlatformB1Isolation.settings(jndi,false,PlatformB1Isolation.MAIN));
        check(PlatformB1Isolation.VERSIONS.equals(List.of("4.71","4.72","4.85","4.86","4.87","4.88","4.89","4.90")));
        check(PlatformB1Isolation.migrationFingerprint().matches("[a-f0-9]{64}"));
        check(!PlatformB1Isolation.settings(valid(),false,PlatformB1Isolation.MAIN).toString().contains("synthetic-contract-placeholder"));
        if(passed!=16)throw new AssertionError("B1_CONTRACT_COUNT");
        System.out.println("B1_NO_DATABASE_CONTRACTS_PASSED=16");
    }
}
