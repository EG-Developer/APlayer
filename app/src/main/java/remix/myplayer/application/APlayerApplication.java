package remix.myplayer.application;

import android.content.Context;
import android.content.Intent;

import com.facebook.common.internal.Supplier;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.cache.MemoryCacheParams;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.umeng.analytics.MobclickAgent;
import com.umeng.socialize.Config;
import com.umeng.socialize.PlatformConfig;
import com.umeng.socialize.UMShareAPI;

import cn.bmob.v3.Bmob;
import remix.myplayer.db.DBManager;
import remix.myplayer.db.DBOpenHelper;
import remix.myplayer.listener.LockScreenListener;
import remix.myplayer.service.MusicService;
import remix.myplayer.service.TimerService;
import remix.myplayer.theme.ThemeStore;
import remix.myplayer.util.ColorUtil;
import remix.myplayer.util.CommonUtil;
import remix.myplayer.util.CrashHandler;
import remix.myplayer.util.DiskCache;
import remix.myplayer.util.ErrUtil;
import remix.myplayer.util.Global;
import remix.myplayer.util.MediaStoreUtil;
import remix.myplayer.util.PermissionUtil;
import remix.myplayer.util.PlayListUtil;

/**
 * Created by taeja on 16-3-16.
 */

/**
 * 错误收集与上报
 */
public class APlayerApplication extends android.app.Application {
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        //字体
        CommonUtil.setFontSize(this);
        //友盟分享
        UMShareAPI.get(this);
        Config.DEBUG = true;
        //bomb
        Bmob.initialize(this, "0c070110fffa9e88a1362643fb9d4d64");
        //禁止默认的页面统计方式
        MobclickAgent.openActivityDurationTrack(false);
        //异常捕获
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(this);
        startService(new Intent(this, MusicService.class));
        //定时
        startService(new Intent(this, TimerService.class));
        //监听锁屏
        new LockScreenListener(getApplicationContext()).beginListen();
        //友盟异常捕获
        MobclickAgent.setCatchUncaughtExceptions(true);
        MobclickAgent.setDebugMode(true);
        initTheme();
        initUtil();
        loadData();

    }

    /**
     * 读取歌曲id列表与播放队列
     */
    public void loadData() {
        new Thread() {
            @Override
            public void run() {
                //读取sd卡歌曲id
                Global.mAllSongList = MediaStoreUtil.getAllSongsId();

                //读取播放队列
//                if(!SPUtil.getValue(mContext, "Setting", "First", true)){
//                    Global.mPlayQueueID = SPUtil.getValue(mContext,"Setting","PlayQueueID",Global.mPlayQueueID);
//                    Global.mPlayQueue = PlayListUtil.getIDList(Global.mPlayQueueID);
//                }
//                Global.mPlayQueue = XmlUtil.getPlayQueue();
//                Global.setPlayQueue(Global.mPlayQueue == null || Global.mPlayQueue.size() == 0 ?
//                                        Global.mAllSongList : Global.mPlayQueue);
                //读取播放列表
//                Global.mPlayList = XmlUtil.getPlayList("playlist.xml");
            }
        }.start();
    }

    private void initUtil() {
        //初始化工具类
        DBManager.initialInstance(new DBOpenHelper(mContext));
        PermissionUtil.setContext(mContext);
        MediaStoreUtil.setContext(mContext);
        CommonUtil.setContext(mContext);
        ErrUtil.setContext(mContext);
        DiskCache.init(mContext);
        ColorUtil.setContext(mContext);
        PlayListUtil.setContext(mContext);
        final int cacheSize = (int)(Runtime.getRuntime().maxMemory() / 8);
        ImagePipelineConfig config = ImagePipelineConfig.newBuilder(this)
                .setBitmapMemoryCacheParamsSupplier(new Supplier<MemoryCacheParams>() {
                    @Override
                    public MemoryCacheParams get() {
                        return new MemoryCacheParams(cacheSize, Integer.MAX_VALUE,cacheSize, Integer.MAX_VALUE, Integer.MAX_VALUE);
                    }
                })
                .build();
        Fresco.initialize(this,config);

    }

    /**
     * 初始化主题
     */
    private void initTheme() {
        ThemeStore.THEME_MODE = ThemeStore.loadThemeMode();
        ThemeStore.THEME_COLOR = ThemeStore.loadThemeColor();

        ThemeStore.MATERIAL_COLOR_PRIMARY = ThemeStore.getMaterialPrimaryColorRes();
        ThemeStore.MATERIAL_COLOR_PRIMARY_DARK = ThemeStore.getMaterialPrimaryDarkColorRes();
    }

    public static Context getContext(){
        return mContext;
    }

    static {
        PlatformConfig.setWeixin("wx10775467a6664fbb","8a64ff1614ffe8d8dd4f8cc794f3c4f1");
    }
}