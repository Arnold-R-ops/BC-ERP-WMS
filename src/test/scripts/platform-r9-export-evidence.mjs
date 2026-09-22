// Narrow allowlisted evidence projection; never persists arbitrary stdout or authentication objects.
import path from 'node:path';
export function exportEvidence(output,root){
 const result={directories:[],bindings:[],diagnostics:[],unavailable:[]};
 const normal=p=>path.resolve(p).replaceAll('\\','/');
 const base=normal(root),owner='[A-Za-z0-9_.$:-]{1,240}',uuid='[a-f0-9-]{36}';
 for(const line of output.split(/\r?\n/)){
  if(!line.startsWith('PLATFORM_EXPORT_'))continue;
  let m;
  if((m=line.match(new RegExp('^PLATFORM_EXPORT_DIRECTORY owner=('+owner+') root=(.+) state=(CREATED|REGISTERED)$')))){
   const directory=normal(m[2]);if(path.dirname(directory).replaceAll('\\','/')!==base||!path.basename(directory).startsWith('owned-export-'))throw Error('Export directory outside owned batch');
   result.directories.push({owner:m[1],directory,state:m[3]});
  }else if((m=line.match(new RegExp('^PLATFORM_EXPORT_DIRECTORY owner=('+owner+') state=(CLEANED|RETAINED_WORKER_ACTIVE|DEFERRED_CLEANUP_FAILED)(?: type=([A-Za-z0-9_$]+))?$'))))result.directories.push({owner:m[1],state:m[2],type:m[3]??null});
  else if((m=line.match(new RegExp('^PLATFORM_EXPORT_FILE owner=('+owner+') job=('+uuid+') file=(.+) state=BOUND$')))){
   const file=normal(m[3]);if(path.basename(file)!==m[2]+'.zip'||!result.directories.some(d=>d.owner===m[1]&&d.directory===path.dirname(file).replaceAll('\\','/')&&d.state==='REGISTERED'))throw Error('Unregistered export file');
   result.bindings.push({owner:m[1],job:m[2],file});
  }else if((m=line.match(/^PLATFORM_EXPORT_FIXTURE_DIAGNOSTIC job=([a-f0-9-]{36}) actor=(\d+) tenant=(\d+) state=(PENDING|RUNNING|FAILED|COMPLETED|EXPIRED|UNRECOGNIZED_REDACTED) publicErrorCode=(NONE|PLATFORM_EXPORT_FAILED|UNRECOGNIZED_REDACTED) matchedFailureAudits=(\d+) auditExceptionTypes=\[([A-Za-z0-9_$, ]*)\] malformedCandidateAudits=(\d+) underlyingErrorCode=NOT_CAPTURED$/)))result.diagnostics.push({job:m[1],actor:Number(m[2]),tenant:Number(m[3]),state:m[4],publicErrorCode:m[5],matchedFailureAudits:Number(m[6]),auditExceptionTypes:m[7]?m[7].split(', '):[],malformedCandidateAudits:Number(m[8]),underlyingErrorCode:'NOT_CAPTURED'});
  else if((m=line.match(/^PLATFORM_EXPORT_DIAGNOSIS_UNAVAILABLE actor=(\d+) tenant=(\d+) type=([A-Za-z0-9_$]+)$/)))result.unavailable.push({actor:Number(m[1]),tenant:Number(m[2]),type:m[3]});
  else throw Error('Malformed export evidence; raw content withheld');
 }
 result.closeoutComplete=result.unavailable.length===0&&!result.directories.some(d=>['RETAINED_WORKER_ACTIVE','DEFERRED_CLEANUP_FAILED'].includes(d.state))
  &&result.directories.filter(d=>d.state==='CREATED').length===result.directories.filter(d=>d.state==='CLEANED').length;
 return result;
}
