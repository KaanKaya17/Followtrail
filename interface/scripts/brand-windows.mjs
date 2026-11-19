import {readFile, writeFile} from 'node:fs/promises';
import {Data, NtExecutable, NtExecutableResource, Resource} from 'resedit';

// Build-time only: edit the package copy, never the installed Electron runtime.
export async function brandWindows(executable, icon, version) {
  const exe=NtExecutable.from(await readFile(executable));
  const resources=NtExecutableResource.from(exe);
  const groups=Resource.IconGroupEntry.fromEntries(resources.entries);
  const versions=Resource.VersionInfo.fromEntries(resources.entries);
  if (groups.length!==1 || versions.length!==1) throw Error('Unexpected Electron branding resources');
  const artwork=Data.IconFile.from(await readFile(icon));
  Resource.IconGroupEntry.replaceIconsForResource(resources.entries,groups[0].id,groups[0].lang,artwork.icons.map(item=>item.data));
  const info=versions[0];
  const languages=info.getAllLanguagesForStringValues();
  if (languages.length!==1) throw Error('Unexpected Electron version language');
  const numbers=version.split('.').map(Number);
  if (numbers.length!==3 || numbers.some(n=>!Number.isInteger(n)||n<0||n>65535)) throw Error('Invalid Windows package version');
  info.setFileVersion(...numbers,0);
  info.setProductVersion(...numbers,0);
  info.setStringValues(languages[0],{
    ProductName:'Followtrail',FileDescription:'Followtrail',CompanyName:'Followtrail',
    InternalName:'Followtrail',OriginalFilename:'Followtrail.exe',FileVersion:version,ProductVersion:version,
  });
  info.outputToResourceEntries(resources.entries);
  resources.outputResource(exe);
  await writeFile(executable,Buffer.from(exe.generate()));
}
