package remix.myplayer.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.v4.view.ViewPager;
import android.support.v7.graphics.Palette;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.PopupMenu;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.tumblr.backboard.performer.Performer;
import com.umeng.analytics.MobclickAgent;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import remix.myplayer.R;
import remix.myplayer.adapter.PagerAdapter;
import remix.myplayer.fragment.CoverFragment;
import remix.myplayer.fragment.LrcFragment;
import remix.myplayer.fragment.RecordFragment;
import remix.myplayer.listener.AudioPopupListener;
import remix.myplayer.lrc.LrcView;
import remix.myplayer.model.MP3Item;
import remix.myplayer.service.MusicService;
import remix.myplayer.theme.Theme;
import remix.myplayer.theme.ThemeStore;
import remix.myplayer.ui.customview.AudioViewPager;
import remix.myplayer.ui.dialog.PlayQueueDialog;
import remix.myplayer.util.ColorUtil;
import remix.myplayer.util.CommonUtil;
import remix.myplayer.util.Constants;
import remix.myplayer.util.DensityUtil;
import remix.myplayer.util.Global;
import remix.myplayer.util.MediaStoreUtil;
import remix.myplayer.util.SPUtil;
import remix.myplayer.util.StatusBarUtil;
import remix.myplayer.util.ToastUtil;

/**
 * Created by Remix on 2015/12/1.
 */

/**
 * 播放界面
 */
public class AudioHolderActivity extends BaseActivity implements MusicService.Callback{
    private static final String TAG = "AudioHolderActivity";
    public static AudioHolderActivity mInstance = null;
    //是否正在运行
    public static boolean mIsRunning;
    //上次选中的Fragment
    private int mPrevPosition = 1;
    //是否播放的标志变量
    public static boolean mIsPlay = false;
    //第一次启动的标志变量
    private boolean mFistStart = true;
    //是否正在拖动进度条
    public static boolean mIsDragSeekBar = false;
    private Palette.Swatch mNewSwatch = null;
    private Palette.Swatch mOldSwatch = null;
    //顶部信息
    @BindView(R.id.top_title)
    TextView mTopTitle;
    @BindView(R.id.top_detail)
    TextView mTopDetail;
    //隐藏按钮
    @BindView(R.id.top_hide)
    ImageButton mTopHide;
    //选项按钮
    @BindView(R.id.top_more)
    ImageButton mTopMore;
    //播放控制
    @BindView(R.id.playbar_prev)
    ImageButton mPlayBarPrev;
    @BindView(R.id.playbar_play)
    ImageView mPlayBarPlay;
    @BindView(R.id.playbar_next)
    ImageButton mPlayBarNext;
    @BindView(R.id.playbar_model)
    ImageButton mPlayModel;
    @BindView(R.id.playbar_playinglist)
    ImageButton mPlayQueue;
    //已播放时间和剩余播放时间
    @BindView(R.id.text_hasplay)
    TextView mHasPlay;
    @BindView(R.id.text_remain)
    TextView mRemainPlay;
    //进度条
    @BindView(R.id.seekbar)
    SeekBar mSeekBar;
    //背景
    @BindView(R.id.audio_holder_container)
    FrameLayout mContainer;
    @BindView(R.id.holder_pager)
    AudioViewPager mPager;
    //下一首歌曲
    @BindView(R.id.next_song)
    TextView mNextSong;
    //歌词控件
    private LrcView mLrcView;
    //背景渐变色
    @ColorInt
    private int mColorFrom;
    @ColorInt
    private int mColorTo;
    @ColorInt
    private int mColorDraken;
    @ColorInt
    private int mColorDark;
    //高亮与非高亮指示器
    private Drawable mHighLightIndicator;
    private Drawable mNormalIndicator;
    //Viewpager
    private PagerAdapter mAdapter;
    private Bundle mBundle;
    private ArrayList<ImageView> mGuideList;

    //当前播放的歌曲
    private MP3Item mInfo;
    //当前播放时间
    private int mCurrentTime;
    //当前歌曲总时长
    private int mDuration;

    //需要高斯模糊的高度与宽度
    public static int mWidth;
    public static int mHeight;
    //高斯模糊之前的bitmap
    private Bitmap mRawBitMap;
    //背景消失与现实的动画
    private Animation mAnimIn;
    private Animation mAnimOut;
    //是否从通知栏启动
    private boolean mFromNotify = false;
    //是否从MainActivity启动
    private boolean mFromMainActivity = false;
    //是否是后退按钮
    private boolean mFromBack = false;
    //是否需要更新
    private boolean mNeedUpdateUI = true;
    //是否开启背景渐变
    private boolean mGradient = false;

