# Read-only DEX inspection for pinned Bronze packaging; no recovered Kotlin source.

import struct
class Dex:
 def __init__(self,b):
  self.b=b
  def table(off):return struct.unpack_from('<II',b,off)
  size,off=table(56);self.strings=[]
  for i in range(size):
   pos=self.u32(off+i*4);_,pos=self.uleb(pos);end=b.index(0,pos);self.strings.append(b[pos:end].decode('utf-8','replace'))
  size,off=table(64);self.types=[self.strings[self.u32(off+i*4)] for i in range(size)]
  size,off=table(72);self.protos=[]
  for i in range(size):
   short,ret,args=struct.unpack_from('<III',b,off+i*12)
   params=[] if not args else [self.types[self.u16(args+4+j*2)] for j in range(self.u32(args))]
   self.protos.append('('+''.join(params)+')'+self.types[ret])
  size,off=table(80);self.fields=[]
  for i in range(size):
   cls,typ,n=struct.unpack_from('<HHI',b,off+i*8);self.fields.append(self.types[cls]+'.'+self.strings[n]+':'+self.types[typ])
  size,off=table(88);self.methods=[]
  for i in range(size):
   cls,proto,n=struct.unpack_from('<HHI',b,off+i*8);self.methods.append(self.types[cls]+'.'+self.strings[n]+self.protos[proto])
  size,off=table(96);self.code={}
  for i in range(size):
   vals=struct.unpack_from('<8I',b,off+i*32);cls=vals[0];source=vals[4];data=vals[6]
   if not data:continue
   sf,ins,dm,vm,pos=self.counts(data,4)
   for j in range(sf+ins):_,pos=self.uleb(pos);_,pos=self.uleb(pos)
   for count in [dm,vm]:
    idx=0
    for j in range(count):
     diff,pos=self.uleb(pos);idx+=diff;flags,pos=self.uleb(pos);code,pos=self.uleb(pos)
     if code:self.code[idx]=(code,self.strings[source] if source!=0xffffffff else '?')
 def u32(self,p):return struct.unpack_from('<I',self.b,p)[0]
 def u16(self,p):return struct.unpack_from('<H',self.b,p)[0]
 def uleb(self,p):
  v=0;shift=0
  while True:
   c=self.b[p];p+=1;v|=(c&127)<<shift
   if not c&128:return v,p
   shift+=7
 def counts(self,p,n):
  r=[]
  for _ in range(n):v,p=self.uleb(p);r.append(v)
  return (*r,p)
 def instructions(self,idx):
  code,sf=self.code[idx];n=self.u32(code+12);raw=[self.u16(code+16+i*2) for i in range(n)]
  widths={0x02:2,0x03:3,0x05:2,0x06:3,0x08:2,0x09:3,0x13:2,0x14:3,0x15:2,0x16:2,0x17:3,0x18:5,0x19:2,0x1a:2,0x1b:3,0x1c:2,0x1f:2,0x20:2,0x22:2,0x23:2,0x24:3,0x25:3,0x26:3,0x29:2,0x2a:3,0x2b:3,0x2c:3}
  for a,z,w in [(0x2d,0x3d,2),(0x44,0x6d,2),(0x6e,0x72,3),(0x74,0x78,3),(0x90,0xaf,2),(0xd0,0xe2,2)]:widths.update({i:w for i in range(a,z+1)})
  p=0;out=[]
  while p<n:
   op=raw[p]&255;w=widths.get(op,1);arg=''
   if op==0 and raw[p]!=0:
    kind=raw[p]>>8
    if kind==1:w=4+raw[p+1]*2
    elif kind==2:w=2+raw[p+1]*4
    elif kind==3:w=4+(raw[p+1]*(raw[p+2]|raw[p+3]<<16)+1)//2
   elif op in [0x1a,0x1b]:
    idx2=raw[p+1] if op==0x1a else raw[p+1]|raw[p+2]<<16;arg='const-string v'+str(raw[p]>>8)+' '+repr(self.strings[idx2])
   elif op in [0x1c,0x1f,0x20,0x22,0x23]:arg='type '+self.types[raw[p+1]]
   elif 0x52<=op<=0x6d:arg='field '+self.fields[raw[p+1]]
   elif op in range(0x6e,0x73) or op in range(0x74,0x79):arg='invoke '+self.methods[raw[p+1]]
   elif op in range(0x32,0x3e):arg='if -> '+hex(p+struct.unpack('<h',struct.pack('<H',raw[p+1]))[0])
   elif op==0x0e:arg='return-void'
   elif op in [0x0f,0x10,0x11]:arg='return v'+str(raw[p]>>8)
   out.append((p,op,arg,raw[p:p+w]));p+=w
  return out
 def show(self,needle,detail=False):
  for idx,(code,sf) in self.code.items():
   if needle not in self.methods[idx]:continue
   print('METHOD',self.methods[idx],'source',sf,'code_offset',hex(code))
   for p,op,arg,raw in self.instructions(idx):
    if arg or detail:print(hex(p),hex(op),arg,raw if detail else '')

