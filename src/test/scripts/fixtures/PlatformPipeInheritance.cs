// Windows-only, short-lived test fixture. No sockets, databases, services or file writes.
// It reproduces native inherited standard handles rather than Node's stdio forwarding.
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
public static class PlatformPipeInheritance {
    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
    struct StartupInfo {
        public int cb; public string reserved, desktop, title;
        public int x,y,xSize,ySize,xChars,yChars,fill,flags;
        public short show,reservedSize; public IntPtr reservedData,input,output,error;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct ProcessInfo { public IntPtr process,thread; public uint processId,threadId; }
    [DllImport("kernel32.dll")] static extern IntPtr GetStdHandle(int handle);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool SetHandleInformation(IntPtr handle,uint mask,uint flags);
    [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    static extern bool CreateProcess(string application,StringBuilder command,IntPtr processAttributes,IntPtr threadAttributes,
        bool inherit,uint flags,IntPtr environment,string directory,ref StartupInfo startup,out ProcessInfo info);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    public static int Main(string[] args) {
        if(args.Length==1 && args[0]=="hold") { Thread.Sleep(1500); return 0; }
        if(args.Length!=0) return 40;
        var startup=new StartupInfo(); startup.cb=Marshal.SizeOf(startup); startup.flags=0x100;
        startup.input=GetStdHandle(-10); startup.output=GetStdHandle(-11); startup.error=GetStdHandle(-12);
        foreach(var handle in new[]{startup.input,startup.output,startup.error})
            if(!SetHandleInformation(handle,1,1)) return 41;
        string executable=Process.GetCurrentProcess().MainModule.FileName;
        ProcessInfo child;
        if(!CreateProcess(executable,new StringBuilder("\""+executable+"\" hold"),IntPtr.Zero,IntPtr.Zero,true,
            0x08000000,IntPtr.Zero,null,ref startup,out child)) return 42; // CREATE_NO_WINDOW
        CloseHandle(child.thread); CloseHandle(child.process);
        Console.WriteLine("short-lived inherited-handle fixture started");
        return 0;
    }
}
