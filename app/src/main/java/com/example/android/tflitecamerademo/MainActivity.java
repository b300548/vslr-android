package com.example.android.tflitecamerademo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "MainActivity";

    private FrameLayout container;
    private Button playButton;
    private Button recordButton;
    private LivePlayFragment mLivePlayFragment;
    private LiveRecordFragment mLiveRecordFragment;

    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;
    private String videoUrl = "https://key003.ku6.com/movie/1af61f05352547bc8468a40ba2d29a1d.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        mLivePlayFragment = LivePlayFragment.newInstance();
        mLiveRecordFragment = LiveRecordFragment.newInstance();

        container = (FrameLayout)findViewById(R.id.container);
        playButton = (Button)findViewById(R.id.btn_playlive);
        recordButton = (Button)findViewById(R.id.btn_recordlive);
        playButton.setOnClickListener(this);
        recordButton.setOnClickListener(this);
        autoObtainCameraPermission();
        replaceFragment(mLivePlayFragment);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_playlive:
                replaceFragment(mLivePlayFragment);
                break;
            case R.id.btn_recordlive:
                replaceFragment(mLiveRecordFragment);
                break;
            default:
                break;
        }
    }

    private void replaceFragment(Fragment fragment){
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container,fragment);
        transaction.commit();
    }

    // 获取相机等权限
    private void autoObtainCameraPermission() {
        Log.i("test","自动获取相机权限");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED|| ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                //ToastUtils.showShort(this, "您已经拒绝过一次");
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            if (!checkCameraHardware(this)) {
                Toast.makeText(this, "相机不支持", Toast.LENGTH_SHORT)
                        .show();
            } else {

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case 1:
                // 检查是否有相机
                if (!checkCameraHardware(this)) {
                    Toast.makeText(this, "相机不支持", Toast.LENGTH_SHORT)
                            .show();
                } else {

                }
                break;
        }
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
}
