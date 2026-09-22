package com.wms.browseracceptance;

/** JDK-only probe: never loads Spring, JDBC, the live harness, or credentials. */
public final class PlatformJvmEntryProbe {
    private static final boolean disabledAtClassInitialization =
            "false".equals(System.getProperty("spring.devtools.restart.enabled"));
    public static void main(String[] args) {
        boolean disabledAtEntry = "false".equals(System.getProperty("spring.devtools.restart.enabled"));
        System.out.println("{\"disabledAtClassInitialization\":" + disabledAtClassInitialization
                + ",\"disabledAtEntry\":" + disabledAtEntry + ",\"argumentCount\":" + args.length + "}");
        if (!disabledAtClassInitialization || !disabledAtEntry || args.length != 0) System.exit(2);
    }
}
