package com.example.android.tflitecamerademo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.daniulive.smartpublisher.SmartPublisherJniV2;
import com.eventhandle.NTSmartEventCallbackV2;
import com.eventhandle.NTSmartEventID;
import com.voiceengine.NTAudioRecordV2;
import com.voiceengine.NTAudioRecordV2Callback;

import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link LiveRecordFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LiveRecordFragment extends Fragment implements SurfaceHolder.Callback, Camera.PreviewCallback{

    private static final String TAG = "RecordFragment";

    public static LiveRecordFragment newInstance() {
        LiveRecordFragment fragment = new LiveRecordFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private SmartPublisherJniV2 libPublisher = null;

    //NTAudioRecord audioRecord_ = null;	//for audio capture
    NTAudioRecordV2 audioRecord_ = null;
    NTAudioRecordV2Callback audioRecordCallback_ = null;

    private Context myContext;    //上下文context
    private long publisherHandle = 0;    //推送handle

    private int pushType = 0;    //推送类型，0：音视频
    private int watemarkType = 3;  // 水印，3 ： 不加水印
    private int sw_video_encoder_profile = 1;    //default with baseline profile


    private int videoEncodeType = 0;  // 默认视频编码模式

    private ImageView imgSwitchCamera;    //前后摄像头切换按钮
    private Button btnInputPushUrl;    //推送的RTMP url设置按钮

    private Button btnStartPush;    //RTMP推送按钮



    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;

    private Camera mCamera = null;
    private Camera.AutoFocusCallback myAutoFocusCallback = null;    //自动对焦

    private boolean mPreviewRunning = false; //priview状态
    private boolean isPushing = false;    //RTMP推送状态
    private boolean isRecording = false;    //录像状态


    final private String logoPath = "/sdcard/daniulivelogo.png";
    private boolean isWritelogoFileSuccess = false;

    private String publishURL;    //RTMP推送URL
    final private String baseURL = "rtmp://39.108.210.48:1935/live/lwj";
    private String inputPushURL = "rtmp://39.108.210.48:1935/live/lwj";  // 推流的url

    private TextView publishUrlText;  // 当前推流url的textview




    private static final int FRONT = 1;        //前置摄像头标记
    private static final int BACK = 2;        //后置摄像头标记
    private int currentCameraType = BACK;    //当前打开的摄像头标记
    private static final int PORTRAIT = 1;    //竖屏
    private static final int LANDSCAPE = 2;    //横屏 home键在右边的情况
    private static final int LANDSCAPE_LEFT_HOME_KEY = 3; // 横屏 home键在左边的情况
    private int currentOrigentation = PORTRAIT;
    private int curCameraIndex = -1;

    private int videoWidth = 640;
    private int videoHeight = 480;

    private int frameCount = 0;

    private String recDir = "/sdcard/daniulive/rec";    //for recorder path

    private boolean is_noise_suppression = true;
    private boolean is_agc = false;
    private boolean is_speex = false;
    private boolean is_mute = false;
    private boolean is_mirror = false;
    private int sw_video_encoder_speed = 3;
    private boolean is_sw_vbr_mode = true;

    private String imageSavePath;

    private static final int PUBLISHER_EVENT_MSG = 1;
    private static final int PUBLISHER_USER_DATA_MSG = 2;

    static {
        System.loadLibrary("SmartPublisher");
    }



    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_record,null);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);    //屏幕常亮

        myContext = getActivity().getApplicationContext();

        btnInputPushUrl = (Button) view.findViewById(R.id.button_input_push_url);
        btnInputPushUrl.setOnClickListener(new ButtonInputPushUrlListener());

        btnStartPush = (Button) view.findViewById(R.id.button_start_push);
        btnStartPush.setOnClickListener(new ButtonStartPushListener());

        publishUrlText = (TextView)view.findViewById(R.id.publish_url);
        publishUrlText.setText(inputPushURL);




        imgSwitchCamera = (ImageView) view.findViewById(R.id.button_switchCamera);
        imgSwitchCamera.setOnClickListener(new SwitchCameraListener());

        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //自动聚焦变量回调
        myAutoFocusCallback = new Camera.AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    Log.i(TAG, "onAutoFocus succeed...");
                } else {
                    Log.i(TAG, "onAutoFocus failed...");
                }
            }
        };

        libPublisher = new SmartPublisherJniV2();

        libPublisher.InitRtspServer(myContext);      //和UnInitRtspServer配对使用，即便是启动多个RTSP服务，也只需调用一次InitRtspServer，请确保在OpenRtspServer之前调用

        return view;
    }



    class SwitchCameraListener implements View.OnClickListener {
        public void onClick(View v) {
            Log.i(TAG, "Switch camera..");
            try {
                switchCamera();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };


    class NTAudioRecordV2CallbackImpl implements NTAudioRecordV2Callback {
        @Override
        public void onNTAudioRecordV2Frame(ByteBuffer data, int size, int sampleRate, int channel, int per_channel_sample_number) {
    		 /*
    		 Log.i(TAG, "onNTAudioRecordV2Frame size=" + size + " sampleRate=" + sampleRate + " channel=" + channel
    				 + " per_channel_sample_number=" + per_channel_sample_number);

    		 */

            if (publisherHandle != 0) {
                libPublisher.SmartPublisherOnPCMData(publisherHandle, data, size, sampleRate, channel, per_channel_sample_number);
            }
        }
    }

    void CheckInitAudioRecorder() {
        if (audioRecord_ == null) {
            //audioRecord_ = new NTAudioRecord(this, 1);

            audioRecord_ = new NTAudioRecordV2(getActivity());
        }

        if (audioRecord_ != null) {
            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()+++...");

            audioRecordCallback_ = new NTAudioRecordV2CallbackImpl();

            // audioRecord_.IsMicSource(true);      //如采集音频声音过小，可以打开此选项

            // audioRecord_.IsRemoteSubmixSource(true);

            audioRecord_.AddCallback(audioRecordCallback_);

            audioRecord_.Start();

            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()---...");


        }
    }

    //Configure recorder related function.
    void ConfigRecorderFuntion(boolean isNeedLocalRecorder) {
        if (libPublisher != null && publisherHandle != 0) {
            if (isNeedLocalRecorder) {
                if (recDir != null && !recDir.isEmpty()) {
                    int ret = libPublisher.SmartPublisherCreateFileDirectory(recDir);
                    if (0 == ret) {
                        if (0 != libPublisher.SmartPublisherSetRecorderDirectory(publisherHandle, recDir)) {
                            Log.e(TAG, "Set record dir failed , path:" + recDir);
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorder(publisherHandle, 1)) {
                            Log.e(TAG, "SmartPublisherSetRecorder failed.");
                            return;
                        }

                        if (0 != libPublisher.SmartPublisherSetRecorderFileMaxSize(publisherHandle, 200)) {
                            Log.e(TAG, "SmartPublisherSetRecorderFileMaxSize failed.");
                            return;
                        }

                    } else {
                        Log.e(TAG, "Create record dir failed, path:" + recDir);
                    }
                }
            } else {
                if (0 != libPublisher.SmartPublisherSetRecorder(publisherHandle, 0)) {
                    Log.e(TAG, "SmartPublisherSetRecorder failed.");
                }
            }
        }
    }




    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case PUBLISHER_EVENT_MSG:
                    String cur_event = "Event: " + (String) msg.obj;
                    break;
                default:
                    break;
            }
        }
    };

    class EventHandeV2 implements NTSmartEventCallbackV2 {
        @Override
        public void onNTSmartEventCallbackV2(long handle, int id, long param1, long param2, String param3, String param4, Object param5) {

            Log.i(TAG, "EventHandeV2: handle=" + handle + " id:" + id);

            String publisher_event = "";

            switch (id) {
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STARTED:
                    publisher_event = "开始..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTING:
                    publisher_event = "连接中..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTION_FAILED:
                    publisher_event = "连接失败..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTED:
                    publisher_event = "连接成功..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_DISCONNECTED:
                    publisher_event = "连接断开..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STOP:
                    publisher_event = "关闭..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RECORDER_START_NEW_FILE:
                    publisher_event = "开始一个新的录像文件 : " + param3;
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_ONE_RECORDER_FILE_FINISHED:
                    publisher_event = "已生成一个录像文件 : " + param3;
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_SEND_DELAY:
                    publisher_event = "发送时延: " + param1 + " 帧数:" + param2;
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CAPTURE_IMAGE:
                    publisher_event = "快照: " + param1 + " 路径：" + param3;

                    if (param1 == 0) {
                        publisher_event = publisher_event + "截取快照成功..";
                    } else {
                        publisher_event = publisher_event + "截取快照失败..";
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RTSP_URL:
                    publisher_event = "RTSP服务URL: " + param3;
                    break;
            }

            String str = "当前回调状态：" + publisher_event;

            Log.i(TAG, str);

            Message message = new Message();
            message.what = PUBLISHER_EVENT_MSG;
            message.obj = publisher_event;
            handler.sendMessage(message);

        }
    }

    private void SaveInputUrl(String url) {
        inputPushURL = "";

        if (url == null)
            return;

        // rtmp://
        if (url.length() < 8) {
            Log.e(TAG, "Input publish url error:" + url);
            return;
        }

        if (!url.startsWith("rtmp://")) {
            Log.e(TAG, "Input publish url error:" + url);
            return;
        }

        inputPushURL = url;
        publishUrlText.setText(inputPushURL);

        Log.i(TAG, "Input publish url:" + url);
    }

    //RTMP推送URL设置
    private void PopInputUrlDialog() {
        final EditText inputUrlTxt = new EditText(getActivity());
        inputUrlTxt.setFocusable(true);
        inputUrlTxt.setText(baseURL);

        android.app.AlertDialog.Builder builderUrl = new android.app.AlertDialog.Builder(getActivity());
        builderUrl.setTitle("如 "+ baseURL).setView(inputUrlTxt).setNegativeButton(
                "取消", null);

        builderUrl.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                String fullPushUrl = inputUrlTxt.getText().toString();
                SaveInputUrl(fullPushUrl);
            }
        });

        builderUrl.show();
    }

    class ButtonInputPushUrlListener implements View.OnClickListener {
        public void onClick(View v) {
            PopInputUrlDialog();
        }
    }



    private void ConfigControlEnable(boolean isEnable) {;
        btnInputPushUrl.setEnabled(isEnable);
    }

    private void InitAndSetConfig() {
        Log.i(TAG, "videoWidth: " + videoWidth + " videoHeight: " + videoHeight
                + " pushType:" + pushType);

        int audio_opt = 1;
        int video_opt = 1;

        if (pushType == 1) {
            video_opt = 0;
        } else if (pushType == 2) {
            audio_opt = 0;
        }

        publisherHandle = libPublisher.SmartPublisherOpen(myContext, audio_opt, video_opt,
                videoWidth, videoHeight);

        if (publisherHandle == 0) {
            Log.e(TAG, "sdk open failed!");
            return;
        }

        Log.i(TAG, "publisherHandle=" + publisherHandle);

        if(videoEncodeType == 1)
        {
            int h264HWKbps = setHardwareEncoderKbps(true, videoWidth, videoHeight);

            Log.i(TAG, "h264HWKbps: " + h264HWKbps);

            int isSupportH264HWEncoder = libPublisher
                    .SetSmartPublisherVideoHWEncoder(publisherHandle, h264HWKbps);

            if (isSupportH264HWEncoder == 0) {
                Log.i(TAG, "Great, it supports h.264 hardware encoder!");
            }
        }
        else if (videoEncodeType == 2)
        {
            int hevcHWKbps = setHardwareEncoderKbps(false, videoWidth, videoHeight);

            Log.i(TAG, "hevcHWKbps: " + hevcHWKbps);

            int isSupportHevcHWEncoder = libPublisher
                    .SetSmartPublisherVideoHevcHWEncoder(publisherHandle, hevcHWKbps);

            if (isSupportHevcHWEncoder == 0) {
                Log.i(TAG, "Great, it supports hevc hardware encoder!");
            }
        }

        if(is_sw_vbr_mode)	//H.264 software encoder
        {
            int is_enable_vbr = 1;
            int video_quality = CalVideoQuality(videoWidth, videoHeight, true);
            int vbr_max_bitrate = CalVbrMaxKBitRate(videoWidth, videoHeight);

            libPublisher.SmartPublisherSetSwVBRMode(publisherHandle, is_enable_vbr, video_quality, vbr_max_bitrate);
        }

        libPublisher.SetSmartPublisherEventCallbackV2(publisherHandle, new EventHandeV2());

        // 如果想和时间显示在同一行，请去掉'\n'
        String watermarkText = "大牛直播(daniulive)\n\n";

        String path = logoPath;

        if (watemarkType == 0) {
            if (isWritelogoFileSuccess)
                libPublisher.SmartPublisherSetPictureWatermark(publisherHandle, path,
                        SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_TOPRIGHT, 160,
                        160, 10, 10);

        } else if (watemarkType == 1) {
            if (isWritelogoFileSuccess)
                libPublisher.SmartPublisherSetPictureWatermark(publisherHandle, path,
                        SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_TOPRIGHT, 160,
                        160, 10, 10);

            libPublisher.SmartPublisherSetTextWatermark(publisherHandle, watermarkText, 1,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_FONTSIZE_BIG,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_BOTTOMRIGHT, 10, 10);


        } else if (watemarkType == 2) {
            libPublisher.SmartPublisherSetTextWatermark(publisherHandle, watermarkText, 1,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_FONTSIZE_BIG,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_BOTTOMRIGHT, 10, 10);

            // libPublisher.SmartPublisherSetTextWatermarkFontFileName("/system/fonts/DroidSansFallback.ttf");
        } else {
            Log.i(TAG, "no watermark settings..");
        }
        // end

        if (!is_speex) {
            // set AAC encoder
            libPublisher.SmartPublisherSetAudioCodecType(publisherHandle, 1);
        } else {
            // set Speex encoder
            libPublisher.SmartPublisherSetAudioCodecType(publisherHandle, 2);
            libPublisher.SmartPublisherSetSpeexEncoderQuality(publisherHandle, 8);
        }

        libPublisher.SmartPublisherSetNoiseSuppression(publisherHandle, is_noise_suppression ? 1
                : 0);

        libPublisher.SmartPublisherSetAGC(publisherHandle, is_agc ? 1 : 0);


        libPublisher.SmartPublisherSetSWVideoEncoderProfile(publisherHandle, sw_video_encoder_profile);

        libPublisher.SmartPublisherSetSWVideoEncoderSpeed(publisherHandle, sw_video_encoder_speed);



        libPublisher.SmartPublisherSaveImageFlag(publisherHandle, 1);

        if (libPublisher.SmartPublisherSetPostUserDataQueueMaxSize(publisherHandle, 3, 0) != 0) {
            Log.e(TAG, "Failed to SetPostUserDataQueueMaxSize..");
        }
    }


    class ButtonStartPushListener implements View.OnClickListener {
        public void onClick(View v) {
            if (isPushing) {
                stopPush();

                if (!isRecording) {
                    ConfigControlEnable(true);
                }

                btnStartPush.setText("推送RTMP");
                isPushing = false;

                return;
            }

            Log.i(TAG, "onClick start push..");

            if (libPublisher == null)
                return;

            if (!isRecording) {
                InitAndSetConfig();
            }

            if (inputPushURL != null && inputPushURL.length() > 1) {
                publishURL = inputPushURL;
                Log.i(TAG, "start, input publish url:" + publishURL);
            } else {
                publishURL = baseURL + String.valueOf((int) (System.currentTimeMillis() % 1000000));
                Log.i(TAG, "start, generate random url:" + publishURL);
            }

            if (libPublisher.SmartPublisherSetURL(publisherHandle, publishURL) != 0) {
                Log.e(TAG, "Failed to set publish stream URL..");
            }

            int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandle);
            if (startRet != 0) {
                isPushing = false;

                Log.e(TAG, "Failed to start push stream..");
                return;
            }

            if (!isRecording) {
                if (pushType == 0 || pushType == 1) {
                    CheckInitAudioRecorder();    //enable pure video publisher..
                }


            }

            btnStartPush.setText("停止推送 ");
            isPushing = true;
        }
    };


    //停止rtmp推送
    private void stopPush() {
        if(!isPushing)
        {
            return;
        }
        if (!isRecording) {
            if (audioRecord_ != null) {
                Log.i(TAG, "stopPush, call audioRecord_.StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }
        }

        if (libPublisher != null) {
            libPublisher.SmartPublisherStopPublisher(publisherHandle);
        }

        if (!isRecording) {
            if (publisherHandle != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandle);
                    publisherHandle = 0;
                }
            }
        }
    }

    //停止录像
    private void stopRecorder() {
        if(!isRecording)
        {
            return;
        }
        if (!isPushing) {
            if (audioRecord_ != null) {
                Log.i(TAG, "stopRecorder, call audioRecord_.StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }
        }

        if (libPublisher != null) {
            libPublisher.SmartPublisherStopRecorder(publisherHandle);
        }

        if (!isPushing) {
            if (publisherHandle != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandle);
                    publisherHandle = 0;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "activity destory!");

        if (isPushing || isRecording ) {
            if (audioRecord_ != null) {
                Log.i(TAG, "surfaceDestroyed, call StopRecording..");

                audioRecord_.Stop();

                if (audioRecordCallback_ != null) {
                    audioRecord_.RemoveCallback(audioRecordCallback_);
                    audioRecordCallback_ = null;
                }

                audioRecord_ = null;
            }

            stopPush();
            isPushing = false;

            stopRecorder();
            isRecording = false;


            if (publisherHandle != 0) {
                if (libPublisher != null) {
                    libPublisher.SmartPublisherClose(publisherHandle);
                    publisherHandle = 0;
                }
            }

            libPublisher.UnInitRtspServer();      //如已启用内置服务功能(InitRtspServer)，调用UnInitRtspServer, 注意，即便是启动多个RTSP服务，也只需调用UnInitRtspServer一次
        }

        super.onDestroy();
    }

    private void SetCameraFPS(Camera.Parameters parameters) {
        if (parameters == null)
            return;

        int[] findRange = null;

        int defFPS = 20 * 1000;

        List<int[]> fpsList = parameters.getSupportedPreviewFpsRange();
        if (fpsList != null && fpsList.size() > 0) {
            for (int i = 0; i < fpsList.size(); ++i) {
                int[] range = fpsList.get(i);
                if (range != null
                        && Camera.Parameters.PREVIEW_FPS_MIN_INDEX < range.length
                        && Camera.Parameters.PREVIEW_FPS_MAX_INDEX < range.length) {
                    Log.i(TAG, "Camera index:" + i + " support min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]);

                    Log.i(TAG, "Camera index:" + i + " support max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);

                    if (findRange == null) {
                        if (defFPS <= range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]) {
                            findRange = range;

                            Log.i(TAG, "Camera found appropriate fps, min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                                    + " ,max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                        }
                    }
                }
            }
        }

        if (findRange != null) {
            parameters.setPreviewFpsRange(findRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], findRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
    }

    /*it will call when surfaceChanged*/
    private void initCamera(SurfaceHolder holder) {
        Log.i(TAG, "initCamera..");

        if (mPreviewRunning)
            mCamera.stopPreview();

        Camera.Parameters parameters;
        try {
            parameters = mCamera.getParameters();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }



        parameters.setPreviewSize(videoWidth, videoHeight);
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

        SetCameraFPS(parameters);

        setCameraDisplayOrientation(getActivity(), curCameraIndex, mCamera);

        mCamera.setParameters(parameters);

        int bufferSize = (((videoWidth | 0xf) + 1) * videoHeight * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat())) / 8;

        mCamera.addCallbackBuffer(new byte[bufferSize]);

        mCamera.setPreviewCallbackWithBuffer(this);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (Exception ex) {
            // TODO Auto-generated catch block
            if (null != mCamera) {
                mCamera.release();
                mCamera = null;
            }
            ex.printStackTrace();
        }
        mCamera.startPreview();
        mCamera.autoFocus(myAutoFocusCallback);
        mPreviewRunning = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated..");
        try {

            int CammeraIndex = findBackCamera();
            Log.i(TAG, "BackCamera: " + CammeraIndex);

            if (CammeraIndex == -1) {
                CammeraIndex = findFrontCamera();
                currentCameraType = FRONT;
                imgSwitchCamera.setEnabled(false);
                if (CammeraIndex == -1) {
                    Log.i(TAG, "NO camera!!");
                    return;
                }
            } else {
                currentCameraType = BACK;
            }

            if (mCamera == null) {
                mCamera = openCamera(currentCameraType);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged..");
        initCamera(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        Log.i(TAG, "Surface Destroyed");
    }

    public void onConfigurationChanged(Configuration newConfig) {
        try {
            super.onConfigurationChanged(newConfig);
            Log.i(TAG, "onConfigurationChanged");
            if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (!isPushing && !isRecording) {

                    int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                    if (Surface.ROTATION_270 == rotation) {
                        Log.i(TAG, "onConfigurationChanged rotation=" + rotation + " LANDSCAPE_LEFT_HOME_KEY");

                        currentOrigentation = LANDSCAPE_LEFT_HOME_KEY;
                    } else {
                        Log.i(TAG, "onConfigurationChanged rotation=" + rotation + " LANDSCAPE");

                        currentOrigentation = LANDSCAPE;
                    }
                }
            } else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (!isPushing && !isRecording) {
                    currentOrigentation = PORTRAIT;
                }
            }
        } catch (Exception ex) {
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        frameCount++;
        if (frameCount % 3000 == 0) {
            Log.i("OnPre", "gc+");
            System.gc();
            Log.i("OnPre", "gc-");
        }

        if (data == null) {
            Camera.Parameters params = camera.getParameters();
            Camera.Size size = params.getPreviewSize();
            int bufferSize = (((size.width | 0x1f) + 1) * size.height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
            camera.addCallbackBuffer(new byte[bufferSize]);
        } else {
            if (isPushing || isRecording) {
                libPublisher.SmartPublisherOnCaptureVideoData(publisherHandle, data, data.length, currentCameraType, currentOrigentation);
            }

            camera.addCallbackBuffer(data);
        }
    }

    @SuppressLint("NewApi")
    private Camera openCamera(int type) {
        int frontIndex = -1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Log.i(TAG, "cameraCount: " + cameraCount);

        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontIndex = cameraIndex;
            } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                backIndex = cameraIndex;
            }
        }

        currentCameraType = type;
        if (type == FRONT && frontIndex != -1) {
            curCameraIndex = frontIndex;
            return Camera.open(frontIndex);
        } else if (type == BACK && backIndex != -1) {
            curCameraIndex = backIndex;
            return Camera.open(backIndex);
        }
        return null;
    }

    private void switchCamera() throws IOException {
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();

        if (currentCameraType == FRONT) {
            mCamera = openCamera(BACK);
        } else if (currentCameraType == BACK) {
            mCamera = openCamera(FRONT);
        }
        initCamera(mSurfaceHolder);
    }

    //Check if it has front camera
    private int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return -1;
    }

    //Check if it has back camera
    private int findBackCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return -1;
    }

    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        Log.i(TAG, "curDegree: " + result);

        camera.setDisplayOrientation(result);
    }

    //设置H.264/H.265硬编码码率(按照25帧计算)
    private int setHardwareEncoderKbps(boolean isH264, int width, int height)
    {
        int kbit_rate = 2000;
        int area = width * height;

        if (area <= (320 * 300)) {
            kbit_rate = isH264?350:280;
        } else if (area <= (370 * 320)) {
            kbit_rate = isH264?470:400;
        } else if (area <= (640 * 360)) {
            kbit_rate = isH264?850:650;
        } else if (area <= (640 * 480)) {
            kbit_rate = isH264?1000:800;
        } else if (area <= (800 * 600)) {
            kbit_rate = isH264?1050:950;
        } else if (area <= (900 * 700)) {
            kbit_rate = isH264?1450:1100;
        } else if (area <= (1280 * 720)) {
            kbit_rate = isH264?2000:1500;
        } else if (area <= (1366 * 768)) {
            kbit_rate = isH264?2200:1900;
        } else if (area <= (1600 * 900)) {
            kbit_rate = isH264?2700:2300;
        } else if (area <= (1600 * 1050)) {
            kbit_rate =isH264?3000:2500;
        } else if (area <= (1920 * 1080)) {
            kbit_rate = isH264?4500:2800;
        } else {
            kbit_rate = isH264?4000:3000;
        }
        return kbit_rate;
    }

    private int CalVideoQuality(int w, int h, boolean is_h264)
    {
        int area = w*h;

        int quality = is_h264 ? 23 : 28;

        if ( area <= (320 * 240) )
        {
            quality = is_h264? 23 : 27;
        }
        else if ( area <= (640 * 360) )
        {
            quality = is_h264? 25 : 28;
        }
        else if ( area <= (640 * 480) )
        {
            quality = is_h264? 26 : 28;
        }
        else if ( area <= (960 * 600) )
        {
            quality = is_h264? 26 : 28;
        }
        else if ( area <= (1280 * 720) )
        {
            quality = is_h264? 27 : 29;
        }
        else if ( area <= (1600 * 900) )
        {
            quality = is_h264 ? 28 : 30;
        }
        else if ( area <= (1920 * 1080) )
        {
            quality = is_h264 ? 29 : 31;
        }
        else
        {
            quality = is_h264 ? 30 : 32;
        }

        return quality;
    }

    private int CalVbrMaxKBitRate(int w, int h)
    {
        int max_kbit_rate = 2000;

        int area = w*h;

        if (area <= (320 * 300))
        {
            max_kbit_rate = 320;
        }
        else if (area <= (360 * 320))
        {
            max_kbit_rate = 400;
        }
        else if (area <= (640 * 360))
        {
            max_kbit_rate = 600;
        }
        else if (area <= (640 * 480))
        {
            max_kbit_rate = 700;
        }
        else if (area <= (800 * 600))
        {
            max_kbit_rate = 800;
        }
        else if (area <= (900 * 700))
        {
            max_kbit_rate = 1000;
        }
        else if (area <= (1280 * 720))
        {
            max_kbit_rate = 1400;
        }
        else if (area <= (1366 * 768))
        {
            max_kbit_rate = 1700;
        }
        else if (area <= (1600 * 900))
        {
            max_kbit_rate = 2400;
        }
        else if (area <= (1600 * 1050))
        {
            max_kbit_rate = 2600;
        }
        else if (area <= (1920 * 1080))
        {
            max_kbit_rate = 2900;
        }
        else
        {
            max_kbit_rate = 3500;
        }

        return max_kbit_rate;
    }

    /**
     * 根据目录创建文件夹
     *
     * @param context
     * @param cacheDir
     * @return
     */
    public static File getOwnCacheDirectory(Context context, String cacheDir) {
        File appCacheDir = null;
        //判断sd卡正常挂载并且拥有权限的时候创建文件
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && hasExternalStoragePermission(context)) {
            appCacheDir = new File(Environment.getExternalStorageDirectory(), cacheDir);
            Log.i(TAG, "appCacheDir: " + appCacheDir);
        }
        if (appCacheDir == null || !appCacheDir.exists() && !appCacheDir.mkdirs()) {
            appCacheDir = context.getCacheDir();
        }
        return appCacheDir;
    }

    /**
     * 检查是否有权限
     *
     * @param context
     * @return
     */
    private static boolean hasExternalStoragePermission(Context context) {
        int perm = context.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        return perm == 0;
    }
}
