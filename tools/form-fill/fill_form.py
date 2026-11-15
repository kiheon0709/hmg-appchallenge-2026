import re,sys,os,shutil,struct
from xml.sax.saxutils import escape
docxml, md_path = sys.argv[1], sys.argv[2]
x=open(docxml,encoding='utf-8').read()
md=open(md_path,encoding='utf-8').read()

def runs(text, base_bold=False, size=None, italic=False, mono=False):
    text=re.sub(r'\[([^\]]+)\]\((https?://[^)]+)\)', r'\1 (\2)', text)
    text=text.replace('`','')
    out=[]
    for i,part in enumerate(re.split(r'\*\*', text)):
        if not part: continue
        bold = base_bold or (i%2==1)
        rpr=''
        if mono: rpr+='<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:eastAsia="맑은 고딕"/>'
        if bold: rpr+='<w:b/><w:bCs/>'
        if italic: rpr+='<w:i/>'
        if size: rpr+=f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>'
        rpr+='<w:lang w:eastAsia="ko-KR"/>'
        out.append(f'<w:r><w:rPr>{rpr}</w:rPr><w:t xml:space="preserve">{escape(part)}</w:t></w:r>')
    return ''.join(out)

def para(text, bold=False, indent=0, hanging=0, before=0, after=60, size=None, italic=False, mono=False, keep=False):
    ind=f'<w:ind w:left="{indent}" w:hanging="{hanging}"/>' if indent else ''
    kn=''
    return f'<w:p><w:pPr>{kn}<w:spacing w:before="{before}" w:after="{after}"/>{ind}</w:pPr>{runs(text,bold,size,italic,mono)}</w:p>'

def table(rows):
    n=len(rows[0]); W=9500
    lens=[max(13,min(60,max(len(re.sub(r'\*\*|`','',r[c])) for r in rows))) for c in range(n)]
    tot=sum(lens); ws=[int(W*l/tot) for l in lens]; ws[-1]=W-sum(ws[:-1])
    b=''.join(f'<w:{s} w:val="single" w:sz="4" w:space="0" w:color="8394A5"/>' for s in ('top','left','bottom','right','insideH','insideV'))
    t=f'<w:tbl><w:tblPr><w:tblW w:w="{W}" w:type="dxa"/><w:tblBorders>{b}</w:tblBorders><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>'+''.join(f'<w:gridCol w:w="{w}"/>' for w in ws)+'</w:tblGrid>'
    for ri,r in enumerate(rows):
        t+='<w:tr>'
        for c in range(n):
            sh='<w:shd w:val="clear" w:color="auto" w:fill="EEF2F6"/>' if ri==0 else ''
            t+=f'<w:tc><w:tcPr><w:tcW w:w="{ws[c]}" w:type="dxa"/>{sh}<w:tcMar><w:top w:w="50" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="50" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tcMar></w:tcPr>{para(r[c] if c<len(r) else "", bold=(ri==0), after=0, size=18)}</w:tc>'
        t+='</w:tr>'
    return t+'</w:tbl>'+para('',after=0,size=8)


IMGS=[]
def jpg_size(path):
    d=open(path,'rb').read(); i=2
    while i<len(d):
        if d[i]!=0xFF: i+=1; continue
        m=d[i+1]
        if m in (0xC0,0xC1,0xC2): h,w=struct.unpack('>HH',d[i+5:i+9]); return w,h
        i+=2+struct.unpack('>H',d[i+2:i+4])[0]
    raise ValueError(path)
def image(cap, path):
    w,h=jpg_size(path); n=len(IMGS)+1; rid=f'rIdImg{n}'; IMGS.append((rid,path,f'fig{n}.jpg'))
    cx=5900000; cy=int(cx*h/w)
    pic=(f'<w:p><w:pPr><w:spacing w:before="80" w:after="20"/><w:jc w:val="center"/></w:pPr><w:r><w:drawing>'
         f'<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{900+n}" name="fig{n}"/>'
         f'<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
         f'<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="{900+n}" name="fig{n}.jpg"/><pic:cNvPicPr/></pic:nvPicPr>'
         f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
         f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
         f'</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>')
    return pic+f'<w:p><w:pPr><w:spacing w:before="0" w:after="120"/><w:jc w:val="center"/></w:pPr>{runs(cap,size=17)}</w:p>'

