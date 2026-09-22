// Narrow b1 startup contract. No processes, filesystem changes or database activity on import.
import fs from 'node:fs';
import path from 'node:path';
export const SECURITY_POWERSHELL='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
export const SECURITY_SID='S-1-5-21-3770167052-703495069-1631994977-1001';
export const SECURITY_MODULE='C:/Windows/System32/WindowsPowerShell/v1.0/Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1';
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
export function privateAclInvocation(directory,ownerSid){
 if(!path.win32.isAbsolute(directory??'')||!canonical(directory).startsWith('d:/erp_wms/bc warehouse/2g/target/')||/[\r\n]/.test(directory)||ownerSid!==SECURITY_SID)throw Error('BROWSER_ACL_ARGUMENTS');
 const quote=s=>"'"+s.replaceAll("'","''")+"'";
 const text=fs.readFileSync(new URL('./platform-b1-private-acl.ps1',import.meta.url),'utf8');
 return {executable:SECURITY_POWERSHELL,args:['-NoProfile','-NonInteractive','-Command','& {\n'+text+'\n} -Directory '+quote(directory)+' -OwnerSid '+quote(ownerSid)]};
}
export function validatePrivateAcl(proof,directory,ownerSid){
 if(proof?.status!=='VERIFIED'||canonical(proof.directory)!==canonical(directory)||proof.ownerSid!==ownerSid
  ||proof.protected!==true||proof.canonical!==true||!Array.isArray(proof.rules)||proof.rules.length!==2)throw Error('BROWSER_ACL_PROOF');
 for(const sid of [ownerSid,'S-1-5-18']){
  const rows=proof.rules.filter(r=>r.sid===sid);
  if(rows.length!==1||rows[0].rights!==2032127||rows[0].type!=='Allow'||rows[0].inherited!==false||rows[0].inheritanceFlags!==3||rows[0].propagationFlags!==0)throw Error('BROWSER_ACL_RULE');
 }
 if(canonical(proof.module?.path)!==canonical(SECURITY_MODULE)||proof.module?.version!=='3.0.0.0'
  ||!canonical(proof.module?.assembly).startsWith('c:/windows/microsoft.net/assembly/gac_msil/microsoft.powershell.security/')
  ||![proof.module?.manifestSha256,proof.module?.assemblySha256].every(s=>/^[a-f0-9]{64}$/.test(s??'')))throw Error('BROWSER_ACL_MODULE');
 return proof;
}
export function classifyCurrentPid(proof,expected){
 if(!Number.isInteger(expected?.pid)||!expected.creationTicks||!expected.ownerSid||!expected.executable||!expected.commandLineSha256||proof?.pid!==expected.pid)throw Error('BROWSER_PID_EXPECTED_IDENTITY');
 if(proof.status==='ABSENT')return {relation:'CURRENTLY_ABSENT',pid:proof.pid,historicalClosureConfirmed:false};
 if(proof.status!=='AVAILABLE'||!proof.creationTicks||!proof.ownerSid||!proof.executable||!proof.commandLineSha256)throw Error('BROWSER_PID_INCOMPLETE');
 const same=proof.creationTicks===expected.creationTicks&&proof.ownerSid===expected.ownerSid&&canonical(proof.executable)===canonical(expected.executable)&&proof.commandLineSha256===expected.commandLineSha256;
 return {relation:same?'SAME_PROCESS_PRESENT':'DIFFERENT_PROCESS_OR_PID_REUSE',pid:proof.pid,historicalClosureConfirmed:false};
}
