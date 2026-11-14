import sys
from PIL import Image
out, cols, tw = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
ims = [Image.open(p).convert("RGB") for p in sys.argv[4:]]
th = max(int(im.height * tw / im.width) for im in ims)
rows = (len(ims) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw + (cols - 1) * 6, rows * th + (rows - 1) * 6), (128, 128, 128))
for i, im in enumerate(ims):
    im = im.resize((tw, int(im.height * tw / im.width)), Image.LANCZOS)
    sheet.paste(im, ((i % cols) * (tw + 6), (i // cols) * (th + 6)))
sheet.save(out, quality=88)
