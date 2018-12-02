package com.example.android.tflitecamerademo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
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
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link LiveRecordFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LiveRecordFragment extends Fragment implements SurfaceHolder.Callback{

    private static final String TAG = "LiveRecordFragment";
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private Button captureButton;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;

    private boolean isRecording = false;

    public LiveRecordFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LiveRecordFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static LiveRecordFragment newInstance() {
        LiveRecordFragment fragment = new LiveRecordFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_live_record, container, false);
        mSurfaceView = (SurfaceView)view.findViewById(R.id.surface);
        captureButton = (Button)view.findViewById(R.id.btn_record);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordVedio();
            }
        });
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        //autoObtainCameraPermission();

        // openCamera();
        mCamera = getCameraInstance();
        setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseMediaRecorder(); // 如果正在使用MediaRecorder，先释放
        releaseCamera(); // 释放Camera资源
    }


    // 检查手机是否具有摄像头
    private boolean checkCameraHardware(Context context){
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // 设备具有摄像头
            return true;
        }else {
            // 设备没有摄像头
            return false;
        }
    }

    // 获得Camera实例
    public static Camera getCameraInstance(){
        Camera camera = null;
        try{
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // 尝试获取Camera
        }catch (Exception e){
            // 如果Camera正在被使用，抛出异常
            Log.e(TAG, "failed to get camera" );
        }

        return camera;
    }

    // 准备视频录制,必须按照以下顺序
    private boolean prepareVedioRecorder(){
        mCamera = getCameraInstance();
        mMediaRecorder = new MediaRecorder();

        // 步骤1: unlock Camera 和为MediaRecorder设置camera
        setCameraDisplayOrientation(getActivity(),Camera.CameraInfo.CAMERA_FACING_BACK,mCamera);
        //mCamera.autoFocus(null);
        mCamera.unlock(); // Android 4.0后可以不调用
        mMediaRecorder.setCamera(mCamera);

        // 步骤2: 设置资源
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Android 2.2(API 8)之前使用以下代码设置
//        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);


        // 步骤3: 设置CamcorderProfile（需要API 8及以上）
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // 步骤4: 设置输出文件
        mMediaRecorder.setOutputFile(getOutputMediaFile().toString());

        // 步骤5: 设置预览输出
        mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());


        // 步骤6: 准备配置MediaRecorder
        try{
            mMediaRecorder.prepare();
        }catch (IllegalStateException e){
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }catch (IOException e){
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    // 录制视频
    private void RecordVedio(){
        if (isRecording) {
            // 停止录制并释放camera
            mMediaRecorder.stop();
            releaseMediaRecorder();
            mCamera.lock();

            isRecording = false;
            captureButton.setText("录制");
        }else {
            // 初始化camera
            if(prepareVedioRecorder()){
                // Camera可以使用并unlocked， MediaRecorder已经准备好，
                mMediaRecorder.start();

                isRecording = true;
                captureButton.setText("结束");
            }else {
                // MediaRecorder没有准备好，释放MediaRecorder
                releaseMediaRecorder();
                Log.d(TAG, "MediaRecorder not prepared");
            }
        }
    }

    // 释放MediaRecorder资源
    private void releaseMediaRecorder(){
        if (mMediaRecorder != null){
            mMediaRecorder.reset(); // 清除recorder配置
            mMediaRecorder.release(); // 释放recorder对象
            mMediaRecorder = null;
            mCamera.lock();
        }
    }

    // 释放Camera资源
    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();
            mCamera = null;
        }
    }

    // 为保存图片或视频创建一个文件
    private static File getOutputMediaFile(){
        // 为了安全，应该检查是否具有SD卡
        // 在此之前，使用Environment.getExternalStorageState()

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"vslrlive");

        // 如果不存在，创建储存目录
        if (!mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // 创建media文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");

        return mediaFile;
    }

    // 设置预览旋转
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    // 获取相机等权限
    private void autoObtainCameraPermission() {
        Log.i("test","自动获取相机权限");

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
                //ToastUtils.showShort(this, "您已经拒绝过一次");
            }
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            if (!checkCameraHardware(getActivity())) {
                Toast.makeText(getActivity(), "相机不支持", Toast.LENGTH_SHORT)
                        .show();
            } else {
                // openCamera();
                mCamera = getCameraInstance();

                captureButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        RecordVedio();
                    }
                });
                setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);

            }
        }
    }

    // 获取权限结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode){
            case 1:
                // 检查是否有相机
                if (!checkCameraHardware(getActivity())) {
                    Toast.makeText(getActivity(), "相机不支持", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    //openCamera();
                    mCamera = getCameraInstance();

                    captureButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            RecordVedio();
                        }
                    });
                    setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
                }
                break;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mHolder = holder;
        if (mCamera == null) {
            return;
        }
        try {
            //设置显示预览
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            releaseCamera();
            getActivity().finish();
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mHolder = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (isRecording && mCamera != null) {
            mCamera.lock();
        }
        mSurfaceView = null;
        mHolder = null;
        releaseMediaRecorder();
        releaseCamera();

    }
}
