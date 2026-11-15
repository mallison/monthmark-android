package app.monthmark;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;
import java.util.UUID;
import app.monthmark.core.Countdown;

/** Local image import and bounded backgrounds shared by cards and RemoteViews. */
final class Appearance {
    static final int[] COLORS={0xff8ce2b4,0xffbdabf6,0xffffbf88,0xff8ccafa,0xfff6a9c5,
            0xfff7d46b,0xff70d6d0,0xfffc9f8f,0xff9daef9,0xffc6dc7a};
    static final String[] COLOR_NAMES={"Mint","Lavender","Peach","Sky","Rose","Sunshine","Teal","Coral","Periwinkle","Lime"};
    static final String[] ICONS={"✨","💷","✈️","🎂","🎉","💍","❤️","🎄","🎓","🏡","🎵","🎮","🌴","🚀","🏃","👶","☕","🍕","🐾","📅"};
    static final String[] ICON_NAMES={"Sparkles","Payday","Travel","Birthday","Celebration","Wedding","Love","Christmas","Graduation","Home","Music","Games","Holiday","Adventure","Fitness","Baby","Coffee","Food","Pets","Calendar"};
    static int color(Countdown event) { return COLORS[Math.floorMod(event.color,COLORS.length)]; }
    static boolean photo(Context c,String ref) { File f=file(c,ref); return f!=null && f.isFile(); }

    private static File file(Context c,String ref) {
        if(ref==null || !ref.startsWith("photo:")) return null;
        String name=ref.substring(6);
        if(!name.matches("[a-f0-9-]+\\.jpg")) return null;
        return new File(new File(c.getFilesDir(),"backgrounds"),name);
    }

    static String importPhoto(Context context,Uri uri) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,bounds); }
        if(bounds.outWidth<=0 || bounds.outHeight<=0) throw new IOException("This image format is not supported");
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=1;
        while(Math.max(bounds.outWidth/options.inSampleSize,bounds.outHeight/options.inSampleSize)>1600) options.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)) { bitmap=BitmapFactory.decodeStream(in,null,options); }
        if(bitmap==null) throw new IOException("Could not open this image");
        try {
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try(InputStream in=context.getContentResolver().openInputStream(uri)) {
                if(in!=null) orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            } catch(IOException ignored) { /* Some providers offer formats without EXIF. */ }
            Matrix matrix=new Matrix();
            switch(orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1,-1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1,1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
                default: break;
            }
            if(!matrix.isIdentity()) {
                Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if(rotated!=bitmap) { bitmap.recycle(); bitmap=rotated; }
            }
            int edge=Math.max(bitmap.getWidth(),bitmap.getHeight());
            if(edge>1280) {
                Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*1280/edge),Math.max(1,bitmap.getHeight()*1280/edge),true);
                if(scaled!=bitmap) { bitmap.recycle(); bitmap=scaled; }
            }
            String ref="photo:"+UUID.randomUUID()+".jpg";
            File destination=file(context,ref); File directory=destination.getParentFile();
            if(directory==null || (!directory.isDirectory() && !directory.mkdirs())) throw new IOException("Could not save the background");
            try(FileOutputStream out=new FileOutputStream(destination)) {
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,out)) throw new IOException("Could not save the background");
            } catch(IOException error) { destination.delete(); throw error; }
            return ref;
        } finally { bitmap.recycle(); }
    }

    static void deleteIfUnused(Context c,String ref) {
        File f=file(c,ref); if(f==null) return;
        for(Countdown event:new Store(c).all()) if(event.background.equals(ref)) return;
        f.delete();
    }

    static Drawable background(Context c,Countdown event) {
        File f=file(c,event.background); Bitmap image=null;
        if(f!=null && f.isFile()) {
            BitmapFactory.Options size=new BitmapFactory.Options(); size.inJustDecodeBounds=true; BitmapFactory.decodeFile(f.getAbsolutePath(),size);
            BitmapFactory.Options sample=new BitmapFactory.Options(); sample.inSampleSize=1;
            while(Math.max(size.outWidth/sample.inSampleSize,size.outHeight/sample.inSampleSize)>720) sample.inSampleSize*=2;
            image=BitmapFactory.decodeFile(f.getAbsolutePath(),sample);
        }
        return new Background(color(event),image,Ui.dp(c,24));
    }

    static Bitmap widgetBackground(Context c,Countdown event,int widthDp,int heightDp) {
        int pxWidth=Math.max(1,Ui.dp(c,widthDp)),pxHeight=Math.max(1,Ui.dp(c,heightDp));
        float scale=Math.min(1f,480f/Math.max(pxWidth,pxHeight));
        int w=Math.max(1,Math.round(pxWidth*scale)),h=Math.max(1,Math.round(pxHeight*scale));
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Drawable background=background(c,event); background.setBounds(0,0,pxWidth,pxHeight);
        Canvas canvas=new Canvas(result); canvas.scale(scale,scale); background.draw(canvas);
        return result;
    }

    private static final class Background extends Drawable {
        private final int color; private final Bitmap photo; private final float radius;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Background(int color,Bitmap photo,float radius) { this.color=color; this.photo=photo; this.radius=radius; }
        @Override public void draw(Canvas canvas) {
            Rect bounds=getBounds(); if(bounds.isEmpty()) return;
            RectF rect=new RectF(bounds); Path clip=new Path(); clip.addRoundRect(rect,radius,radius,Path.Direction.CW);
            int save=canvas.save(); canvas.clipPath(clip);
            if(photo!=null) {
                float scale=Math.max(rect.width()/photo.getWidth(),rect.height()/photo.getHeight());
                float w=photo.getWidth()*scale,h=photo.getHeight()*scale;
                paint.setShader(null); canvas.drawBitmap(photo,null,new RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),paint);
                paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,new int[]{0x55101613,0xbb101613},null,Shader.TileMode.CLAMP));
            } else {
                int light=Color.rgb((Color.red(color)*4+255)/5,(Color.green(color)*4+255)/5,(Color.blue(color)*4+255)/5);
                paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,new int[]{light,color},null,Shader.TileMode.CLAMP));
            }
            canvas.drawRect(rect,paint); paint.setShader(null); canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
