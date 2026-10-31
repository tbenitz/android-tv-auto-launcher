package dev.pirateflix.tv;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.HashMap;
import java.util.Map;

/** A controllable ExoPlayer for direct URLs legitimately exposed by the website.
 * Cannot decode opaque iframe pages, protected streams, or blob: URLs. */
@UnstableApi
public final class NativePlayerController {
    public interface Events { void onError(String detail); void onClose(); void onModeChanged(boolean pip); }
    private final Activity activity;
    private final FrameLayout root;
    private final Events events;
    private FrameLayout frame;
    private PlayerView video;
    private ExoPlayer player;
    private LinearLayout pipActions;
    private Button pause, expand;
    private TextView title;
    private String url;
    private boolean mini;
    private boolean focused;
    private int actionIndex;
    private final int dark = Color.rgb(6, 15, 27);
    public NativePlayerController(Activity activity, FrameLayout root, Events events) {
        this.activity=activity;this.root=root;this.events=events;
    }
    private int dp(int x) {return (int)(x*activity.getResources().getDisplayMetrics().density+.5f);}
    public boolean active(){return player!=null && frame!=null;}
    public boolean isMini(){return active() && mini;}
    public boolean isFullscreen(){return active() && !mini;}
    public boolean isFocused(){return focused && isMini();}
    public long duration(){return player==null?0:player.getDuration();}
    public long position(){return player==null?0:player.getCurrentPosition();}
    public boolean playable(String target){
        if(target==null||target.length()>4096)return false;
        try{
            Uri uri=Uri.parse(target);
            if(!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))return false;
            return uri.getHost()!=null && !uri.getHost().trim().isEmpty() && uri.getHost().indexOf('.')>0;
        }catch(Exception e){return false;}
    }
    public boolean play(String target, String referer) {
        if(!playable(target))return false;
        if(active() && target.equals(url)){ fullscreen();return true; }
        close(false);
        url=target;
        try{
            final Map<String,String> headers=new HashMap<>();
            if(referer!=null && referer.startsWith("https://"))headers.put("Referer",referer);
            DefaultHttpDataSource.Factory data=new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Android TV) PirateFlixTV/2.2")
                .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
            player=new ExoPlayer.Builder(activity)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(activity).setDataSourceFactory(data)).build();
            player.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException error){
                    events.onError("Native player couldn't open this source. Choose another server or use Website view.");
                    close(true);
                }
            });
            frame=new FrameLayout(activity);frame.setBackgroundColor(Color.BLACK);
            frame.setFocusable(true);
            video=new PlayerView(activity);video.setPlayer(player);video.setUseController(true);
            video.setControllerAutoShow(true);
            frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
            title=new TextView(activity);title.setText("PirateFlix TV  •  Native playback");
            title.setTextColor(Color.WHITE);title.setTextSize(12);title.setPadding(dp(10),dp(3),dp(10),dp(3));
            title.setBackgroundColor(0xC6112436);
            FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(-2,dp(23),Gravity.TOP|Gravity.LEFT);
            frame.addView(title,tl);
            pipActions=new LinearLayout(activity);pipActions.setOrientation(LinearLayout.HORIZONTAL);
            pipActions.setGravity(Gravity.CENTER);
            pipActions.setBackgroundColor(0xEE06111F);
            pause=new Button(activity);pause.setText("Pause / Play");pause.setAllCaps(false);
            expand=new Button(activity);expand.setText("Full screen");expand.setAllCaps(false);
            pipActions.addView(pause,new LinearLayout.LayoutParams(0,dp(48),1));
            pipActions.addView(expand,new LinearLayout.LayoutParams(0,dp(48),1));
            pause.setOnClickListener(v->toggle());expand.setOnClickListener(v->fullscreen());
            FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM);
            frame.addView(pipActions,ap);
            root.addView(frame,new FrameLayout.LayoutParams(-1,-1));
            player.setMediaItem(MediaItem.fromUri(target));player.prepare();player.play();
            fullscreen();frame.setAlpha(0f);frame.animate().alpha(1f).setDuration(300).start();
            return true;
        }catch(Exception ex){events.onError("Native playback error: "+ex.getClass().getSimpleName());close(false);return false;}
    }
    public void fullscreen(){
        if(!active())return;
        mini=false;focused=false;pipActions.setVisibility(View.GONE);
        video.setUseController(true);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1,Gravity.FILL);
        frame.setLayoutParams(lp);frame.bringToFront();
        frame.setBackgroundColor(Color.BLACK);
        events.onModeChanged(false);
    }
    public void minimize(){
        if(!active())return;
        mini=true;focused=false;actionIndex=0;
        video.setUseController(false);
        pipActions.setVisibility(View.GONE);
        int width=dp(272),height=dp(196);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height,Gravity.LEFT|Gravity.BOTTOM);
        lp.leftMargin=dp(12);lp.bottomMargin=dp(12);
        frame.setLayoutParams(lp);frame.bringToFront();
        frame.setBackgroundColor(dark);
        events.onModeChanged(true);
    }
    public void focus(){if(!isMini())return;focused=true;actionIndex=0;pipActions.setVisibility(View.VISIBLE);showFocus();}
    public void blur(){focused=false;if(pipActions!=null)pipActions.setVisibility(View.GONE);}
    private void showFocus(){
        if(pause==null)return;
        pause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(actionIndex==0?0xFF18C4B5:0xFF263C4C));
        expand.setBackgroundTintList(android.content.res.ColorStateList.valueOf(actionIndex==1?0xFF18C4B5:0xFF263C4C));
    }
    public void move(int dir){if(!isFocused())return;actionIndex=dir>0?1:0;showFocus();}
    public void confirm(){if(!isFocused())return;if(actionIndex==0)toggle();else fullscreen();}
    public void toggle(){if(player==null)return;if(player.isPlaying())player.pause();else player.play();}
    public boolean seek(long millis){
        if(player==null)return false;
        long duration=player.getDuration();
        if(duration<=0 || duration==androidx.media3.common.C.TIME_UNSET)return false;
        long target=Math.min(Math.max(player.getCurrentPosition()+millis,0),duration-100);
        player.seekTo(target);return true;
    }
    public boolean jumpPercent(int percent){
        if(player==null)return false;
        long d=player.getDuration();if(d<=0||d==androidx.media3.common.C.TIME_UNSET)return false;
        player.seekTo(d*percent/100);return true;
    }
    public boolean jumpTo(long millis){
        if(player==null)return false;
        long d=player.getDuration();if(d<=0||d==androidx.media3.common.C.TIME_UNSET)return false;
        player.seekTo(Math.min(d-100,Math.max(0,millis)));return true;
    }
    public String clock(){
        if(!active())return "";
        long pos=Math.max(0,position()/1000),dur=Math.max(0,duration()/1000);
        return fmt(pos)+" / "+fmt(dur);
    }
    private static String fmt(long t){return String.format(java.util.Locale.US,"%d:%02d:%02d",t/3600,(t/60)%60,t%60);}
    public void close(boolean notify){
        focused=false;mini=false;
        if(frame!=null){frame.animate().cancel();root.removeView(frame);}
        if(video!=null)video.setPlayer(null);
        if(player!=null){player.release();player=null;}
        frame=null;video=null;pause=null;expand=null;pipActions=null;title=null;url=null;
        if(notify)events.onClose();
    }
}