def convert(lines):
    out=[]; i=0
    while i<len(lines):
        l=lines[i].rstrip('\n')
        if not l.strip() or l.strip()=='---': i+=1; continue
        mi=re.match(r'!\[([^\]]*)\]\(([^)]+)\)', l)
        if mi: out.append(image(mi.group(1), mi.group(2))); i+=1; continue
        if l.startswith('```'):
            i+=1
            while i<len(lines) and not lines[i].startswith('```'):
                out.append(para(lines[i].rstrip('\n'), after=0, size=14, mono=True)); i+=1
            i+=1; out.append(para('',after=0,size=8)); continue
        if l.startswith('|'):
            rows=[]
            while i<len(lines) and lines[i].startswith('|'):
                cells=[c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?',c) for c in cells): rows.append(cells)
                i+=1
            out.append(table(rows)); continue
        if l.startswith('### '): out.append(para(l[4:], bold=True, before=160, after=60, keep=True))
        elif l.startswith('> '): out.append(para(l[2:], italic=True, indent=300, after=80))
        elif re.match(r'^\s*- ', l):
            lvl=(len(l)-len(l.lstrip()))//2
            out.append(para('· '+l.lstrip()[2:], indent=260+300*lvl, hanging=180, after=40))
        elif re.match(r'^\s*\d+\. ', l):
            out.append(para(l.strip(), indent=320, hanging=320, after=40))
        else: out.append(para(l))
        i+=1
    return ''.join(out)

secs={}
parts=re.split(r'^## (\d)\. .*$', md, flags=re.M)
for k in range(1,len(parts),2): secs[int(parts[k])]=parts[k+1].splitlines()

spans=[]
for m in re.finditer(r'<w:sdt><w:sdtPr>(?:(?!</w:sdtPr>).)*?<w:alias w:val="(\d)\. ', x):
    j=m.start()
    ps=max(x.rfind('<w:p ',0,j), x.rfind('<w:p>',0,j)); pe=x.index('</w:p>',j)+6
    spans.append((int(m.group(1)),ps,pe))
assert [n for n,_,_ in spans]==list(range(1,9)), spans
lo=x.rfind('<w:tbl>',0,spans[0][1]); hi=spans[-1][2]
x=x[:lo]+x[lo:hi].replace('<w:cantSplit/>','')+x[hi:]
spans=[]
for m in re.finditer(r'<w:sdt><w:sdtPr>(?:(?!</w:sdtPr>).)*?<w:alias w:val="(\d)\. ', x):
    j=m.start()
    ps=max(x.rfind('<w:p ',0,j), x.rfind('<w:p>',0,j)); pe=x.index('</w:p>',j)+6
    spans.append((int(m.group(1)),ps,pe))
for n,ps,pe in reversed(spans):
    body=convert(secs[n]) if n!=8 else para('')
    if not body.endswith('</w:p>'): body+=para('')
    x=x[:ps]+body+x[pe:]
# app name lines
x=re.sub(r'(프로젝트명: )_+(</w:t></w:r>)(<w:r[^>]*>(<w:rPr>.*?</w:rPr>)?<w:t>_+</w:t></w:r>)*', r'\1타비 (TABI)\2', x)
x=re.sub(r'(프로젝트명: )_+', r'\1타비 (TABI)', x)
open(docxml,'w',encoding='utf-8').write(x)

root=os.path.dirname(os.path.dirname(os.path.abspath(docxml)))
if IMGS:
    os.makedirs(os.path.join(root,'word','media'),exist_ok=True)
    rp=os.path.join(root,'word','_rels','document.xml.rels'); r=open(rp,encoding='utf-8').read()
    add=''
    for rid,path,name in IMGS:
        shutil.copy(path, os.path.join(root,'word','media',name))
        add+=f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{name}"/>'
    open(rp,'w',encoding='utf-8').write(r.replace('</Relationships>',add+'</Relationships>'))
    cp=os.path.join(root,'[Content_Types].xml'); c=open(cp,encoding='utf-8').read()
    if 'Extension="jpg"' not in c:
        c=c.replace('<Default Extension="xml"','<Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="xml"',1)
        open(cp,'w',encoding='utf-8').write(c)
print('ok', len(IMGS), {k:len(v) for k,v in secs.items()})
