package com.wms.browseracceptance;

import java.io.PrintStream;
import java.util.*;

/** Test-harness diagnostics only. Never serialize Throwable messages, configuration, SQL or requests. */
public final class PlatformBackendDiagnostic {
    public enum Stage { ARGUMENTS, CREDENTIALS_READ, ISOLATION_CONFIG, DATABASE_IDENTITY,
        SPRING_CONFIGURATION, SPRING_START, SYNTHETIC_ACCOUNT_PREPARATION, READY, WAIT_STOP, SPRING_CLOSE, COMPLETE }
    public static final String PREFIX="BROWSER_DIAGNOSTIC_V1 ";
    private static final int CHAIN=4, FRAMES=4, SUPPRESSED=2;
    private PlatformBackendDiagnostic() { }
    public static final class Trace {
        private final PrintStream output;
        private Stage stage=Stage.ARGUMENTS;
        public Trace(PrintStream output){this.output=Objects.requireNonNull(output);}
        public void enter(Stage next){stage=Objects.requireNonNull(next);emit("{\"v\":1,\"event\":\"STAGE\",\"stage\":\""+stage+"\"}");}
        public void failure(Throwable failure){emit(describe(stage,failure));}
        private void emit(String json){output.println(PREFIX+json);output.flush();}
    }
    private static String className(String name){
        if(name!=null && name.length()<=160 && name.matches("(?:java|javax|jdk|sun|com\\.wms|org\\.springframework|org\\.postgresql|com\\.zaxxer|org\\.hibernate|org\\.apache)\\.[A-Za-z0-9_.$]+"))return name;
        return "UNKNOWN";
    }
    private static String methodName(String name){return name!=null&&name.length()<=100&&name.matches("(?:[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>)")?name:"UNKNOWN";}
    private static String quote(String value){return "\""+value+"\"";} // All inputs are enums or validated identifiers.
    private static String node(Throwable t){
        var stack=t.getStackTrace();var frames=new ArrayList<String>();
        for(int i=0;i<Math.min(FRAMES,stack.length);i++){
            var f=stack[i];var owner=className(f.getClassName());
            var method=owner.equals("UNKNOWN")?"UNKNOWN":methodName(f.getMethodName());
            int line=f.getLineNumber();if(line < -2 || line > 1000000)line=-1;
            frames.add("{\"class\":"+quote(owner)+",\"method\":"+quote(method)+",\"line\":"+line+"}");
        }
        return "{\"type\":"+quote(className(t.getClass().getName()))+",\"frames\":["+String.join(",",frames)+"],\"framesTruncated\":"+(stack.length>FRAMES)+"}";
    }
    private static String reason(Throwable t){
        if(t==null || t.getClass()!=IllegalStateException.class)return "UNKNOWN";
        var stack=t.getStackTrace();if(stack.length==0)return "UNKNOWN";
        var owner=stack[0].getClassName();var message=t.getMessage(); // Exact comparisons only; never output this value.
        if(owner.equals("com.wms.browseracceptance.PlatformB1Isolation") && "R2 isolation precondition failed; STOP without fallback".equals(message))return "ISOLATION_PRECONDITION";
        if(!owner.equals("com.wms.browseracceptance.PlatformB1Harness"))return "UNKNOWN";
        if(message==null)return "UNKNOWN";
        return switch(message){
            case "B1_ARGUMENTS" -> "ARGUMENTS_REJECTED";
            case "B1_UNKNOWN_ACCOUNT_RESIDUE" -> "UNKNOWN_ACCOUNT_RESIDUE";
            case "B1_REFERENCE_ROLE_MISSING" -> "REFERENCE_ROLE_MISSING";
            case "B1_SEED_ROLE_FAILED" -> "SEED_ROLE_FAILED";
            case "B1_STOP_IDENTITY" -> "STOP_IDENTITY_MISMATCH";
            default -> "UNKNOWN";
        };
    }
    public static String describe(Stage stage,Throwable failure){
        Objects.requireNonNull(stage);Objects.requireNonNull(failure);
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var chain=new ArrayList<String>();Throwable cursor=failure;String code="UNKNOWN";
        while(cursor!=null && chain.size()<CHAIN && seen.add(cursor)){
            chain.add(node(cursor));if(code.equals("UNKNOWN"))code=reason(cursor);cursor=cursor.getCause();
        }
        var suppressed=failure.getSuppressed();var extra=new ArrayList<String>();
        for(int i=0;i<Math.min(SUPPRESSED,suppressed.length);i++)extra.add(node(suppressed[i]));
        return "{\"v\":1,\"event\":\"FAILURE\",\"stage\":"+quote(stage.name())+",\"reason\":"+quote(code)
            +",\"chain\":["+String.join(",",chain)+"],\"chainTruncated\":"+(cursor!=null)
            +",\"suppressed\":["+String.join(",",extra)+"],\"suppressedTruncated\":"+(suppressed.length>SUPPRESSED)+"}";
    }
}
