package com.example.matissedemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.lxj.matisse.CaptureMode;
import com.lxj.matisse.Matisse;
import com.lxj.matisse.MimeType;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Matisse.from(this)
                .choose(MimeType.ofAll()) //显示所有文件类型，比如图片和视频，
                .capture(true, CaptureMode.All)//是否显示拍摄按钮，默认不显示
                .isCrop(true)
                .maxSelectable(9) //默认9张
                .maxSelectablePerMediaType(9,1)
                .forResult(1); //请求码
    }
}
