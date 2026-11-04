const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');

function backend(){
  const sheets=new Map();let id=0;
  const book={getSheetByName:name=>sheets.get(name),insertSheet:name=>{
    const rows=[];const sh={getParent:()=>book,appendRow:row=>rows.push(row),setFrozenRows:()=>{},getLastRow:()=>rows.length,
      getDataRange:()=>({getValues:()=>rows}),getRange:()=>({insertCheckboxes:()=>{}}),rows};sheets.set(name,sh);return sh;
  }};
  const reports=book.insertSheet('Reportes');
  const context=vm.createContext({console,Date,Utilities:{getUuid:()=>`id${++id}`}});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../backend/Code.gs'),'utf8'),context);
  context.sheet_=()=>reports;context.json_=value=>value;
  return {context,sheets,reports};
}
test('old Android suggestions route to their own sheet',()=>{
  const {context:c,sheets}=backend();let reportCalled=false;c.report_=()=>{reportCalled=true;};
  const result=c.doPost({postData:{contents:JSON.stringify({action:'report',kind:'Sugerencia para la tienda',game:'Demo',detail:'https://example.org/game'})}});
  assert.equal(result.ok,true);assert.equal(reportCalled,false);assert.equal(sheets.get('Sugerencias').rows.length,2);
});
test('private and unapproved compatibility reports never enter public output',()=>{
  const {context:c,sheets}=backend();
  c.compatibilitySubmit_({game:'demo',version:'1',engine:'classic',state:'jugable',hours:2,note:'Mapa funciona'});
  const rows=sheets.get('Compatibilidad').rows;
  assert.equal(rows[1][7],false);assert.equal(c.compatibilityFeed_().compatibility.length,0);
  rows[1][7]=true;
  const result=c.compatibilityFeed_().compatibility;
  assert.equal(result.length,1);assert.equal(result[0].game,'demo');assert.equal(result[0].votes.jugable,1);assert.equal(result[0].hours,2);
  assert.deepEqual(Object.keys(result[0]).sort(),['engine','game','hours','note','reports','version','votes']);
  assert.equal(JSON.stringify(result).includes('scripts'),false);
});
test('different versions and engines are kept separate',()=>{
  const {context:c,sheets}=backend();
  for(const [version,engine] of [['1','classic'],['2','classic'],['2','modern']])c.compatibilitySubmit_({game:'demo',version,engine,state:'con_fallas',hours:1,note:''});
  for(const row of sheets.get('Compatibilidad').rows.slice(1))row[7]=true;
  assert.equal(c.compatibilityFeed_().compatibility.length,3);
});
test('formula-looking content is escaped before reaching Sheets',()=>{
  const {context:c,sheets}=backend();
  c.compatibilitySubmit_({game:'demo',version:'=IMPORTXML("bad")',engine:'classic',state:'no_abre',hours:0,note:'=HYPERLINK("bad")'});
  const row=sheets.get('Compatibilidad').rows[1];assert.equal(row[2][0],"'");assert.equal(row[6][0],"'");assert.equal(row[7],false);
});
test('invalid hours, states and identifiers are rejected',()=>{
  const {context:c}=backend();
  const valid={game:'demo',version:'1',engine:'auto',state:'jugable',hours:1,note:''};
  for(const invalid of [{game:'../../private'},{state:'verified'},{hours:Infinity},{hours:-1}])assert.throws(()=>c.compatibilitySubmit_({...valid,...invalid}));
});
test('migration is repeatable and preserves original reports',()=>{
  const {context:c,sheets,reports}=backend();reports.rows.push(new Array(13).fill('header'));
  let row=new Array(13).fill('');row[0]='fecha';row[1]='1.0';row[4]='Demo';row[6]='Sugerencia para la tienda';row[10]='https://example.org';row[12]='suggestion-1';reports.rows.push(row);
  assert.equal(c.migrateSuggestions(),1);assert.equal(c.migrateSuggestions(),0);
  assert.equal(reports.rows.length,2);assert.equal(sheets.get('Sugerencias').rows.length,2);
});
