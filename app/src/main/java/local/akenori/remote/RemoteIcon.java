package local.akenori.remote;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, consistent line icons. Text labels remain the primary affordance. */
final class RemoteIcon extends Drawable {
    private final String name; private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG); private final int size;
    RemoteIcon(String name,int color,int size) { this.name=name; this.size=size; pen.setColor(color); pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(1.65f); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND); }
    private void line(Canvas c,float... xy) { Path p=new Path(); p.moveTo(xy[0],xy[1]); for(int i=2;i<xy.length;i+=2) p.lineTo(xy[i],xy[i+1]); c.drawPath(p,pen); }
    @Override public void draw(Canvas c) {
        int save=c.save(); Rect b=getBounds(); c.translate(b.left,b.top); c.scale(b.width()/24f,b.height()/24f);
        switch(name) {
            case "up": line(c,6,14,12,8,18,14); break;
            case "down": line(c,6,10,12,16,18,10); break;
            case "left": line(c,14,6,8,12,14,18); break;
            case "right": line(c,10,6,16,12,10,18); break;
            case "focus": line(c,8,3,3,3,3,8); line(c,16,3,21,3,21,8); line(c,3,16,3,21,8,21); line(c,16,21,21,21,21,16); c.drawCircle(12,12,4,pen); break;
            case "scale": c.drawRoundRect(new RectF(3,5,21,19),2,2,pen); line(c,8,14,15,8); line(c,11,8,15,8,15,12); break;
            case "keystone": line(c,7,4,17,4,22,20,2,20,7,4); line(c,8,12,16,12); break;
            case "align": c.drawRect(5,5,19,19,pen); for(int x:new int[]{5,19}) for(int y:new int[]{5,19}) { pen.setStyle(Paint.Style.FILL); c.drawCircle(x,y,2,pen); pen.setStyle(Paint.Style.STROKE); } break;
            case "rotate": c.drawRoundRect(new RectF(6,8,18,16),1,1,pen); c.drawArc(new RectF(2,2,22,22),200,100,false,pen); line(c,17,2,20,4,17,6); c.drawArc(new RectF(2,2,22,22),20,100,false,pen); line(c,7,22,4,20,7,18); break;
            case "home": line(c,3,11,12,3,21,11); line(c,6,10,6,21,10,21,10,15,14,15,14,21,18,21,18,10); break;
            case "back": line(c,9,6,3,12,9,18); line(c,3,12,20,12); break;
            case "settings": c.drawCircle(12,12,7,pen); c.drawCircle(12,12,2.5f,pen); for(int a=0;a<8;a++) { c.save(); c.rotate(a*45,12,12); line(c,12,2,12,5); c.restore(); } break;
            case "sound_minus": case "sound_plus": line(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9); line(c,16,12,22,12); if(name.endsWith("plus")) line(c,19,9,19,15); break;
            case "screen": c.drawRoundRect(new RectF(2,4,22,18),2,2,pen); line(c,8,22,16,22); line(c,12,18,12,22); break;
            case "wifi": c.drawArc(new RectF(1,4,23,24),220,100,false,pen); c.drawArc(new RectF(5,9,19,23),220,100,false,pen); c.drawCircle(12,18,1,pen); break;
            case "vibration": c.drawRoundRect(new RectF(8,4,16,20),2,2,pen); line(c,4,8,2,10,4,12,2,14,4,16); line(c,20,8,22,10,20,12,22,14,20,16); break;
            case "refresh": c.drawArc(new RectF(4,4,20,20),30,300,false,pen); line(c,20,5,20,10,15,10); break;
            default: c.drawCircle(12,12,8,pen);
        }
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { pen.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { pen.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
}