    //更新背景与专辑封面的Handler
    private Handler mBlurHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == Constants.UPDATE_BG) {
                //第一次更新不启用动画
                if(!mFistStart) {
                    mContainer.startAnimation(mAnimOut);
                } else {
                    updateViewColor();
                }

                //更新专辑封面
                ((CoverFragment) mAdapter.getItem(1)).UpdateCover(mInfo,!mFistStart);
                if(mFistStart)
                    mFistStart = false;
            }
        }
    };
    //更新进度条的Handler
    public  Handler mProgressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //如果当前正在播放，参数合法且用户没有在拖动进度条，更新进度条与时间
            if(mHasPlay != null
                    && mRemainPlay != null
                    && mCurrentTime > 0
                    && (mDuration - mCurrentTime) > 0){
                mHasPlay.setText(CommonUtil.getTime(mCurrentTime));
                mRemainPlay.setText(CommonUtil.getTime(mDuration - mCurrentTime));
            }
            if(msg.what == Constants.UPDATE_TIME_ALL && mSeekBar != null && !mIsDragSeekBar)
                mSeekBar.setProgress(mCurrentTime);
        }
    };


    @Override
    protected void setStatusBar() {
        if(mGradient){
            StatusBarUtil.setTransparent(this);
        } else {
            StatusBarUtil.setColor(this,Color.WHITE,0);
        }
//        StatusBarUtil.setTransparent(this);
    }

    @Override
    protected void setUpTheme() {
        super.setUpTheme();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFromNotify = getIntent().getBooleanExtra("Notify",false);
        mFromMainActivity =  getIntent().getBooleanExtra("FromMainActivity",false);
        mGradient = SPUtil.getValue(this,"Setting","Gradient",false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_holder);
        ButterKnife.bind(this);
        mInstance = this;

        //获是否正在播放和正在播放的歌曲
        mInfo = MusicService.getCurrentMP3();
        mIsPlay = MusicService.getIsplay();

        MusicService.addCallback(this);
        //初始化动画相关
        initAnim();
        //初始化顶部信息
        initTop();
        //初始化三个指示标志
        initGuide();
        //初始化ViewPager
        initPager();
        //初始化seekbar以及播放时间
        initSeekBar();
        //初始化主题颜色
        initViewColor();
    }

    /**
     * 上一首 下一首 播放、暂停
     * @param v
     */
    @OnClick({R.id.playbar_next,R.id.playbar_prev,R.id.playbar_play_container})
    public void onCtrlClick(View v){
        Intent intent = new Intent(Constants.CTL_ACTION);
        switch (v.getId()) {
            case R.id.playbar_prev:
                intent.putExtra("Control", Constants.PREV);
                break;
            case R.id.playbar_next:
                intent.putExtra("Control", Constants.NEXT);
                break;
            case R.id.playbar_play_container:
                intent.putExtra("Control", Constants.PLAYORPAUSE);
                if(mGradient){
                    Theme.TintDrawable(mPlayBarPlay,!mIsPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop,mColorDraken);
                } else {
                    mPlayBarPlay.setImageResource(!mIsPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop);
                }

                break;
        }
        MobclickAgent.onEvent(this,v.getId() == R.id.playbar_play_container ? "Prev" : v.getId() == R.id.playbar_next ? "Next" : "Play");
        sendBroadcast(intent);
    }

    /**
     * 播放模式 播放列表 关闭 隐藏
     * @param v
     */
    @OnClick({R.id.playbar_model,R.id.playbar_playinglist,R.id.top_hide,R.id.top_more})
    public void onOtherClick(View v){
        switch (v.getId()){
            //设置播放模式
            case R.id.playbar_model:
                MobclickAgent.onEvent(this,"PlayModel");
                int currentmodel = MusicService.getPlayModel();
                currentmodel = (currentmodel == Constants.PLAY_REPEATONE ? Constants.PLAY_LOOP : ++currentmodel);
                MusicService.setPlayModel(currentmodel);
                if(mGradient){
                    Theme.TintDrawable(mPlayModel,currentmodel == Constants.PLAY_LOOP ? R.drawable.play_btn_loop :
                        currentmodel == Constants.PLAY_SHUFFLE ? R.drawable.play_btn_shuffle :
                                R.drawable.play_btn_loop_one,mColorDraken);
                } else {
                    mPlayModel.setImageResource(currentmodel == Constants.PLAY_LOOP ? R.drawable.play_btn_loop :
                            currentmodel == Constants.PLAY_SHUFFLE ? R.drawable.play_btn_shuffle :
                                    R.drawable.play_btn_loop_one);
                }

                String msg = currentmodel == Constants.PLAY_LOOP ? getString(R.string.model_normal) :
                        currentmodel == Constants.PLAY_SHUFFLE ? getString(R.string.model_random) : getString(R.string.model_repeat);
                //刷新下一首
                if(currentmodel != Constants.PLAY_SHUFFLE && MusicService.getNextMP3() != null){
                    mNextSong.setText(MusicService.getNextMP3().getTitle());
                }
                ToastUtil.show(this,msg);
                break;
            //打开正在播放列表
            case R.id.playbar_playinglist:
                MobclickAgent.onEvent(this,"PlayingList");
                startActivity(new Intent(this,PlayQueueDialog.class));
                break;
            //关闭
            case R.id.top_hide:
                mFromBack = true;
                finish();
                break;
            //弹出窗口
            case R.id.top_more:
                Context wrapper = new ContextThemeWrapper(this,R.style.PopupMenuDayStyle);
                final PopupMenu popupMenu = new PopupMenu(wrapper,v, Gravity.TOP);
                popupMenu.getMenuInflater().inflate(R.menu.audio_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new AudioPopupListener(this,mInfo));
                popupMenu.show();
                break;
        }
    }

    /**
     * 初始化动画
     */
    private void initAnim() {
        WindowManager wm = getWindowManager();
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        mWidth = metrics.widthPixels;
        mHeight = metrics.heightPixels;

        mAnimIn = AnimationUtils.loadAnimation(this,R.anim.audio_bg_in);
        mAnimIn.setFillAfter(true);
        mAnimOut = AnimationUtils.loadAnimation(this,R.anim.audio_bg_out);
        mAnimOut.setFillAfter(true);
        mAnimOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                updateViewColor();
                mContainer.startAnimation(mAnimIn);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

    }


    @Override
    public void onResume() {
        super.onResume();
        //更新界面
//        if(MusicService.getCurrentMP3().getId() != mInfo.getId()) {
//            UpdateUI(MusicService.getCurrentMP3(), MusicService.getIsplay());
//        }
        mIsRunning = true;
        if(mNeedUpdateUI){
            UpdateUI(MusicService.getCurrentMP3(), MusicService.getIsplay());
            mNeedUpdateUI = false;
        }

        //更新进度条
        new ProgeressThread().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsRunning = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mRawBitMap != null && !mRawBitMap.isRecycled())
            mRawBitMap.recycle();
    }

    /**
     * 初始化三个dot
     */
    private void initGuide() {
        mGuideList = new ArrayList<>();
        mGuideList.add((ImageView) findView(R.id.guide_01));
        mGuideList.add((ImageView) findViewById(R.id.guide_02));
        mGuideList.add((ImageView) findViewById(R.id.guide_03));
        int width = DensityUtil.dip2px(this,5);
        int height = DensityUtil.dip2px(this,2);
        mHighLightIndicator = Theme.getShape(GradientDrawable.RECTANGLE,ThemeStore.getStressColor(),width,height);
        mNormalIndicator = Theme.getShape(GradientDrawable.RECTANGLE,ColorUtil.adjustAlpha(ThemeStore.getStressColor(),0.3f),width,height);
        for(int i = 0 ; i < mGuideList.size();i++){
            mGuideList.get(i).setImageDrawable(mNormalIndicator);
        }
    }

    private void initSeekBar() {
        if(mInfo == null)
            return;

        //初始化已播放时间与剩余时间
        mDuration = (int)mInfo.getDuration();
        final int temp = MusicService.getProgress();
        mCurrentTime = temp > 0 && temp < mDuration ? temp : 0;

        if(mDuration > 0 && mCurrentTime >= 0 && (mDuration - mCurrentTime) > 0){
            mHasPlay.setText(CommonUtil.getTime(mCurrentTime));
            mRemainPlay.setText(CommonUtil.getTime(mDuration - mCurrentTime));
        }

        //初始化seekbar
        if(mDuration > 0 && mDuration < Integer.MAX_VALUE)
            mSeekBar.setMax(mDuration);
        else
            mSeekBar.setMax(1000);

        if(mCurrentTime > 0 && mCurrentTime < mDuration)
            mSeekBar.setProgress(mCurrentTime);
        else
            mSeekBar.setProgress(0);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser)
                    mProgressHandler.sendEmptyMessage(Constants.UPDATE_TIME_ONLY);
                if(mLrcView != null)
                    mLrcView.seekTo(progress,true,fromUser);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsDragSeekBar = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //没有播放拖动进度条无效
                if(!mIsPlay){
                    seekBar.setProgress(0);
                }
                MusicService.setProgress(seekBar.getProgress());
                mIsDragSeekBar = false;
            }
        });
    }

    /**
     *
     */
    public void setMP3Item(MP3Item mp3Item){
        if(mp3Item != null)
            mInfo = mp3Item;
    }

    /**
     * 更新顶部歌曲信息
     * @param mp3Item
     */
    public void UpdateTopStatus(MP3Item mp3Item) {
        if(mp3Item == null)
            return;
        String title = mp3Item.getTitle() == null ? "" : mp3Item.getTitle();
        String artist =  mp3Item.getArtist() == null ? "" : mp3Item.getArtist();
        String album =  mp3Item.getAlbum() == null ? "" : mp3Item.getAlbum();

        if(title.equals(""))
            mTopTitle.setText(getString(R.string.unknow_song));
        else
            mTopTitle.setText(title);
        if(artist.equals(""))
            mTopDetail.setText(mp3Item.getAlbum());
        else if(album.equals(""))
            mTopDetail.setText(mp3Item.getArtist());
        else
            mTopDetail.setText(mp3Item.getArtist() + "-" + mp3Item.getAlbum());
    }

    /**
     * 更新播放、暂停按钮
     * @param isPlay
     */
    public void UpdatePlayButton(boolean isPlay) {
        if(mPlayBarPlay != null) {
//            Theme.TintDrawable(mPlayBarPlay,!isPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop,ThemeStore.getMaterialPrimaryColor());
            mPlayBarPlay.setImageResource(!isPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop);
        }
    }

    private void initTop() {
        //初始化顶部信息
        UpdateTopStatus(mInfo);
    }

    private void initPager() {
        //初始化Viewpager
        mAdapter = new PagerAdapter(getSupportFragmentManager());
        mBundle = new Bundle();
        mBundle.putSerializable("MP3Item", mInfo);
        //初始化所有fragment
        RecordFragment recordFragment = new RecordFragment();
        mAdapter.AddFragment(recordFragment);
        CoverFragment coverFragment = new CoverFragment();
        coverFragment.setArguments(mBundle);
        mAdapter.AddFragment(coverFragment);
        LrcFragment lrcFragment = new LrcFragment();
        lrcFragment.setOnFindListener(new LrcFragment.OnLrcViewFindListener() {
            @Override
            public void onLrcViewFind(LrcView lrcView) {
                mLrcView = lrcView;
                mLrcView.setOnSeekToListener(new LrcView.OnSeekToListener() {
                    @Override
                    public void onSeekTo(int progress) {
                        if(progress > 0 && progress < MusicService.getDuration()){
                            MusicService.setProgress(progress);
                            mCurrentTime = progress;
                            mProgressHandler.sendEmptyMessage(Constants.UPDATE_TIME_ALL);
                        }
                    }
                });
            }
        });
        lrcFragment.setArguments(mBundle);
        mAdapter.AddFragment(lrcFragment);

        mPager.setAdapter(mAdapter);
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }
            @Override
            public void onPageSelected(int position) {
                mGuideList.get(mPrevPosition).setImageDrawable(mNormalIndicator);
                mGuideList.get(position).setImageDrawable(mHighLightIndicator);
                mPrevPosition = position;
                if(position == 0)
                    mPager.setIntercept(true);
                else
                    mPager.setIntercept(false);
                if(mLrcView != null)
                    mLrcView.setViewPagerScroll(false);
            }
            @Override
            public void onPageScrollStateChanged(int state) {
                if(mLrcView != null)
                    mLrcView.setViewPagerScroll(state != ViewPager.SCROLL_STATE_IDLE);
            }
        });
        mPager.setCurrentItem(1);
    }
    @Override
    protected void onStart() {
        super.onStart();
        //只有从Mactivity启动，才使用动画
        if(!mFromNotify && mFromMainActivity) {
            overridePendingTransition(R.anim.slide_bottom_in, 0);
            mFromMainActivity = false;
        }
    }

    @Override
    public void finish() {
        super.finish();
        //只有后退到MainActivity,才使用动画
        if(mFromBack) {
            overridePendingTransition(0, R.anim.slide_bottom_out);
            mFromBack = false;
        }
    }

    @Override
    public void onBackPressed() {
        mFromBack = true;
        super.onBackPressed();
    }

    //更新界面
    @Override
    public void UpdateUI(MP3Item mp3Item, boolean isplay){
        mInfo = mp3Item;
        mIsPlay = isplay;
        //两种情况下更新ui
        //一是activity在前台  二是activity暂停后有更新的动作，当activity重新回到前台后更新ui
        if(!mIsRunning){
            mNeedUpdateUI = true;
            return;
        }
        if(mNeedUpdateUI || mIsRunning){
            //当操作不为播放或者暂停且正在运行时，更新所有控件
            if((Global.getOperation() != Constants.PLAYORPAUSE  || mFistStart) && mInfo != null ) {
                //更新顶部信息
                UpdateTopStatus(mInfo);
                //更新歌词
                ((LrcFragment) mAdapter.getItem(2)).UpdateLrc(mInfo);
                //更新进度条
                int temp = MusicService.getProgress();
                mCurrentTime = temp > 0 && temp < mDuration ? temp : 0;
                mDuration = (int) mInfo.getDuration();
                mSeekBar.setMax(mDuration);
                //更新下一首歌曲
                if(MusicService.getNextMP3() != null){
                    mNextSong.setText("下一首：" + MusicService.getNextMP3().getTitle());
                }
                //背景开启渐变
                if(mGradient){
                    new SwatchThread().start();
                } else {
                    //更新专辑封面
                    ((CoverFragment) mAdapter.getItem(1)).UpdateCover(mInfo,!mFistStart);
                    mFistStart = false;
                }
            }
            if(!mGradient) {
                //更新按钮状态
                UpdatePlayButton(isplay);
            }
        }

    }

    @Override
    public int getType() {
        return Constants.AUDIOHOLDERACTIVITY;
    }

    //更新进度条线程
    class ProgeressThread extends Thread {
        @Override
        public void run() {
            while (mIsRunning) {
                int temp = MusicService.getProgress();
                if (MusicService.getIsplay() && temp > 0 && temp < mDuration) {
                    mCurrentTime = temp;
                    mProgressHandler.sendEmptyMessage(Constants.UPDATE_TIME_ALL);
                    try {
                        sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 根据主题颜色修改按钮颜色
     */
    private void initViewColor() {
        if(mGradient)
            return;
        int stressColor = ThemeStore.getStressColor();
        int garyColor = ColorUtil.getColor(R.color.gray_6c6a6c);
        //修改顶部按钮颜色
        Theme.TintDrawable(mTopHide,R.drawable.play_btn_back,garyColor);
        Theme.TintDrawable(mTopMore,R.drawable.list_icn_more,garyColor);

        LayerDrawable layerDrawable =  (LayerDrawable) mSeekBar.getProgressDrawable();
        //修改progress颜色
        (layerDrawable.getDrawable(1)).setColorFilter(stressColor, PorterDuff.Mode.SRC_IN);
        mSeekBar.setProgressDrawable(layerDrawable);
        //修改thumb颜色
        mSeekBar.setThumb(Theme.TintDrawable(Theme.getShape(GradientDrawable.RECTANGLE,stressColor, DensityUtil.dip2px(this,2),DensityUtil.dip2px(this,6)),stressColor));

        //修改控制按钮颜色
        Theme.TintDrawable(mPlayBarNext,R.drawable.play_btn_next,stressColor);
        Theme.TintDrawable(mPlayBarPrev,R.drawable.play_btn_pre,stressColor);

        int playmodel = SPUtil.getValue(this,"Setting", "PlayModel",Constants.PLAY_LOOP);
        Theme.TintDrawable(mPlayModel,playmodel == Constants.PLAY_LOOP ? R.drawable.play_btn_loop :
                playmodel == Constants.PLAY_SHUFFLE ? R.drawable.play_btn_shuffle :
                        R.drawable.play_btn_loop_one,garyColor);
        Theme.TintDrawable(mPlayQueue,R.drawable.play_btn_normal_list,garyColor);

//        Theme.TintDrawable(mPlayBarPlay,!mIsPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop,mColorDraken);
        mPlayBarPlay.setImageResource(!mIsPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop);

        Theme.TintDrawable(findView(R.id.playbar_play_bg),getResources().getDrawable(R.drawable.play_bg_play),stressColor);
    }

    /**
     * 修改所有控件颜色
     */
    private void updateViewColor(){
        if(mTopDetail != null && mTopTitle != null && mRawBitMap != null){
            //修改背景颜色
            mContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{mColorFrom, mColorTo}));
            //歌词字体颜色
            if(mLrcView != null){
                mLrcView.setHighLightColor(ColorUtil.adjustAlpha(mNewSwatch.getRgb(),1.0f));
                mLrcView.setOtherColor(ColorUtil.adjustAlpha(mNewSwatch.getRgb(),0.6f));
                mLrcView.setTimeLineColor(ColorUtil.adjustAlpha(mNewSwatch.getRgb(),0.6f));
                mLrcView.invalidate();
            }
            //修改顶部字体颜色
            mTopTitle.setTextColor(mColorDraken);
            mTopDetail.setTextColor(mColorDark);

            //修改顶部按钮颜色
            Theme.TintDrawable(mTopHide,R.drawable.play_btn_back,mColorDraken);
            Theme.TintDrawable(mTopMore,R.drawable.list_icn_more,mColorDraken);

            LayerDrawable layerDrawable =  (LayerDrawable) mSeekBar.getProgressDrawable();
            //修改progress颜色
            (layerDrawable.getDrawable(1)).setColorFilter(mColorDraken, PorterDuff.Mode.SRC_IN);
            mSeekBar.setProgressDrawable(layerDrawable);
            //修改thumb颜色
            mSeekBar.setThumb(
                    Theme.TintDrawable(Theme.getShape(GradientDrawable.RECTANGLE,mColorDraken, DensityUtil.dip2px(this,2),DensityUtil.dip2px(this,6)),mColorDraken));
            //三个指示器
            int width = DensityUtil.dip2px(this,5);
            int height = DensityUtil.dip2px(this,2);
            mHighLightIndicator = Theme.getShape(GradientDrawable.RECTANGLE,mColorDraken,width,height);
            mNormalIndicator = Theme.getShape(GradientDrawable.RECTANGLE,ColorUtil.adjustAlpha(mColorDraken,0.3f),width,height);
            //修改控制按钮颜色
            Theme.TintDrawable(mPlayBarNext,R.drawable.play_btn_next,mColorDraken);
            Theme.TintDrawable(mPlayBarPrev,R.drawable.play_btn_pre,mColorDraken);
            //播放按钮
            mPlayBarPlay.setImageResource(!mIsPlay ? R.drawable.play_btn_play : R.drawable.play_btn_stop);
            int currentmodel = MusicService.getPlayModel();
            Theme.TintDrawable(mPlayModel,currentmodel == Constants.PLAY_LOOP ? R.drawable.play_btn_loop :
                    currentmodel == Constants.PLAY_SHUFFLE ? R.drawable.play_btn_shuffle :
                            R.drawable.play_btn_loop_one,mColorDraken);
            //播放按钮背景
            Theme.TintDrawable(findView(R.id.playbar_play_bg),getResources().getDrawable(R.drawable.play_bg_play),mColorDraken);
            //播放队列按钮
            Theme.TintDrawable(mPlayQueue,R.drawable.play_btn_normal_list,mColorDraken);
        }
    }

    class SwatchThread extends Thread{
        @Override
        public void run() {
            if(mInfo != null){
                mRawBitMap = MediaStoreUtil.getAlbumBitmap(mInfo.getAlbumId(),false);
                if(mRawBitMap == null)
                    mRawBitMap = BitmapFactory.decodeResource(getResources(), R.drawable.no_art_normal);

                /** start*/
                Palette palette = new Palette.Builder(mRawBitMap).generate();
                mOldSwatch = mNewSwatch;
                mNewSwatch = palette.getMutedSwatch();
                if(mNewSwatch == null)
                    mNewSwatch = new Palette.Swatch(Color.GRAY,100);
                mColorFrom = ColorUtil.adjustAlpha(mNewSwatch.getRgb(),0.4f);
                mColorTo = ColorUtil.adjustAlpha(mNewSwatch.getRgb(),0.1f);
//                mColorDraken = ColorUtil.shiftColor(mNewSwatch.getRgb(),0.8f);
//                mColorDark = mNewSwatch.getRgb();
                mColorDraken = mNewSwatch.getRgb();
                mColorDark = ColorUtil.adjustAlpha(mNewSwatch.getRgb(),0.8f);

                mBlurHandler.sendEmptyMessage(Constants.UPDATE_BG);
                /** end */
            }
        }
    }

}
