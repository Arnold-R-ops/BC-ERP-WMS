package com.wms.browseracceptance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JDK-only synthetic process. Does not load the harness, Spring, JDBC, or a network client. */
public final class PlatformBackendDiagnosticProbe {
    static final String SENSITIVE="password=SYNTHETIC_PASSWORD_DO_NOT_PERSIST; jwt=eyJsynthetic.payload.signature; mfa=SYNTHETIC_MFA; recovery=SYNTHETIC_RECOVERY; SQL SELECT private_value";
    static Throwable failure(String mode){
        var error=new IllegalStateException(mode.equals("known")?"B1_REFERENCE_ROLE_MISSING":SENSITIVE);
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.wms.browseracceptance.PlatformB1Harness","seed","PlatformB1Harness.java",127)});
        if(mode.equals("deep")){
            Throwable t=error;for(int i=0;i<10;i++)t=new RuntimeException(SENSITIVE,t);return t;
        }
        if(mode.equals("cycle")){
            var second=new IllegalArgumentException(SENSITIVE);error.initCause(second);second.initCause(error);return error;
        }
        if(mode.equals("hostileframes"))error.setStackTrace(new StackTraceElement[]{new StackTraceElement(SENSITIVE,SENSITIVE,SENSITIVE,Integer.MAX_VALUE)});
        error.addSuppressed(new IOException(SENSITIVE));return error;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)System.exit(2);
        var stage=PlatformBackendDiagnostic.Stage.valueOf(args[0]);var mode=args[1];
        if(!Set.of("known","unknown","deep","cycle","hostileframes","noisy").contains(mode))System.exit(2);
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        var trace=new PlatformBackendDiagnostic.Trace(stream);trace.enter(stage);trace.failure(failure(mode));
        // Non-protocol content is deliberately emitted only by this JDK-only probe; the real receiver must drop it.
        if(mode.equals("noisy"))System.out.println(SENSITIVE);
        var output=bytes.toByteArray();
        for(int at=0;at<output.length;at+=7){System.err.write(output,at,Math.min(7,output.length-at));System.err.flush();}
        System.exit(1); // Synthetic failure is expected, never mistaken for a successful service startup.
    }
}
