/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2012 YIXIA.COM
 * Copyright (C) 2013 Zhang Rui <bbcallen@gmail.com>

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.widget;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.protocol.HTTP;

import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnBufferingUpdateListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnCompletionListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnInfoListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnSeekCompleteListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnVideoSizeChangedListener;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.option.AvFourCC;
import tv.danmaku.ijk.media.player.option.format.AvFormatOption_HttpDetectRangeSupport;
import android.R.bool;
import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

/**
 * Displays a video file. The VideoView class can load images from various
 * sources (such as resources or content providers), takes care of computing its
 * measurement from the video so that it can be used in any layout manager, and
 * provides various display options such as scaling and tinting.
 * 
 * VideoView also provide many wrapper methods for
 * {@link io.vov.vitamio.MediaPlayer}, such as {@link #getVideoWidth()},
 * {@link #setSubShown(boolean)}
 */
public class VideoView extends SurfaceView implements
        MediaController.MediaPlayerControl {
    private static final String TAG = VideoView.class.getName();

    private Uri mUri;
    private long mDuration;
    private String mUserAgent;

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private static final int STATE_SUSPEND = 6;
    private static final int STATE_RESUME = 7;
    private static final int STATE_SUSPEND_UNSUPPORTED = 8;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private int mVideoLayout = VIDEO_LAYOUT_SCALE;
    public static final int VIDEO_LAYOUT_ORIGIN = 0;
    public static final int VIDEO_LAYOUT_SCALE = 1;
    public static final int VIDEO_LAYOUT_STRETCH = 2;
    public static final int VIDEO_LAYOUT_ZOOM = 3;

    private SurfaceHolder mSurfaceHolder = null;
    private IMediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoSarNum;
    private int mVideoSarDen;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private MediaController mMediaController;
    private View mMediaBufferingIndicator;
    private OnCompletionListener mOnCompletionListener;
    private OnPreparedListener mOnPreparedListener;
    private OnErrorListener mOnErrorListener;
    private OnSeekCompleteListener mOnSeekCompleteListener;
    private OnInfoListener mOnInfoListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private int mCurrentBufferPercentage;
    private long mSeekWhenPrepared;
    private boolean mCanPause = true;
    private boolean mCanSeekBack = true;
    private boolean mCanSeekForward = true;
    private Context mContext;

    public VideoView(Context context) {
        super(context);
        initVideoView(context);
    }

    public VideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initVideoView(context);
    }

    //new API by WilliamShi
    private static HashMap<String, Integer> tokenOpenCountHashMap = null; 
    private int addOpenCountWithStream(String token)
    {
    	if (tokenOpenCountHashMap==null) {
    		tokenOpenCountHashMap = new HashMap<>();
		}
    	
    	int openCount = 0;
    	if (tokenOpenCountHashMap.containsKey(token)) {
    		openCount = tokenOpenCountHashMap.get(token);
		}
    	openCount++;
    	tokenOpenCountHashMap.put(token, openCount);
    	
    	return openCount;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    /**
     * Set the display options
     * 
     * @param layout
     *            <ul>
     *            <li>{@link #VIDEO_LAYOUT_ORIGIN}
     *            <li>{@link #VIDEO_LAYOUT_SCALE}
     *            <li>{@link #VIDEO_LAYOUT_STRETCH}
     *            <li>{@link #VIDEO_LAYOUT_ZOOM}
     *            </ul>
     * @param aspectRatio
     *            video aspect ratio, will audo detect if 0.
     */
    public void setVideoLayout(int layout) {/*
        LayoutParams lp = getLayoutParams();
        Pair<Integer, Integer> res  = ScreenResolution.getResolution(mContext);
        int windowWidth = res.first.intValue(), windowHeight = res.second.intValue();
        float windowRatio = windowWidth / (float) windowHeight;
        int sarNum = mVideoSarNum;
        int sarDen = mVideoSarDen;
        if (mVideoHeight > 0 && mVideoWidth > 0) {
            float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
            if (sarNum > 0 && sarDen > 0)
                videoRatio = videoRatio * sarNum / sarDen;
            mSurfaceHeight = mVideoHeight;
            mSurfaceWidth = mVideoWidth;

            if (VIDEO_LAYOUT_ORIGIN == layout && mSurfaceWidth < windowWidth
                    && mSurfaceHeight < windowHeight) {
                lp.width = (int) (mSurfaceHeight * videoRatio);
                lp.height = mSurfaceHeight;
            } else if (layout == VIDEO_LAYOUT_ZOOM) {
                lp.width = windowRatio > videoRatio ? windowWidth
                        : (int) (videoRatio * windowHeight);
                lp.height = windowRatio < videoRatio ? windowHeight
                        : (int) (windowWidth / videoRatio);
            } else {
                boolean full = layout == VIDEO_LAYOUT_STRETCH;
                lp.width = (full || windowRatio < videoRatio) ? windowWidth
                        : (int) (videoRatio * windowHeight);
                lp.height = (full || windowRatio > videoRatio) ? windowHeight
                        : (int) (windowWidth / videoRatio);
            }
            setLayoutParams(lp);
            getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
            DebugLog.dfmt(
                    TAG,
                    "VIDEO: %dx%dx%f[SAR:%d:%d], Surface: %dx%d, LP: %dx%d, Window: %dx%dx%f",
                    mVideoWidth, mVideoHeight, videoRatio, mVideoSarNum,
                    mVideoSarDen, mSurfaceWidth, mSurfaceHeight, lp.width,
                    lp.height, windowWidth, windowHeight, windowRatio);
        }
        mVideoLayout = layout;*/
    }

    private void initVideoView(Context ctx) {
        mContext = ctx;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoSarNum = 0;
        mVideoSarDen = 0;
        getHolder().addCallback(mSHCallback);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        if (ctx instanceof Activity)
            ((Activity) ctx).setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    public boolean isValid() {
        return (mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid());
    }
    
    private String token = null;
    
    public String getToken()
    {
    	return token;
    }
    
    public int getStreamOpenCount() {
    	if (tokenOpenCountHashMap==null) {
    		tokenOpenCountHashMap = new HashMap<>();
		}
    	
    	int openCount = 0;
    	if (tokenOpenCountHashMap.containsKey(token)) {
    		openCount = tokenOpenCountHashMap.get(token);
		}
    	
    	return openCount;
	}
    
    private String cdnName = null;
    public String getCdnName()
    {
    	return cdnName;
    }
    
    private String linkAddress = null;
    private boolean isTokenMode = false;
    private int rootDataSourceType;
    public void setVideoToken(String token) {
    	isTokenMode = true;
		this.token = token;
		rootDataSourceType = mDataSourceType;
		
		loadVideoToken(this.token);
	}
    
    private void loadVideoToken(String token)
    {
		String urlHeaderString = "http://192.168.9.117:8080/live/httpcdn?token=";
		String tokenEncodedString;
		try {
			tokenEncodedString = URLEncoder.encode(token, HTTP.UTF_8);
		} catch (UnsupportedEncodingException e) {
			tokenEncodedString = token;
		}
		
		StringBuilder urlStringBuilder = new StringBuilder();
		String encodedUrlString = urlStringBuilder.append(urlHeaderString).append(tokenEncodedString).toString();
		Log.v(TAG, encodedUrlString);
		
		//async http request
		AsyncHttpClient client = new AsyncHttpClient();
		client.setURLEncodingEnabled(false);
		client.get(encodedUrlString, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int statusCode, Header[] headers,
					byte[] responseBody) {
				Log.v(TAG, "onSuccess");
				
				String jsonData = new String(responseBody);
				Log.v(TAG, jsonData);
				
				if (jsonData.isEmpty()) {
		            if (mOnErrorListener != null) {
		            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
		            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
		            }
		            return;
				}
				
				Gson gson = new Gson();
				PlayerUrlInfo urlInfo = new PlayerUrlInfo();
				urlInfo = gson.fromJson(jsonData, PlayerUrlInfo.class);
				
				String cdnString = urlInfo.getCdn();
				String linkString = urlInfo.getLink();
				
				if (cdnString==null || linkString==null) {
		            if (mOnErrorListener != null) {
		            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
		            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
		            }
		            return;
				}
				
				cdnName = cdnString;
				linkAddress = linkString;
				
//				try {
//					Thread.sleep(3000);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
				String playUrl = null;
				if (isBackPlayMode) {
					mDataSourceType = VOD_STREAMING_TYPE;
					
			    	//get vod url
			    	String vodUrl = null;
			    	if (cdnName.equals("ws")) {
						String baseUrl = linkAddress.substring(0, linkAddress.indexOf("?"));
						String vodBaseUrl = baseUrl.replace("rtmp://ws", "rtmp://wsshiyi");
						String tailUrl = linkAddress.substring(linkAddress.indexOf("?")+1,linkAddress.length());
						StringBuilder stringBuilder = new StringBuilder();
						vodUrl = stringBuilder.append(vodBaseUrl).append(backPlayUrlField).append(tailUrl).toString();
					}
			    	

			    	if (vodUrl==null) {
			            if (mOnErrorListener != null) {
			            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
			            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
			            }
			            return;
					}
			    	
			    	playUrl = vodUrl;
				}else {
					mDataSourceType = rootDataSourceType;
					playUrl = linkAddress;
				}
				
				setVideoPath(playUrl);
			}

			@Override
			public void onFailure(int statusCode, Header[] headers,
					byte[] responseBody, Throwable error) {
				Log.v(TAG, "onFailure");

	            if (mOnErrorListener != null) {
	            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
	            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
	            }
	            return;
			}
		});

    }
    
    private boolean isBackPlayMode = false;
    private String backPlayUrlField = null;
    //new API for back play
    public void backPlayWithABS(long absTime) //绝对时间
    {
    	if (!isTokenMode) return;
    	isBackPlayMode = true;
    	
    	//release live stream
    	stopPlayback();
    	
    	StringBuilder stringBuilder = new StringBuilder();
    	backPlayUrlField = stringBuilder.append("?wsStreamTimeABS=").append(absTime).append("&").toString();
    	
    	loadVideoToken(token);
    }
    
    public void backPlayWithREL(long relTime) //相对时间
    {
    	if (!isTokenMode) return;
    	isBackPlayMode = true;
    	
    	//release live stream
    	stopPlayback();
    	
    	StringBuilder stringBuilder = new StringBuilder();
    	backPlayUrlField = stringBuilder.append("?wsStreamTimeREL=").append(relTime).append("&").toString();
    	
    	loadVideoToken(token);
    }
    
    public void backLivePlay()
    {
    	if (!isTokenMode) return;
    	isBackPlayMode = false;
    	
    	//release live stream
    	stopPlayback();

    	loadVideoToken(token);
    }
    //
    
    public void setVideoPath(String path) {
    	//add by William
    	try {
			this.token = URLEncoder.encode(path, HTTP.UTF_8);
		} catch (UnsupportedEncodingException e) {
			this.token = path;
		}
    	
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void setUserAgent(String ua) {
    	mUserAgent = ua;
    }
    
    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
        
        //end info report
        if (playerInfoReport!=null) {
        	playerInfoReport.endReport();
        	playerInfoReport = null;
        }
    }

    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null)
            return;

        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);

        release(false);
        try {
            mDuration = -1;
            mCurrentBufferPercentage = 0;
            // mMediaPlayer = new AndroidMediaPlayer();
            IjkMediaPlayer ijkMediaPlayer = null;
            if (mUri != null) {
                ijkMediaPlayer = new IjkMediaPlayer();
                if(isMediaCodecEnabled)
                {
                	ijkMediaPlayer.setMediaCodecEnabled(true);
                }else {
                    if(isSupportMediaCodec())
                    {
                    	ijkMediaPlayer.setMediaCodecEnabled(true);
                    	isMediaCodecEnabled = true;
                    }
				}
                ijkMediaPlayer.setDataSourceType(mDataSourceType);
                ijkMediaPlayer.setDataCache(mCache);
                ijkMediaPlayer.setAvOption(AvFormatOption_HttpDetectRangeSupport.Disable);
                ijkMediaPlayer.setOverlayFormat(AvFourCC.SDL_FCC_RV32);

                ijkMediaPlayer.setAvCodecOption("skip_loop_filter", "48");
                ijkMediaPlayer.setFrameDrop(12);
                if (mUserAgent != null) {
                    ijkMediaPlayer.setAvFormatOption("user_agent", mUserAgent);
                }
            }
            mMediaPlayer = ijkMediaPlayer;
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            if (mUri != null)
                mMediaPlayer.setDataSource(mUri.toString());
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            DebugLog.e(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer,
                    IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (IllegalArgumentException ex) {
            DebugLog.e(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer,
                    IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null)
            mMediaController.hide();
        mMediaController = controller;
        attachMediaController();
    }

    public void setMediaBufferingIndicator(View mediaBufferingIndicator) {
        if (mMediaBufferingIndicator != null)
            mMediaBufferingIndicator.setVisibility(View.GONE);
        mMediaBufferingIndicator = mediaBufferingIndicator;
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ? (View) this
                    .getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());

            if (mUri != null) {
                List<String> paths = mUri.getPathSegments();
                String name = paths == null || paths.isEmpty() ? "null" : paths
                        .get(paths.size() - 1);
                mMediaController.setFileName(name);
            }
        }
    }

    OnVideoSizeChangedListener mSizeChangedListener = new OnVideoSizeChangedListener() {
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height,
                int sarNum, int sarDen) {
            DebugLog.dfmt(TAG, "onVideoSizeChanged: (%dx%d)", width, height);
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            mVideoSarNum = sarNum;
            mVideoSarDen = sarDen;
//            if (mVideoWidth != 0 && mVideoHeight != 0)
//                setVideoLayout(mVideoLayout);
        }
    };

    private PlayerInfoReport playerInfoReport = null;
    
    OnPreparedListener mPreparedListener = new OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            DebugLog.d(TAG, "onPrepared");
            
            if (isTokenMode)
            {
            }
            
            //add by William
            addOpenCountWithStream(token);
            
            mCurrentState = STATE_PREPARED;
            mTargetState = STATE_PLAYING;

            if (mOnPreparedListener != null)
                mOnPreparedListener.onPrepared(mMediaPlayer);
            if (mMediaController != null)
                mMediaController.setEnabled(true);
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            long seekToPosition = mSeekWhenPrepared;

            if (seekToPosition != 0)
                seekTo(seekToPosition);
            if (mVideoWidth != 0 && mVideoHeight != 0) {
//                setVideoLayout(mVideoLayout);
                if (mSurfaceWidth == mVideoWidth
                        && mSurfaceHeight == mVideoHeight) {
                    if (mTargetState == STATE_PLAYING) {
                        start();
                        if (mMediaController != null)
                            mMediaController.show();
                    } else if (!isPlaying()
                            && (seekToPosition != 0 || getCurrentPosition() > 0)) {
                        if (mMediaController != null)
                            mMediaController.show(0);
                    }
                }
            } else if (mTargetState == STATE_PLAYING) {
                start();
            }
            
            if (isTokenMode) {

			}
            //start info report
            if (playerInfoReport==null) {
            	playerInfoReport = new PlayerInfoReport(VideoView.this);
			}
            playerInfoReport.startReport();
        }
    };

    private OnCompletionListener mCompletionListener = new OnCompletionListener() {
        public void onCompletion(IMediaPlayer mp) {
            DebugLog.d(TAG, "onCompletion");
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;
            if (mMediaController != null)
                mMediaController.hide();
            if (mOnCompletionListener != null)
                mOnCompletionListener.onCompletion(mMediaPlayer);
        }
    };

    
    private int errorCode = 0;
    public int getErrorCode()
    {
    	return errorCode;
    }
    
    private OnErrorListener mErrorListener = new OnErrorListener() {
        public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
            DebugLog.dfmt(TAG, "Error: %d, %d", framework_err, impl_err);
            
            errorCode = impl_err;
            
            if (playerInfoReport==null) {
            	playerInfoReport = new PlayerInfoReport(VideoView.this);
			}
            playerInfoReport.reportError();
            
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            if (mMediaController != null)
                mMediaController.hide();

            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mMediaPlayer, framework_err,
                        impl_err))
                    return true;
            }

            if (getWindowToken() != null) {
                int message = framework_err == IMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK ? R.string.vitamio_videoview_error_text_invalid_progressive_playback
                        : R.string.vitamio_videoview_error_text_unknown;

                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.vitamio_videoview_error_title)
                        .setMessage(message)
                        .setPositiveButton(
                                R.string.vitamio_videoview_error_button,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton) {
                                        if (mOnCompletionListener != null)
                                            mOnCompletionListener
                                                    .onCompletion(mMediaPlayer);
                                    }
                                }).setCancelable(false).show();
            }
            return true;
        }
    };

    private OnBufferingUpdateListener mBufferingUpdateListener = new OnBufferingUpdateListener() {
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
            if (mOnBufferingUpdateListener != null)
                mOnBufferingUpdateListener.onBufferingUpdate(mp, percent);
        }
    };

    //new API add by William Shi
    private int allbuffingCount = 0;
    private int buffingCountPerMinute = 0;
    
    public int getAllbuffingCount()
    {
    	return allbuffingCount;
    }
    
    public int getBuffingCountPerMinute() {
		int tmpbuffingCountPerMinute = buffingCountPerMinute;
		buffingCountPerMinute = 0;
		return tmpbuffingCountPerMinute;
	}
    
    private boolean isBuffing = false;
    private long buffingTimePerMinute = 0;
    private long buffingStartTime = 0;
    
    public int getBuffingTimePerMinute()
    {
    	if (isBuffing) {
    		buffingTimePerMinute += SystemClock.uptimeMillis() - buffingStartTime;
		}
    	
    	long tmpBuffingTimePerMinute = buffingTimePerMinute;
    	buffingTimePerMinute = 0;
    	
    	buffingStartTime = SystemClock.uptimeMillis();
    	
    	return (int)tmpBuffingTimePerMinute/1000;
    }
    
    private OnInfoListener mInfoListener = new OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            DebugLog.dfmt(TAG, "onInfo: (%d, %d)", what, extra);
            if (mOnInfoListener != null) {
                mOnInfoListener.onInfo(mp, what, extra);
            } else if (mMediaPlayer != null) {
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    DebugLog.dfmt(TAG, "onInfo: (MEDIA_INFO_BUFFERING_START)");
                    if (mMediaBufferingIndicator != null)
                        mMediaBufferingIndicator.setVisibility(View.VISIBLE);
                    
                    allbuffingCount++;
                    buffingCountPerMinute++;
                    
                    isBuffing = true;
                    buffingStartTime = SystemClock.uptimeMillis();
                    
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    DebugLog.dfmt(TAG, "onInfo: (MEDIA_INFO_BUFFERING_END)");
                    if (mMediaBufferingIndicator != null)
                        mMediaBufferingIndicator.setVisibility(View.GONE);
                    
                    isBuffing = false;
                    buffingTimePerMinute += SystemClock.uptimeMillis() - buffingStartTime;
                }
            }

            return true;
        }
    };

    private OnSeekCompleteListener mSeekCompleteListener = new OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            DebugLog.d(TAG, "onSeekComplete");
            if (mOnSeekCompleteListener != null)
                mOnSeekCompleteListener.onSeekComplete(mp);
        }
    };

    public void setOnPreparedListener(OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener l) {
        mOnBufferingUpdateListener = l;
    }

    public void setOnSeekCompleteListener(OnSeekCompleteListener l) {
        mOnSeekCompleteListener = l;
    }

    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
    }
    
    private void openVideoWithToken(String token)
    {
		String urlHeaderString = "http://192.168.9.117:8080/live/httpcdn?token=";
		String tokenEncodedString;
		try {
			tokenEncodedString = URLEncoder.encode(token, HTTP.UTF_8);
		} catch (UnsupportedEncodingException e) {
			tokenEncodedString = token;
		}
		
		StringBuilder urlStringBuilder = new StringBuilder();
		String encodedUrlString = urlStringBuilder.append(urlHeaderString).append(tokenEncodedString).toString();
		Log.v(TAG, encodedUrlString);
		
		//async http request
		AsyncHttpClient client = new AsyncHttpClient();
		client.setURLEncodingEnabled(false);
		client.get(encodedUrlString, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int statusCode, Header[] headers,
					byte[] responseBody) {
				Log.v(TAG, "onSuccess");
				
				String jsonData = new String(responseBody);
				Log.v(TAG, jsonData);
				
				if (jsonData.isEmpty()) {
		            if (mOnErrorListener != null) {
		            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
		            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
		            }
		            return;
				}
				
				Gson gson = new Gson();
				PlayerUrlInfo urlInfo = new PlayerUrlInfo();
				urlInfo = gson.fromJson(jsonData, PlayerUrlInfo.class);
				
				String cdnString = urlInfo.getCdn();
				String linkString = urlInfo.getLink();
				
				if (cdnString==null || linkString==null) {
		            if (mOnErrorListener != null) {
		            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
		            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
		            }
		            return;
				}
				
				cdnName = cdnString;
				linkAddress = linkString;
				
				String playUrl = null;
				if (isBackPlayMode) {
					mDataSourceType = VOD_STREAMING_TYPE;
					
			    	//get vod url
			    	String vodUrl = null;
			    	if (cdnName.equals("ws")) {
						String baseUrl = linkAddress.substring(0, linkAddress.indexOf("?"));
						String vodBaseUrl = baseUrl.replace("rtmp://ws", "rtmp://wsshiyi");
						String tailUrl = linkAddress.substring(linkAddress.indexOf("?")+1,linkAddress.length());
						StringBuilder stringBuilder = new StringBuilder();
						vodUrl = stringBuilder.append(vodBaseUrl).append(backPlayUrlField).append(tailUrl).toString();
					}
			    	

			    	if (vodUrl==null) {
			            if (mOnErrorListener != null) {
			            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
			            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
			            }
			            return;
					}
			    	
			    	playUrl = vodUrl;
				}else {
					mDataSourceType = rootDataSourceType;
					playUrl = linkAddress;
				}
				
				mUri = Uri.parse(playUrl);
				openVideo();
			}

			@Override
			public void onFailure(int statusCode, Header[] headers,
					byte[] responseBody, Throwable error) {
				Log.v(TAG, "onFailure");

	            if (mOnErrorListener != null) {
	            	mOnErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN,
	            			IMediaPlayer.MEDIA_ERROR_UNSUPPORTED);
	            }
	            return;
			}
		});
    }
    
    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w,
                int h) {
            mSurfaceHolder = holder;
            if (mMediaPlayer != null) {
                mMediaPlayer.setDisplay(mSurfaceHolder);
            }

            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0)
                    seekTo(mSeekWhenPrepared);
                start();
                if (mMediaController != null) {
                    if (mMediaController.isShowing())
                        mMediaController.hide();
                    mMediaController.show();
                }
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
            if (mMediaPlayer != null && mCurrentState == STATE_SUSPEND
                    && mTargetState == STATE_RESUME) {
                mMediaPlayer.setDisplay(mSurfaceHolder);
                resume();
            } else {
//                openVideo();
            	if (isTokenMode && !isBackPlayMode) {
            		openVideoWithToken(token);
				}else {
					openVideo();
				}
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            mSurfaceHolder = null;
            if (mMediaController != null)
                mMediaController.hide();
            if (mCurrentState != STATE_SUSPEND)
                release(true);
        }
    };

    private void release(boolean cleartargetstate) {
    	
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate)
                mTargetState = STATE_IDLE;
        }
        
        //end info report
        if (playerInfoReport!=null) {
        	playerInfoReport.endReport();
        	playerInfoReport = null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null)
            toggleMediaControlsVisiblity();
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null)
            toggleMediaControlsVisiblity();
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK
                && keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                && keyCode != KeyEvent.KEYCODE_MENU
                && keyCode != KeyEvent.KEYCODE_CALL
                && keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported
                && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_SPACE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    && mMediaPlayer.isPlaying()) {
                pause();
                mMediaController.show();
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void resume() {
        if (mSurfaceHolder == null && mCurrentState == STATE_SUSPEND) {
            mTargetState = STATE_RESUME;
        } else if (mCurrentState == STATE_SUSPEND_UNSUPPORTED) {
            openVideo();
        }
    }

    public void black_screen() {
    	Canvas canvas = this.getHolder().lockCanvas();
    	canvas.drawARGB(255, 0, 0, 0);
    	this.getHolder().unlockCanvasAndPost(canvas);
    	invalidate();
    }
    
    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            if (mDuration > 0)
                return (int) mDuration;
            mDuration = mMediaPlayer.getDuration();
            return (int) mDuration;
        }
        mDuration = -1;
        return (int) mDuration;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            long position = mMediaPlayer.getCurrentPosition();
            return (int) position;
        }
        return 0;
    }
    
    public int getPlayableDuration() {
        if (isInPlaybackState()) {
            return (int)mMediaPlayer.getPlayableDuration()/1000;
        }
        return 0;
	}

    public String getRemoteIpAddress()
    {
    	if (isInPlaybackState()) {
			return mMediaPlayer.getRemoteIpAddress();
		}
    	return null;
    }
    
    public int getBitRate() {
    	if (isInPlaybackState()) {
			return mMediaPlayer.getBitRate();
		}
    	return 0;
	}

    @Override
    public void seekTo(long msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null)
            return mCurrentBufferPercentage;
        return 0;
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    protected boolean isInPlaybackState() {
        return (mMediaPlayer != null && mCurrentState != STATE_ERROR
                && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
    }

    public boolean canPause() {
        return mCanPause;
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }
	public void setPlayerSpeedMode(int speedMode)
	{
		IjkMediaPlayer ijkMediaPlayer = (IjkMediaPlayer)mMediaPlayer;
		ijkMediaPlayer.setPlayerSpeedMode(speedMode);
	}
	public void setPlayerVolume(int volume)
	{
		IjkMediaPlayer ijkMediaPlayer = (IjkMediaPlayer)mMediaPlayer;
		ijkMediaPlayer.setPlayerVolume(volume);
	}
    //add by william
    private int mDataSourceType = LOWDELAY_LIVE_STREAMING_TYPE;
    public static final int LOWDELAY_LIVE_STREAMING_TYPE = 0;
    public static final int HIGHDELAY_LIVE_STREAMING_TYPE = 1;
    public static final int VOD_STREAMING_TYPE = 2;
    public void setDataSourceType(int type)
    {
    	mDataSourceType = type;
    }
    public int getDataSourceType()
    {
    	return mDataSourceType;
    }
    
    private boolean isMediaCodecEnabled = false;
    public void setMediaCodecEnabled(boolean enable)
    {
    	isMediaCodecEnabled = enable;
    }
    
    private String getUniqueId()
    {
    	return android.os.Build.MODEL;
    }
    
//    private String SUMSUN_NOTE_III_N9002 = "samsung/h3gduoszn/hlte:4.4.2/KOT49H/N9002ZNUFNK1:user/release-keys";
//    private String MI_4LTE = "Xiaomi/cancro_wc_lte/cancro:4.4.4/KTU84P/V6.4.1.0.KXDCNCB:user/release-keys";
//    private String SUMSUN_S_IV = "samsung/jftddzn/jftdd:4.4.2/KOT49H/I9507VZNUBOB1:user/release-keys";
//    private String MI_3 = "Xiaomi/cancro/cancro:4.4.4/KTU84P/5.4.24:user/release-keys";
//    private String M1_NOTE = "Meizu/meizu_m1note/m1note:4.4.4/KTU84P/m71c.Flyme_OS_4.2.20150312033938:user/release-keys";
    
    private String M1_NOTE_model = "m1 note";
    private String MI_3_model = "MI 3";
    private String MI_4LTE_model = "MI 4LTE";
    private String SUMSUN_S_IV_model = "GT-I95";
    private String sUMSUN_NOTE_III_model = "SM-N900";
    
    private boolean isSupportMediaCodec()
    {
    	if (getUniqueId().startsWith(M1_NOTE_model)
    			|| getUniqueId().startsWith(SUMSUN_S_IV_model)
    			|| getUniqueId().startsWith(MI_3_model)
    			|| getUniqueId().startsWith(MI_4LTE_model)
    			|| getUniqueId().startsWith(sUMSUN_NOTE_III_model)) {
			return true;
		}else {
			return false;
		}
    }
    
    //ms
    private int mCache = 10000;//10ms
    public void setDataCache(int cache)
    {
    	mCache = cache;
    }
    
    public long getAbsoluteTimestamp()
    {
    	if (mMediaPlayer!=null) {
			return mMediaPlayer.getAbsoluteTimestamp();
		}
    	
    	return 0;
    }
}
